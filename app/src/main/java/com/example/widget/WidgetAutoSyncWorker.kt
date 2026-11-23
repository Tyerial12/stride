package com.example.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.firebase.SolstrideFirestoreRepository
import com.example.data.firebase.safeCurrentUser
import com.example.data.local.SolstridePreferences
import com.example.data.model.computeDerivedStats
import com.example.data.model.currentCalendarDateKey
import com.example.wear.WatchConnectionDetector
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Schedules battery-conscious WorkManager auto-sync for all 4 Solstride Home Screen Widgets:
 * - Periodic background sync (15 / 30 / 60 min) when enabled.
 * - Immediate one-shot sync on widget placement, watch events, or user refresh taps.
 * - Precision Expedition Completion refresh so widgets automatically flip to "Claim Ready"
 *   the moment a timed expedition finishes, even when the app is closed.
 */
object WidgetAutoSyncScheduler {
    private const val PERIODIC_WIDGET_SYNC_NAME = "solstride_widget_auto_sync_periodic"
    private const val IMMEDIATE_WIDGET_SYNC_NAME = "solstride_widget_auto_sync_immediate"
    private const val EXPEDITION_COMPLETE_SYNC_NAME = "solstride_widget_expedition_complete"
    private const val TAG = "WidgetAutoSyncSched"

    fun syncSchedule(context: Context) {
        val appContext = context.applicationContext
        val config = SolstrideWidgetSnapshotStore.readAutoSyncConfig(appContext)
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return

        if (!config.enabled) {
            workManager.cancelUniqueWork(PERIODIC_WIDGET_SYNC_NAME)
            Log.d(TAG, "Widget periodic auto-sync disabled by user")
            return
        }

        val intervalMin = config.intervalMinutes.coerceIn(15L, 60L)
        val request = PeriodicWorkRequestBuilder<WidgetAutoSyncWorker>(
            intervalMin,
            TimeUnit.MINUTES
        ).build()

        runCatching {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WIDGET_SYNC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Scheduled widget auto-sync every ${intervalMin}m")
        }.onFailure { err ->
            Log.w(TAG, "Unable to schedule periodic widget auto-sync: ${err.message}")
        }
    }

    fun triggerImmediateSync(context: Context) {
        val appContext = context.applicationContext
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        val request = OneTimeWorkRequestBuilder<WidgetAutoSyncWorker>().build()
        runCatching {
            workManager.enqueueUniqueWork(
                IMMEDIATE_WIDGET_SYNC_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }.onFailure { err ->
            Log.w(TAG, "Unable to enqueue immediate widget auto-sync: ${err.message}")
        }
    }

    fun scheduleExpeditionCompletionRefresh(context: Context, expeditionEndMs: Long) {
        val appContext = context.applicationContext
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val remainingMs = expeditionEndMs - now

        if (expeditionEndMs <= 0L || remainingMs <= 0L) {
            workManager.cancelUniqueWork(EXPEDITION_COMPLETE_SYNC_NAME)
            return
        }

        val delayMs = (remainingMs + 1_500L).coerceAtMost(TimeUnit.HOURS.toMillis(24L))
        val request = OneTimeWorkRequestBuilder<WidgetAutoSyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        runCatching {
            workManager.enqueueUniqueWork(
                EXPEDITION_COMPLETE_SYNC_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "Scheduled expedition-complete widget refresh in ${delayMs / 1000L}s")
        }.onFailure { err ->
            Log.w(TAG, "Unable to schedule expedition-complete refresh: ${err.message}")
        }
    }

    fun cancelAllIfNoWidgets(context: Context) {
        val appContext = context.applicationContext
        if (SolstrideWidgetUpdater.hasAnyActiveWidgets(appContext)) return
        runCatching {
            val wm = WorkManager.getInstance(appContext)
            wm.cancelUniqueWork(PERIODIC_WIDGET_SYNC_NAME)
            wm.cancelUniqueWork(EXPEDITION_COMPLETE_SYNC_NAME)
            Log.d(TAG, "No active home screen widgets remaining; paused background widget auto-sync")
        }
    }
}

/**
 * Pulls live Hero Profile, Inventory, Companion Pet, Hardcore RPG state, Step Telemetry,
 * Watch Connection status, and Expedition Timers into the widget snapshot and repaints all
 * active Solstride Home Screen Widgets automatically in the background.
 */
class WidgetAutoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val config = SolstrideWidgetSnapshotStore.readAutoSyncConfig(applicationContext)
            val hasWidgets = SolstrideWidgetUpdater.hasAnyActiveWidgets(applicationContext)
            if (!hasWidgets && !config.enabled) {
                return Result.success()
            }

            // First capture any closed-app watch / phone step deltas if not foregrounded
            val watchConnected = withTimeoutOrNull(2_500L) {
                runCatching {
                    WatchConnectionDetector.detectConnectedWatch(applicationContext).connected
                }.getOrDefault(false)
            } ?: false

            refreshFullWidgetSnapshot(
                context = applicationContext,
                watchConnectedOverride = watchConnected
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget auto-sync skipped (will retry on next cycle): ${e.message}")
            Result.success()
        }
    }

    companion object {
        private const val TAG = "WidgetAutoSyncWorker"

        /**
         * Reusable full-state widget sync helper invoked by both WidgetAutoSyncWorker
         * and StepBackgroundSyncWorker so Home Screen Widgets always reflect live
         * Hero Stats, Companion Pet, Hardcore Alerts, Steps, Watch link & Expeditions.
         */
        suspend fun refreshFullWidgetSnapshot(
            context: Context,
            watchConnectedOverride: Boolean? = null
        ) {
            val appContext = context.applicationContext
            val preferences = SolstridePreferences(appContext)
            val settings = preferences.settingsFlow.first()
            val hardcoreState = preferences.hardcoreRpgStateFlow.first()

            val todaySteps = if (settings.pedometerDateKey == currentCalendarDateKey()) {
                settings.pedometerDailySteps
            } else {
                0L
            }

            val isWatchConnected = watchConnectedOverride ?: (withTimeoutOrNull(2_000L) {
                runCatching {
                    WatchConnectionDetector.detectConnectedWatch(appContext).connected
                }.getOrDefault(false)
            } ?: false)

            val sleepSnapshot = runCatching {
                com.example.wear.HealthConnectStepReader(appContext, preferences)
                    .readWatchSleepSnapshot(watchConnected = isWatchConnected)
            }.getOrNull()
            val manuallyWokenMarker = preferences.readManuallyWokenSleepMs()
            val isHeroSleeping = isWatchConnected &&
                sleepSnapshot?.isSleepingNow == true &&
                (sleepSnapshot.activeSessionMarkerMs == 0L || sleepSnapshot.activeSessionMarkerMs != manuallyWokenMarker)
            val sleepSource = if (isWatchConnected) sleepSnapshot?.sleepSourceLabel.orEmpty() else ""

            SolstrideWidgetSnapshotStore.rechargeWildStaminaAndTreats(appContext, todaySteps)

            val uid = safeCurrentUser(appContext)?.uid
            if (uid != null) {
                val syncedFromCloud = runCatching {
                    val databaseId = appContext.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    val repository = SolstrideFirestoreRepository(firestore)

                    val profile = withTimeoutOrNull(5_000L) {
                        repository.observeHeroProfile(uid).first()
                    }
                    if (profile != null) {
                        val inventory = withTimeoutOrNull(4_000L) {
                            repository.observeInventory(uid).first()
                        }.orEmpty()
                        val consumedChestIds = preferences.readConsumedChestIds()
                        val chests = withTimeoutOrNull(4_000L) {
                            repository.observeChests(uid).first()
                        }.orEmpty().filter { chest ->
                            chest.id.isNotBlank() && chest.id !in consumedChestIds
                        }
                        val pets = withTimeoutOrNull(4_000L) {
                            repository.observePets(uid).first()
                        }.orEmpty()
                        val equippedPet = if (profile.equippedPetId.isBlank()) {
                            null
                        } else {
                            pets.firstOrNull {
                                it.petType == profile.equippedPetId || it.id == profile.equippedPetId
                            }
                        }
                        val stats = computeDerivedStats(
                            profile = profile,
                            inventory = inventory,
                            equippedPet = equippedPet,
                            hardcoreState = hardcoreState
                        )
                        var effectiveProfile = profile
                        var effectiveHardcore = hardcoreState
                        if (!isHeroSleeping &&
                            sleepSnapshot != null &&
                            sleepSnapshot.latestCompletedEndMs > 0L &&
                            sleepSnapshot.latestCompletedDurationMinutes >= com.example.domain.ProgressionEngine.MIN_CREDITED_SLEEP_MINUTES
                        ) {
                            val lastCredited = preferences.readLastCreditedSleepEndMs()
                            if (sleepSnapshot.latestCompletedEndMs > lastCredited) {
                                val durationMin = sleepSnapshot.latestCompletedDurationMinutes
                                val sleepReward = com.example.domain.ProgressionEngine.computeSleepSolCoinReward(durationMin)
                                val fraction = (durationMin / 360f).coerceIn(0.25f, 1.0f)
                                val hpGain = (stats.computedMaxHp * fraction).toInt().toLong().coerceAtLeast(15L)
                                val energyGain = (stats.computedMaxEnergy * fraction).toInt().toLong().coerceAtLeast(10L)
                                val woundsCured = when {
                                    durationMin >= 360L -> 3
                                    durationMin >= 180L -> 2
                                    else -> 1
                                }
                                val newHp = (profile.currentHp + hpGain).coerceAtMost(stats.computedMaxHp.toLong())
                                val newEnergy = (profile.strideEnergy + energyGain).coerceAtMost(stats.computedMaxEnergy.toLong())
                                val newSolCoins = (profile.solCoins + sleepReward.solCoinsEarned).coerceAtMost(100_000_000L)

                                val sleepUpdateRes = repository.updateHeroProfile(
                                    uid,
                                    mapOf(
                                        "currentHp" to newHp,
                                        "strideEnergy" to newEnergy,
                                        "solCoins" to newSolCoins
                                    )
                                )
                                if (sleepUpdateRes.isSuccess) {
                                    preferences.setLastCreditedSleepEndMs(sleepSnapshot.latestCompletedEndMs)
                                    preferences.updateHardcoreRpgState { hc ->
                                        val nextWounds = (hc.battleWoundStacks - woundsCured).coerceAtLeast(0)
                                        hc.copy(battleWoundStacks = nextWounds).also { effectiveHardcore = it }
                                    }
                                    effectiveProfile = profile.copy(
                                        currentHp = newHp,
                                        strideEnergy = newEnergy,
                                        solCoins = newSolCoins
                                    )
                                }
                            }
                        }
                        val freshSnapshot = SolstrideWidgetSnapshotStore.buildFromLiveState(
                            context = appContext,
                            profile = effectiveProfile,
                            stats = stats,
                            equippedPet = equippedPet,
                            hardcoreState = effectiveHardcore,
                            todaySteps = todaySteps,
                            dailyStepGoal = settings.dailyStepGoal,
                            isWatchConnected = isWatchConnected,
                            inventory = inventory,
                            unopenedChestCount = chests.size,
                            isHeroSleeping = isHeroSleeping,
                            sleepSourceLabel = sleepSource,
                            activeSleepMinutes = sleepSnapshot?.activeSleepMinutes ?: 0L,
                            lastCompletedSleepMinutes = sleepSnapshot?.latestCompletedDurationMinutes ?: 0L
                        )
                        SolstrideWidgetSnapshotStore.save(appContext, freshSnapshot)
                        if (freshSnapshot.hasActiveExpedition && freshSnapshot.expeditionEndMs > System.currentTimeMillis()) {
                            WidgetAutoSyncScheduler.scheduleExpeditionCompletionRefresh(
                                appContext,
                                freshSnapshot.expeditionEndMs
                            )
                        }
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)

                if (syncedFromCloud) {
                    SolstrideWidgetUpdater.updateAllWidgets(appContext)
                    return
                }
            }

            // Fallback when offline or signed out: update local step/watch/sleep telemetry and repaint widgets
            val prev = SolstrideWidgetSnapshotStore.read(appContext)
            SolstrideWidgetSnapshotStore.updateStepTelemetryOnly(
                context = appContext,
                todaySteps = todaySteps,
                weeklySteps = prev.weeklySteps,
                strideEnergy = prev.strideEnergy,
                encounterProgressPct = prev.encounterProgressPct,
                isWatchConnected = isWatchConnected
            )
            SolstrideWidgetSnapshotStore.updateHeroSleepingOnly(
                context = appContext,
                isSleeping = isHeroSleeping,
                sleepSourceLabel = sleepSource
            )
            SolstrideWidgetUpdater.updateAllWidgets(appContext)
        }
    }
}
