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
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
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

data class HealthConnectSyncStatus(
    val state: HealthConnectState = HealthConnectState.UNAVAILABLE,
    val primaryWatchSource: String = "Health Connect",
    val watchStepsToday: Long = 0L,
    val phoneStepsIgnoredToday: Long = 0L,
    val backgroundReadSupported: Boolean = false,
    val backgroundReadGranted: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val lastError: String? = null
)

/**
 * Full-compatibility Android Health Connect client for Solstride.
 *
 * - Supports Samsung Health (Galaxy Watch), Pixel Watch / Fitbit, Garmin Connect, Google Fit,
 *   Amazfit / Zepp, Oura, Polar, Suunto, Withings, and Wear OS.
 * - Supports optional Health Connect background read (`READ_HEALTH_DATA_IN_BACKGROUND`) when
 *   available on the device, while functioning 100% with foreground `READ_STEPS` + 24h catch-up.
 * - Uses per-record delta watermarks persisted in SolstridePreferences so live in-place updates
 *   (same metadata.id, growing count) are captured every 5 seconds without double-counting.
 * - Filters out phone-only step records while Solstride's built-in phone pedometer is active
 *   (unless the user enables "Include All Health Connect Sources" or pauses the phone pedometer).
 */
class HealthConnectStepReader(
    context: Context,
    private val preferences: SolstridePreferences
) {
    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(HealthConnectSyncStatus())
    val status: StateFlow<HealthConnectSyncStatus> = _status.asStateFlow()

    fun coreStepPermissions(): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setOf(HealthPermission.getReadPermission(StepsRecord::class))
        } else {
            emptySet()
        }
    }

    fun requestablePermissions(): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptySet()
        val perms = linkedSetOf(HealthPermission.getReadPermission(StepsRecord::class))
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val unsupported = HealthConnectSyncStatus(
                state = HealthConnectState.UNAVAILABLE,
                primaryWatchSource = "Requires Android 8.0+"
            )
            _status.value = unsupported
            return unsupported
        }
        return refreshStatusApi26()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun refreshStatusApi26(): HealthConnectSyncStatus {
        return try {
            when (HealthConnectClient.getSdkStatus(appContext, PROVIDER_PACKAGE_NAME)) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    val s = _status.value.copy(
                        state = HealthConnectState.UNAVAILABLE,
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
                        backgroundReadSupported = bgSupported,
                        backgroundReadGranted = bgGranted,
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
                lastError = e.localizedMessage
            )
            _status.value = s
            s
        }
    }

    /**
     * Pulls newly added or incremented StepsRecord deltas from Health Connect over the last 24 hours.
     *
     * @param includeAllSources When true, reads all Health Connect step sources (watch + fitness apps + phone).
     *                          When false, skips phone-only records to avoid double-counting Solstride's built-in pedometer.
     */
    suspend fun pullNewWatchSteps(includeAllSources: Boolean = false): List<WatchStepPayload> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        return pullNewWatchStepsApi26(includeAllSources = includeAllSources)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pullNewWatchStepsApi26(includeAllSources: Boolean): List<WatchStepPayload> {
        val currentStatus = refreshStatusApi26()
        if (currentStatus.state != HealthConnectState.CONNECTED) {
            return emptyList()
        }

        val nowMs = System.currentTimeMillis()
        val nowInstant = Instant.ofEpochMilli(nowMs)
        val lookbackStart = nowInstant.minusSeconds(LOOKBACK_SECONDS)
        val todayStartInstant = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

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

            val watermarks = decodeWatermarks(preferences.readHealthConnectWatermarksRaw()).toMutableMap()
            var watermarksChanged = false
            val payloads = mutableListOf<WatchStepPayload>()

            var watchStepsToday = 0L
            var phoneStepsIgnoredToday = 0L
            val detectedSources = linkedSetOf<String>()

            for (record in allRecords) {
                val count = record.count
                if (count <= 0L) continue

                val originPkg = record.metadata.dataOrigin.packageName
                if (originPkg == appContext.packageName) continue

                val deviceType = record.metadata.device?.type
                val isToday = !record.endTime.isBefore(todayStartInstant)

                if (!includeAllSources && isPhoneOnlyRecord(originPkg, deviceType)) {
                    if (isToday) phoneStepsIgnoredToday += count
                    continue
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

            val primaryLabel = detectedSources.firstOrNull() ?: "Health Connect (Ready)"
            _status.value = currentStatus.copy(
                state = HealthConnectState.CONNECTED,
                primaryWatchSource = primaryLabel,
                watchStepsToday = watchStepsToday,
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
                lastSyncedAtMs = nowMs,
                lastError = "Health Connect permission check needed"
            )
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect readRecords failed: ${e.message}")
            _status.value = _status.value.copy(
                lastSyncedAtMs = nowMs,
                lastError = e.localizedMessage
            )
            emptyList()
        }
    }

    /**
     * Excludes phone-only step records so Health Connect never double-counts Solstride's
     * built-in phone pedometer. Accepts watch/band/ring devices and wearable companion apps
     * (like Samsung Health, Fitbit, Garmin, Wear OS) even when OEM writes Device.TYPE_UNKNOWN.
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

    companion object {
        private const val TAG = "HealthConnectSteps"
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
        private const val LOOKBACK_SECONDS = 24L * 60L * 60L
        private const val WATERMARK_RETENTION_MS = 36L * 60L * 60L * 1000L
        private const val MAX_WATERMARK_ENTRIES = 250
        private const val MAX_PAGES = 10
        private const val MAX_SINGLE_RECORD_DELTA = 50_000L
    }
}
