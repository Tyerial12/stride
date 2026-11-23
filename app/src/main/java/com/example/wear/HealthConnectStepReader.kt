package com.example.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.data.local.SolstridePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HealthConnectState {
    UNAVAILABLE,
    UPDATE_REQUIRED,
    PERMISSION_REQUIRED,
    CONNECTED
}

data class WatchSleepSnapshot(
    val isSleepingNow: Boolean = false,
    val activeSleepMinutes: Long = 0L,
    val activeSessionMarkerMs: Long = 0L,
    val sleepSourceLabel: String = "",
    val latestCompletedEndMs: Long = 0L,
    val latestCompletedDurationMinutes: Long = 0L,
    val sleepPermissionGranted: Boolean = false,
    val hasWatchSleepDetected: Boolean = false
)

data class HealthConnectSyncStatus(
    val state: HealthConnectState = HealthConnectState.UNAVAILABLE,
    val primaryWatchSource: String = "Health Connect",
    val watchStepsToday: Long = 0L,
    val samsungHealthInstalled: Boolean = false,
    val samsungHealthLinked: Boolean = false,
    val samsungHealthStepsToday: Long = 0L,
    val hasWatchDeviceRecordToday: Boolean = false,
    val phoneStepsIgnoredToday: Long = 0L,
    val backgroundReadSupported: Boolean = false,
    val backgroundReadGranted: Boolean = false,
    val sleepPermissionGranted: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val lastError: String? = null
)

/**
 * Full-compatibility Android Health Connect & Samsung Health client for Solstride.
 *
 * - First-class Samsung Health (`com.sec.android.app.shealth`) compatibility using Health Connect's
 *   deduplicated `aggregate(StepsRecord.COUNT_TOTAL)` filtered to Samsung Health's `DataOrigin`,
 *   ensuring exact parity with Samsung Health / Galaxy Watch daily totals even when One UI writes
 *   merged records tagged as `Device.TYPE_PHONE` or overlapping watch/phone segments.
 * - Also supports Pixel Watch / Fitbit, Garmin Connect, Google Fit, Amazfit / Zepp, Oura, Polar,
 *   Suunto, Withings, and Wear OS.
 * - Supports optional Health Connect background read (`READ_HEALTH_DATA_IN_BACKGROUND`) when
 *   available on the device, while functioning 100% with foreground `READ_STEPS` + 24h catch-up.
 * - Uses persisted watermarks in SolstridePreferences so live step increments sync cleanly every
 *   5 seconds without double-counting.
 */
class HealthConnectStepReader(
    context: Context,
    private val preferences: SolstridePreferences
) {
    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(
        HealthConnectSyncStatus(samsungHealthInstalled = isSamsungHealthInstalled())
    )
    val status: StateFlow<HealthConnectSyncStatus> = _status.asStateFlow()

    fun isSamsungHealthInstalled(): Boolean {
        return runCatching {
            val pm = appContext.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    SAMSUNG_HEALTH_PACKAGE,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(SAMSUNG_HEALTH_PACKAGE, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun buildOpenSamsungHealthIntent(): Intent {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH_PACKAGE)
        if (launchIntent != null) {
            return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$SAMSUNG_HEALTH_PACKAGE")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun coreStepPermissions(): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setOf(HealthPermission.getReadPermission(StepsRecord::class))
        } else {
            emptySet()
        }
    }

    fun requestablePermissions(): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptySet()
        val perms = linkedSetOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
        if (_status.value.backgroundReadSupported) {
            perms.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        }
        return perms
    }

    fun backgroundOnlyPermission(): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptySet()
        return setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
    }

    suspend fun refreshStatus(): HealthConnectSyncStatus {
        val samsungInstalled = isSamsungHealthInstalled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val unsupported = HealthConnectSyncStatus(
                state = HealthConnectState.UNAVAILABLE,
                primaryWatchSource = "Requires Android 8.0+",
                samsungHealthInstalled = samsungInstalled
            )
            _status.value = unsupported
            return unsupported
        }
        return refreshStatusApi26(samsungInstalled)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun refreshStatusApi26(
        samsungInstalled: Boolean = isSamsungHealthInstalled()
    ): HealthConnectSyncStatus {
        return try {
            when (HealthConnectClient.getSdkStatus(appContext, PROVIDER_PACKAGE_NAME)) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    val s = _status.value.copy(
                        state = HealthConnectState.UNAVAILABLE,
                        samsungHealthInstalled = samsungInstalled,
                        backgroundReadSupported = false,
                        backgroundReadGranted = false,
                        lastError = null
                    )
                    _status.value = s
                    s
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    val s = _status.value.copy(
                        state = HealthConnectState.UPDATE_REQUIRED,
                        samsungHealthInstalled = samsungInstalled,
                        backgroundReadSupported = false,
                        backgroundReadGranted = false,
                        lastError = null
                    )
                    _status.value = s
                    s
                }
                else -> {
                    val client = HealthConnectClient.getOrCreate(appContext)
                    val granted = client.permissionController.getGrantedPermissions()
                    val coreRead = HealthPermission.getReadPermission(StepsRecord::class)
                    val sleepRead = HealthPermission.getReadPermission(SleepSessionRecord::class)
                    val bgSupported = runCatching {
                        client.features.getFeatureStatus(
                            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
                        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
                    }.getOrDefault(false)
                    val bgGranted = bgSupported &&
                        granted.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)

                    val nextState = if (granted.contains(coreRead)) {
                        HealthConnectState.CONNECTED
                    } else {
                        HealthConnectState.PERMISSION_REQUIRED
                    }
                    val s = _status.value.copy(
                        state = nextState,
                        samsungHealthInstalled = samsungInstalled,
                        backgroundReadSupported = bgSupported,
                        backgroundReadGranted = bgGranted,
                        sleepPermissionGranted = granted.contains(sleepRead),
                        lastError = null
                    )
                    _status.value = s
                    s
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect status check failed: ${e.message}")
            val s = _status.value.copy(
                state = HealthConnectState.UNAVAILABLE,
                samsungHealthInstalled = samsungInstalled,
                lastError = e.localizedMessage
            )
            _status.value = s
            s
        }
    }

    /**
     * Pulls newly added or incremented StepsRecord deltas from Health Connect (including
     * deduplicated Samsung Health daily aggregates) over the last 24 hours.
     *
     * @param includeAllSources When true, reads all Health Connect step sources (watch + Samsung Health + fitness apps).
     *                          When false, skips phone-only records while Solstride's built-in phone pedometer is active,
     *                          while ALWAYS accepting Samsung Health when a Galaxy Watch is connected or watch records exist.
     * @param watchConnected    True if a paired watch (e.g. Galaxy Watch / Wear OS) is currently connected.
     */
    suspend fun pullNewWatchSteps(
        includeAllSources: Boolean = false,
        watchConnected: Boolean = false
    ): List<WatchStepPayload> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        return pullNewWatchStepsApi26(
            includeAllSources = includeAllSources,
            watchConnected = watchConnected
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pullNewWatchStepsApi26(
        includeAllSources: Boolean,
        watchConnected: Boolean
    ): List<WatchStepPayload> {
        val samsungInstalled = isSamsungHealthInstalled()
        val currentStatus = refreshStatusApi26(samsungInstalled)
        if (currentStatus.state != HealthConnectState.CONNECTED) {
            return emptyList()
        }

        val nowMs = System.currentTimeMillis()
        val nowInstant = Instant.ofEpochMilli(nowMs)
        val lookbackStart = nowInstant.minusSeconds(LOOKBACK_SECONDS)
        val zone = ZoneId.systemDefault()
        val todayDate = LocalDate.now(zone)
        val todayStartInstant = todayDate.atStartOfDay(zone).toInstant()
        val yesterdayDate = todayDate.minusDays(1)
        val yesterdayStartInstant = yesterdayDate.atStartOfDay(zone).toInstant()

        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val allRecords = mutableListOf<StepsRecord>()
            var pageToken: String? = null
            var pages = 0

            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(lookbackStart, nowInstant),
                        pageToken = pageToken
                    )
                )
                allRecords.addAll(response.records)
                // Per Android Health Connect docs, pageToken may be "" instead of null on Android 12/13
                pageToken = response.pageToken
                pages += 1
            } while (!pageToken.isNullOrEmpty() && pages < MAX_PAGES)

            // Deduplicated Samsung Health daily total via Health Connect aggregate()
            val samsungAggregateToday = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(todayStartInstant, nowInstant),
                        dataOriginFilter = setOf(DataOrigin(SAMSUNG_HEALTH_PACKAGE))
                    )
                )[StepsRecord.COUNT_TOTAL] ?: 0L
            }.getOrDefault(0L)

            val samsungRecords = allRecords.filter { isSamsungHealthOrigin(it.metadata.dataOrigin.packageName) }
            val hasSamsungRecords = samsungRecords.isNotEmpty() || samsungAggregateToday > 0L
            val hasSamsungWatchRecord = samsungRecords.any { rec ->
                val dtype = rec.metadata.device?.type
                dtype == Device.TYPE_WATCH || dtype == Device.TYPE_FITNESS_BAND || dtype == Device.TYPE_RING
            }
            val rawSamsungTodayFallback = samsungRecords
                .filter { !it.endTime.isBefore(todayStartInstant) }
                .sumOf { it.count }
            val samsungStepsToday = if (samsungAggregateToday > 0L) samsungAggregateToday else rawSamsungTodayFallback

            val watermarks = decodeWatermarks(preferences.readHealthConnectWatermarksRaw()).toMutableMap()
            var watermarksChanged = false
            val payloads = mutableListOf<WatchStepPayload>()

            var watchStepsToday = 0L
            var phoneStepsIgnoredToday = 0L
            val detectedSources = linkedSetOf<String>()

            // 1. Handle Samsung Health via deduplicated aggregate ONLY when a Galaxy Watch is actually connected
            //    or a watch record was written by Samsung Health (or user explicitly opted into All Health Connect Sources).
            //    Notice: we do NOT treat "includeAllSources" alone as a connected watch, nor do we let phone-only
            //    Samsung Health records fool Solstride into thinking a watch is connected!
            val shouldAcceptSamsungHealth = hasSamsungRecords && (
                watchConnected ||
                    hasSamsungWatchRecord ||
                    includeAllSources
                )

            if (hasSamsungRecords) {
                if (shouldAcceptSamsungHealth) {
                    val label = if (watchConnected || hasSamsungWatchRecord) {
                        "Samsung Health (Galaxy Watch)"
                    } else {
                        "Samsung Health (Phone/App)"
                    }
                    detectedSources.add(label)
                    watchStepsToday += samsungStepsToday

                    // Catch any late-synced steps from yesterday right after midnight if yesterday was tracked
                    val yesterdayKey = "shealth_$yesterdayDate"
                    val prevYesterday = watermarks[yesterdayKey]
                    if (prevYesterday != null) {
                        val samsungAggregateYesterday = runCatching {
                            client.aggregate(
                                AggregateRequest(
                                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                                    timeRangeFilter = TimeRangeFilter.between(yesterdayStartInstant, todayStartInstant),
                                    dataOriginFilter = setOf(DataOrigin(SAMSUNG_HEALTH_PACKAGE))
                                )
                            )[StepsRecord.COUNT_TOTAL] ?: 0L
                        }.getOrDefault(0L)
                        val yDelta = (samsungAggregateYesterday - prevYesterday.syncedCount).coerceAtLeast(0L)
                        if (yDelta > 0L) {
                            watermarks[yesterdayKey] = WatermarkEntry(
                                syncedCount = samsungAggregateYesterday,
                                endMs = todayStartInstant.toEpochMilli()
                            )
                            watermarksChanged = true
                            payloads.add(
                                WatchStepPayload(
                                    batchId = "hc_${yesterdayKey}_$samsungAggregateYesterday",
                                    stepDelta = yDelta.coerceAtMost(MAX_SINGLE_RECORD_DELTA),
                                    recordedAtMs = nowMs
                                )
                            )
                        }
                    }

                    // Today's deduplicated Samsung Health delta
                    val todayKey = "shealth_$todayDate"
                    val prevToday = watermarks[todayKey]?.syncedCount ?: 0L
                    val todayDelta = (samsungStepsToday - prevToday).coerceAtLeast(0L)
                    if (todayDelta > 0L) {
                        watermarks[todayKey] = WatermarkEntry(syncedCount = samsungStepsToday, endMs = nowMs)
                        watermarksChanged = true
                        payloads.add(
                            WatchStepPayload(
                                batchId = "hc_${todayKey}_$samsungStepsToday",
                                stepDelta = todayDelta.coerceAtMost(MAX_SINGLE_RECORD_DELTA),
                                recordedAtMs = nowMs
                            )
                        )
                    } else if (samsungStepsToday > 0L && watermarks[todayKey] == null) {
                        watermarks[todayKey] = WatermarkEntry(syncedCount = samsungStepsToday, endMs = nowMs)
                        watermarksChanged = true
                    }
                } else {
                    // Phone-only Samsung Health while no watch is connected & Solstride's phone pedometer is active
                    phoneStepsIgnoredToday += samsungStepsToday
                }
            }

            var hasOtherWatchDeviceRecordToday = false

            // 2. Process all other Health Connect sources (Fitbit, Garmin, Google Fit, Wear OS, Zepp, Oura, etc.)
            for (record in allRecords) {
                val count = record.count
                if (count <= 0L) continue

                val originPkg = record.metadata.dataOrigin.packageName
                if (originPkg == appContext.packageName) continue
                // Samsung Health is already handled via deduplicated aggregate above
                if (isSamsungHealthOrigin(originPkg)) continue

                val deviceType = record.metadata.device?.type
                val isToday = !record.endTime.isBefore(todayStartInstant)
                val isWearableDevice = deviceType == Device.TYPE_WATCH ||
                    deviceType == Device.TYPE_FITNESS_BAND ||
                    deviceType == Device.TYPE_RING

                if (!includeAllSources && isPhoneOnlyRecord(originPkg, deviceType)) {
                    if (isToday) phoneStepsIgnoredToday += count
                    continue
                }

                if (isToday && isWearableDevice) {
                    hasOtherWatchDeviceRecordToday = true
                }

                val friendlySource = mapOriginToSourceLabel(originPkg, deviceType)
                detectedSources.add(friendlySource)
                if (isToday) {
                    watchStepsToday += count
                }

                val recordId = record.metadata.id.ifBlank {
                    "${originPkg}_${record.startTime.toEpochMilli()}_${record.endTime.toEpochMilli()}"
                }
                val endMs = record.endTime.toEpochMilli()
                val prevEntry = watermarks[recordId]
                val prevCount = prevEntry?.syncedCount ?: 0L
                val delta = (count - prevCount).coerceAtLeast(0L)

                if (delta > 0L) {
                    watermarks[recordId] = WatermarkEntry(syncedCount = count, endMs = endMs)
                    watermarksChanged = true
                    payloads.add(
                        WatchStepPayload(
                            batchId = "hc_${recordId}_$count",
                            stepDelta = delta.coerceAtMost(MAX_SINGLE_RECORD_DELTA),
                            recordedAtMs = endMs
                        )
                    )
                } else if (prevEntry != null && prevEntry.endMs != endMs) {
                    watermarks[recordId] = prevEntry.copy(endMs = endMs)
                    watermarksChanged = true
                }
            }

            if (watermarksChanged) {
                val prunedCutoff = nowMs - WATERMARK_RETENTION_MS
                val pruned = watermarks.entries
                    .filter { it.value.endMs >= prunedCutoff }
                    .sortedByDescending { it.value.endMs }
                    .take(MAX_WATERMARK_ENTRIES)
                    .associate { it.key to it.value }
                preferences.writeHealthConnectWatermarksRaw(encodeWatermarks(pruned))
            }

            val primaryLabel = detectedSources.firstOrNull()
                ?: if (samsungInstalled) "Health Connect (No Watch Detected)" else "Health Connect (Ready)"

            _status.value = currentStatus.copy(
                state = HealthConnectState.CONNECTED,
                primaryWatchSource = primaryLabel,
                watchStepsToday = watchStepsToday,
                samsungHealthInstalled = samsungInstalled,
                samsungHealthLinked = hasSamsungRecords,
                samsungHealthStepsToday = samsungStepsToday,
                hasWatchDeviceRecordToday = hasSamsungWatchRecord || hasOtherWatchDeviceRecordToday,
                phoneStepsIgnoredToday = phoneStepsIgnoredToday,
                lastSyncedAtMs = nowMs,
                lastError = null
            )

            payloads
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.w(TAG, "Health Connect read blocked (permission or background restriction): ${e.message}")
            _status.value = _status.value.copy(
                samsungHealthInstalled = samsungInstalled,
                lastSyncedAtMs = nowMs,
                lastError = "Health Connect permission check needed"
            )
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect readRecords failed: ${e.message}")
            _status.value = _status.value.copy(
                samsungHealthInstalled = samsungInstalled,
                lastSyncedAtMs = nowMs,
                lastError = e.localizedMessage
            )
            emptyList()
        }
    }

    private fun isSamsungHealthOrigin(originPackage: String): Boolean {
        val pkg = originPackage.lowercase()
        return pkg == SAMSUNG_HEALTH_PACKAGE || pkg.contains("shealth")
    }

    /**
     * Excludes phone-only step records so Health Connect never double-counts Solstride's
     * built-in phone pedometer. Accepts watch/band/ring devices and wearable companion apps
     * (like Fitbit, Garmin, Wear OS) even when OEM writes Device.TYPE_UNKNOWN.
     */
    private fun isPhoneOnlyRecord(originPackage: String, deviceType: Int?): Boolean {
        if (originPackage == appContext.packageName) return true
        if (deviceType == Device.TYPE_PHONE) return true
        if (originPackage == "android" && deviceType != Device.TYPE_WATCH && deviceType != Device.TYPE_FITNESS_BAND) {
            return true
        }
        return false
    }

    private fun mapOriginToSourceLabel(originPackage: String, deviceType: Int?): String {
        val pkg = originPackage.lowercase()
        return when {
            pkg.contains("shealth") || pkg.contains("samsung") -> "Samsung Health (Galaxy Watch)"
            pkg.contains("fitbit") -> "Fitbit / Pixel Watch"
            pkg.contains("garmin") -> "Garmin Connect"
            pkg.contains("wearable") || pkg.contains("wearos") -> "Wear OS Watch"
            pkg.contains("apps.fitness") -> "Google Fit"
            pkg.contains("zepp") || pkg.contains("huami") -> "Zepp / Amazfit"
            pkg.contains("oura") -> "Oura Ring"
            pkg.contains("polar") -> "Polar Flow"
            pkg.contains("suunto") -> "Suunto Watch"
            pkg.contains("withings") -> "Withings Watch"
            pkg == "android" -> "Android Health Connect"
            deviceType == Device.TYPE_WATCH -> "Smart Watch"
            deviceType == Device.TYPE_FITNESS_BAND -> "Fitness Band"
            deviceType == Device.TYPE_RING -> "Smart Ring"
            else -> "Health Connect"
        }
    }

    fun buildInstallOrUpdateIntent(): Intent {
        val uri = Uri.parse(
            "market://details?id=$PROVIDER_PACKAGE_NAME&url=healthconnect%3A%2F%2Fonboarding"
        )
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.vending")
            putExtra("overlay", true)
            putExtra("callerId", appContext.packageName)
        }
    }

    fun buildHealthConnectSettingsIntent(): Intent {
        return Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    }

    private data class WatermarkEntry(
        val syncedCount: Long,
        val endMs: Long
    )

    private fun decodeWatermarks(raw: String): Map<String, WatermarkEntry> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, WatermarkEntry>()
        raw.split(';').forEach { token ->
            val parts = token.split('|')
            if (parts.size == 3) {
                val id = parts[0]
                val count = parts[1].toLongOrNull()
                val endMs = parts[2].toLongOrNull()
                if (id.isNotBlank() && count != null && endMs != null) {
                    result[id] = WatermarkEntry(count, endMs)
                }
            }
        }
        return result
    }

    private fun encodeWatermarks(map: Map<String, WatermarkEntry>): String {
        return map.entries.joinToString(";") { (id, entry) ->
            val safeId = id.replace("|", "_").replace(";", "_")
            "$safeId|${entry.syncedCount}|${entry.endMs}"
        }
    }

    /**
     * Reads real smartwatch sleep sessions from Health Connect (SleepSessionRecord) and combines
     * them with any live Wear OS watch sleep status signal so the Hero automatically enters
     * Sleeping Mode when real-life watch sleep is detected and wakes up Well-Rested afterward.
     *
     * When [watchConnected] is false (watch disconnected), active sleep and live watch sleep
     * detection are immediately cleared so the UI and Dual-Pool Stamina economy switch to
     * step-fueled Stamina mode right away.
     */
    suspend fun readWatchSleepSnapshot(watchConnected: Boolean = true): WatchSleepSnapshot {
        val (liveWearSleeping, liveWearSinceMs, liveWearSource) = preferences.readWatchLiveSleepTriple()
        if (!watchConnected && liveWearSleeping) {
            preferences.setWatchLiveSleeping(false)
        }
        val nowMs = System.currentTimeMillis()
        val liveWearActive = watchConnected &&
            liveWearSleeping &&
            liveWearSinceMs > 0L &&
            (nowMs - liveWearSinceMs) < 16L * 3600_000L

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return WatchSleepSnapshot(
                isSleepingNow = liveWearActive,
                activeSleepMinutes = if (liveWearActive) ((nowMs - liveWearSinceMs) / 60_000L).coerceAtLeast(1L) else 0L,
                activeSessionMarkerMs = if (liveWearActive) liveWearSinceMs else 0L,
                sleepSourceLabel = if (liveWearActive) liveWearSource else "",
                sleepPermissionGranted = false,
                hasWatchSleepDetected = liveWearActive
            )
        }
        return readWatchSleepSnapshotApi26(
            watchConnected = watchConnected,
            liveWearActive = liveWearActive,
            liveWearSinceMs = liveWearSinceMs,
            liveWearSource = liveWearSource,
            nowMs = nowMs
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun readWatchSleepSnapshotApi26(
        watchConnected: Boolean,
        liveWearActive: Boolean,
        liveWearSinceMs: Long,
        liveWearSource: String,
        nowMs: Long
    ): WatchSleepSnapshot {
        return try {
            val sdkStatus = HealthConnectClient.getSdkStatus(appContext, PROVIDER_PACKAGE_NAME)
            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                return WatchSleepSnapshot(
                    isSleepingNow = liveWearActive,
                    activeSleepMinutes = if (liveWearActive) ((nowMs - liveWearSinceMs) / 60_000L).coerceAtLeast(1L) else 0L,
                    activeSessionMarkerMs = if (liveWearActive) liveWearSinceMs else 0L,
                    sleepSourceLabel = if (liveWearActive) liveWearSource else "",
                    sleepPermissionGranted = false,
                    hasWatchSleepDetected = liveWearActive
                )
            }
            val client = HealthConnectClient.getOrCreate(appContext)
            val granted = client.permissionController.getGrantedPermissions()
            val sleepPerm = HealthPermission.getReadPermission(SleepSessionRecord::class)
            val hasSleepPerm = granted.contains(sleepPerm)
            _status.value = _status.value.copy(sleepPermissionGranted = hasSleepPerm)

            if (!hasSleepPerm) {
                return WatchSleepSnapshot(
                    isSleepingNow = liveWearActive,
                    activeSleepMinutes = if (liveWearActive) ((nowMs - liveWearSinceMs) / 60_000L).coerceAtLeast(1L) else 0L,
                    activeSessionMarkerMs = if (liveWearActive) liveWearSinceMs else 0L,
                    sleepSourceLabel = if (liveWearActive) liveWearSource else "",
                    sleepPermissionGranted = false,
                    hasWatchSleepDetected = liveWearActive
                )
            }

            val endInstant = Instant.now()
            val startInstant = endInstant.minusSeconds(SLEEP_LOOKBACK_SECONDS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
                )
            )
            // Prefer watch/wearable or Samsung Health sleep sessions; exclude purely self-origin records
            val watchSessions = response.records
                .filter { rec ->
                    val pkg = rec.metadata.dataOrigin.packageName
                    val devType = rec.metadata.device?.type
                    !isPhoneOnlyRecord(pkg, devType) || isSamsungHealthOrigin(pkg)
                }
                .sortedByDescending { it.endTime.toEpochMilli() }

            val latest = watchSessions.firstOrNull()
            if (latest == null) {
                return WatchSleepSnapshot(
                    isSleepingNow = liveWearActive,
                    activeSleepMinutes = if (liveWearActive) ((nowMs - liveWearSinceMs) / 60_000L).coerceAtLeast(1L) else 0L,
                    activeSessionMarkerMs = if (liveWearActive) liveWearSinceMs else 0L,
                    sleepSourceLabel = if (liveWearActive) liveWearSource else "",
                    sleepPermissionGranted = true,
                    hasWatchSleepDetected = liveWearActive
                )
            }

            val sessionStartMs = latest.startTime.toEpochMilli()
            val sessionEndMs = latest.endTime.toEpochMilli()
            val durationMin = ((sessionEndMs - sessionStartMs) / 60_000L).coerceAtLeast(1L)
            val sourceLabel = mapOriginToSourceLabel(
                latest.metadata.dataOrigin.packageName,
                latest.metadata.device?.type
            )

            // Active ONLY if the watch is currently connected AND Health Connect reports a sleep session covering "now"
            val hcActiveNow = watchConnected && sessionEndMs >= (nowMs - 12L * 60_000L)
            val isSleeping = watchConnected && (liveWearActive || hcActiveNow)
            val activeDuration = when {
                liveWearActive -> ((nowMs - liveWearSinceMs) / 60_000L).coerceAtLeast(durationMin)
                hcActiveNow -> durationMin
                else -> 0L
            }
            val activeMarker = if (liveWearActive) liveWearSinceMs else sessionStartMs

            WatchSleepSnapshot(
                isSleepingNow = isSleeping,
                activeSleepMinutes = activeDuration,
                activeSessionMarkerMs = if (isSleeping) activeMarker else 0L,
                sleepSourceLabel = if (liveWearActive) liveWearSource else sourceLabel,
                latestCompletedEndMs = if (!hcActiveNow && durationMin >= 10L) sessionEndMs else 0L,
                latestCompletedDurationMinutes = if (!hcActiveNow && durationMin >= 10L) durationMin else 0L,
                sleepPermissionGranted = true,
                hasWatchSleepDetected = watchConnected
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Watch sleep check skipped: ${e.message}")
            WatchSleepSnapshot(
                isSleepingNow = liveWearActive,
                activeSleepMinutes = if (liveWearActive) ((nowMs - liveWearSinceMs) / 60_000L).coerceAtLeast(1L) else 0L,
                activeSessionMarkerMs = if (liveWearActive) liveWearSinceMs else 0L,
                sleepSourceLabel = if (liveWearActive) liveWearSource else "",
                sleepPermissionGranted = _status.value.sleepPermissionGranted,
                hasWatchSleepDetected = liveWearActive
            )
        }
    }

    companion object {
        private const val TAG = "HealthConnectSteps"
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
        const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
        const val GALAXY_WEARABLE_PACKAGE = "com.samsung.android.app.watchmanager"
        private const val LOOKBACK_SECONDS = 24L * 60L * 60L
        private const val SLEEP_LOOKBACK_SECONDS = 7L * 24L * 60L * 60L
        private const val WATERMARK_RETENTION_MS = 36L * 60L * 60L * 1000L
        private const val MAX_WATERMARK_ENTRIES = 250
        private const val MAX_PAGES = 10
        private const val MAX_SINGLE_RECORD_DELTA = 50_000L
    }
}
