package com.example.hardware

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.firebase.SolstrideFirestoreRepository
import com.example.data.local.PendingStepBatchEntity
import com.example.data.local.SolstrideLocalDatabase
import com.example.data.local.SolstridePreferences
import com.example.data.local.SyncLogEntity
import com.example.data.model.HeroClass
import com.example.data.model.StepSource
import com.example.data.model.currentCalendarDateKey
import com.example.data.model.currentIsoWeekKey
import com.example.wear.HealthConnectStepReader
import com.google.android.gms.wearable.Wearable
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Process-wide guard ensuring background WorkManager checkpoints never collide
 * with the live foreground pedometer / 5-second watch auto-sync loop.
 */
object BackgroundSyncGuard {
    private val foregroundActive = AtomicBoolean(false)

    fun setForeground(active: Boolean) {
        foregroundActive.set(active)
    }

    fun isForeground(): Boolean = foregroundActive.get()
}

object StepBackgroundSyncScheduler {
    private const val UNIQUE_WORK_NAME = "solstride_background_step_sync"
    private const val IMMEDIATE_WORK_NAME = "solstride_widget_immediate_step_sync"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<StepBackgroundSyncWorker>(
            15L,
            TimeUnit.MINUTES
        ).build()

        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }.onFailure { err ->
            Log.w("StepBgScheduler", "Unable to schedule periodic background step sync: ${err.message}")
        }
    }

    fun enqueueImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<StepBackgroundSyncWorker>().build()
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }.onFailure { err ->
            Log.w("StepBgScheduler", "Unable to enqueue immediate step sync: ${err.message}")
        }
    }
}

/**
 * Periodically wakes in the background (even when Solstride is completely closed or after reboot)
 * to checkpoint hardware TYPE_STEP_COUNTER steps, check Health Connect watch steps if permitted,
 * and flush pending Room step batches to Cloud Firestore.
 */
class StepBackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // When the app is actively open in the foreground, the live pedometer + 5s loop owns sync.
        if (BackgroundSyncGuard.isForeground()) {
            return Result.success()
        }

        val preferences = SolstridePreferences(applicationContext)
        val localDb = SolstrideLocalDatabase.getInstance(applicationContext)
        val dao = localDb.offlineSyncDao()
        val activeUid = com.example.data.firebase.safeCurrentUser(applicationContext)?.uid ?: "__WATCH_UNBOUND__"
        val now = System.currentTimeMillis()

        try {
            // 1. Check live Wear OS / Galaxy Watch Bluetooth node connection first
            val wearWatchConnected = isWearWatchConnectedNow()
            if (!wearWatchConnected) {
                preferences.setLastWatchStepReceivedMs(0L)
            }

            // 2. Pull Health Connect & Samsung Health watch steps
            val includeAllHcSources = preferences.readHealthConnectAllSources() || !preferences.readPedometerEnabled()
            val hcPayloads = runCatching {
                HealthConnectStepReader(applicationContext, preferences).pullNewWatchSteps(
                    includeAllSources = includeAllHcSources,
                    watchConnected = wearWatchConnected
                )
            }.getOrDefault(emptyList())

            if (wearWatchConnected && hcPayloads.isNotEmpty()) {
                preferences.setLastWatchStepReceivedMs(now)
            }
            for (payload in hcPayloads) {
                dao.insertBatch(
                    PendingStepBatchEntity(
                        batchId = payload.batchId,
                        userId = activeUid,
                        source = StepSource.GALAXY_WATCH.name,
                        stepDelta = payload.stepDelta,
                        recordedAtMs = payload.recordedAtMs
                    )
                )
            }

            // 3. Phone pedometer ONLY pauses when a watch is actually connected right now
            val watchConnected = wearWatchConnected

            // 3. Phone pedometer ONLY counts when NO watch is connected!
            if (hasActivityRecognitionPermission()) {
                val cumulative = readHardwareStepCounterOnce()
                if (cumulative != null && cumulative >= 0L) {
                    if (watchConnected || !preferences.readPedometerEnabled()) {
                        // Watch is connected: silently advance baseline WITHOUT counting phone steps
                        // so watch-time steps are never double-counted later if the watch disconnects.
                        preferences.setLastHardwareStepCount(cumulative)
                    } else {
                        val savedBaseline = preferences.readLastHardwareStepCount()
                        val delta = when {
                            savedBaseline < 0L -> {
                                preferences.setLastHardwareStepCount(cumulative)
                                0L
                            }
                            cumulative < savedBaseline -> {
                                preferences.setLastHardwareStepCount(cumulative)
                                cumulative.coerceAtMost(MAX_BACKGROUND_DELTA)
                            }
                            else -> {
                                val diff = cumulative - savedBaseline
                                if (diff > 0L) {
                                    preferences.setLastHardwareStepCount(cumulative)
                                }
                                diff.coerceAtMost(MAX_BACKGROUND_DELTA)
                            }
                        }

                        if (delta > 0L) {
                            preferences.recordDailyPedometerSteps(currentCalendarDateKey(), delta)
                            dao.insertBatch(
                                PendingStepBatchEntity(
                                    batchId = "bg_phone_${now}_${delta}_${UUID.randomUUID().toString().take(6)}",
                                    userId = activeUid,
                                    source = StepSource.PHONE.name,
                                    stepDelta = delta,
                                    recordedAtMs = now
                                )
                            )
                            Log.d(TAG, "Captured +$delta closed-app phone steps (no watch connected)")
                        }
                    }
                }
            }

            // 4. If signed in and online, merge queued step batches into Cloud Firestore
            val signedInUid = com.example.data.firebase.safeCurrentUser(applicationContext)?.uid
            if (signedInUid != null) {
                dao.claimUnboundWatchBatches(signedInUid)
                flushToCloudInBackground(signedInUid, dao, preferences)
            }

            // 5. Refresh home screen widgets with full Hero, Pet, Hardcore, Step & Watch snapshot
            com.example.widget.WidgetAutoSyncWorker.refreshFullWidgetSnapshot(
                context = applicationContext,
                watchConnectedOverride = wearWatchConnected
            )

            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background step sync skipped (will retry): ${e.message}")
            return Result.success()
        }
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun readHardwareStepCounterOnce(): Long? {
        val mgr = applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return null
        val stepCounter = mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null

        return withTimeoutOrNull(SENSOR_READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var completed = false
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (completed) return
                        val value = event?.values?.firstOrNull()?.toLong() ?: return
                        completed = true
                        runCatching { mgr.unregisterListener(this) }
                        if (cont.isActive) cont.resume(value)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                cont.invokeOnCancellation {
                    runCatching { mgr.unregisterListener(listener) }
                }

                val registered = try {
                    mgr.registerListener(listener, stepCounter, SensorManager.SENSOR_DELAY_NORMAL, 0)
                } catch (_: SecurityException) {
                    false
                }

                if (!registered && cont.isActive) {
                    completed = true
                    cont.resume(null)
                }
            }
        }
    }

    private suspend fun flushToCloudInBackground(
        uid: String,
        dao: com.example.data.local.OfflineSyncDao,
        preferences: SolstridePreferences
    ) {
        val batches = dao.getPendingBatches(uid)
        if (batches.isEmpty()) return

        val totalDelta = batches.sumOf { it.stepDelta }.coerceAtLeast(0L)
        if (totalDelta <= 0L) {
            dao.markBatchesSynced(batches.map { it.batchId })
            return
        }

        val databaseId = applicationContext.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        val repository = SolstrideFirestoreRepository(firestore)

        val profile = withTimeoutOrNull(6_000L) {
            repository.observeHeroProfile(uid).first()
        } ?: return

        val weekNow = currentIsoWeekKey()
        val prevWeekly = if (profile.weekKey == weekNow) profile.weeklySteps else 0L
        val newTotalSteps = (profile.totalSteps + totalDelta).coerceAtMost(1_000_000_000L)
        val newWeeklySteps = (prevWeekly + totalDelta).coerceAtMost(10_000_000L)

        // Steps are ONLY used to level the Hero (Hero XP) and fuel Pet Arena Widget training
        val stepXpGain = com.example.domain.ProgressionEngine.computeHeroStepXp(totalDelta)
        val leveled = com.example.domain.ProgressionEngine.applyStepXpToProfile(
            profile = profile,
            xpGain = stepXpGain
        )

        val updateMap = mapOf(
            "totalSteps" to newTotalSteps,
            "weeklySteps" to newWeeklySteps,
            "weekKey" to weekNow,
            "level" to leveled.level,
            "xp" to leveled.xp,
            "xpToNextLevel" to leveled.xpToNextLevel,
            "unspentPoints" to leveled.unspentPoints,
            "maxHp" to leveled.maxHp,
            "currentHp" to leveled.currentHp,
            "heroPower" to leveled.heroPower
        )

        val result = repository.updateHeroProfile(uid, updateMap)
        if (result.isSuccess) {
            dao.markBatchesSynced(batches.map { it.batchId })
            val watchDelta = batches.filter { it.source != StepSource.PHONE.name }.sumOf { it.stepDelta }
            if (watchDelta > 0L) {
                preferences.recordDailyPedometerSteps(currentCalendarDateKey(), watchDelta)
            }
            val sources = batches.map { it.source }.distinct().joinToString(" + ")
            val lvlTag = if (leveled.level > profile.level) " ★ Lv.${leveled.level}!" else ""
            dao.insertSyncLog(
                SyncLogEntity(
                    userId = uid,
                    source = "$sources (BG)",
                    stepDelta = totalDelta,
                    summary = "+$totalDelta closed-app steps -> +$stepXpGain Hero XP$lvlTag & Pet Stamina",
                    timestampMs = System.currentTimeMillis()
                )
            )
            repository.upsertLeaderboard(
                userId = uid,
                displayName = profile.displayName,
                heroClass = profile.heroClass,
                heroLevel = leveled.level,
                heroPower = leveled.heroPower,
                weeklySteps = newWeeklySteps,
                weekKey = weekNow,
                bossesDefeated = profile.bossesDefeated,
                prestigeLevel = profile.prestigeLevel,
                highestSpireFloor = profile.highestSpireFloor
            )
            val updatedSettings = preferences.settingsFlow.first()
            val todayNow = if (updatedSettings.pedometerDateKey == currentCalendarDateKey()) {
                updatedSettings.pedometerDailySteps
            } else {
                0L
            }
            com.example.widget.SolstrideWidgetSnapshotStore.updateStepTelemetryOnly(
                context = applicationContext,
                todaySteps = todayNow,
                weeklySteps = newWeeklySteps,
                strideEnergy = profile.strideEnergy,
                encounterProgressPct = profile.encounterProgress.toInt(),
                isWatchConnected = isWearWatchConnectedNow()
            )
            com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(applicationContext)
        }
    }

    private suspend fun isWearWatchConnectedNow(): Boolean {
        return withTimeoutOrNull(2_000L) {
            runCatching {
                com.example.wear.WatchConnectionDetector
                    .detectConnectedWatch(applicationContext)
                    .connected
            }.getOrDefault(false)
        } ?: false
    }

    private companion object {
        const val TAG = "StepBgSyncWorker"
        const val SENSOR_READ_TIMEOUT_MS = 4_000L
        const val MAX_BACKGROUND_DELTA = 50_000L
        const val WATCH_ACTIVE_GRACE_MS = 15L * 60L * 1000L
    }
}
