package com.example.wear

import android.content.Context
import android.util.Log
import com.example.data.model.HeroProfile
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

object WearDataPaths {
    const val STEPS_BATCH = "/solstride/steps"
    const val REQUEST_STEPS_SYNC = "/solstride/request_steps"
    const val HERO_HUD_SYNC = "/solstride/hero_state"
    const val ENCOUNTER_ALERT = "/solstride/encounter_alert"
    const val KEY_STEPS_DELTA = "steps_delta"
    const val KEY_BATCH_ID = "batch_id"
    const val KEY_TIMESTAMP = "recorded_at_ms"
}

data class WatchStepPayload(
    val batchId: String,
    val stepDelta: Long,
    val recordedAtMs: Long
)

data class WatchLinkStatus(
    val connected: Boolean = false,
    val watchDisplayName: String = "No Galaxy Watch Linked",
    val connectedNodeId: String? = null,
    val lastCheckedMs: Long = 0L,
    val lastAutoSyncMs: Long = 0L,
    val autoSyncIntervalSec: Int = 5,
    val lastError: String? = null
)

class GalaxyWatchBridge(context: Context) {
    private val appContext = context.applicationContext
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)

    private val _linkStatus = MutableStateFlow(WatchLinkStatus())
    val linkStatus: StateFlow<WatchLinkStatus> = _linkStatus.asStateFlow()

    suspend fun refreshWatchConnection(): WatchLinkStatus {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val primaryNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            val prevAutoSync = _linkStatus.value.lastAutoSyncMs
            val updated = if (primaryNode != null) {
                WatchLinkStatus(
                    connected = true,
                    watchDisplayName = primaryNode.displayName.ifBlank { "Samsung Galaxy Watch" },
                    connectedNodeId = primaryNode.id,
                    lastCheckedMs = System.currentTimeMillis(),
                    lastAutoSyncMs = prevAutoSync,
                    lastError = null
                )
            } else {
                WatchLinkStatus(
                    connected = false,
                    watchDisplayName = "No Wear OS Watch Connected",
                    connectedNodeId = null,
                    lastCheckedMs = System.currentTimeMillis(),
                    lastAutoSyncMs = prevAutoSync,
                    lastError = null
                )
            }
            _linkStatus.value = updated
            updated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GalaxyWatchBridge", "Wearable node check unavailable on this device: ${e.message}")
            val fallback = WatchLinkStatus(
                connected = false,
                watchDisplayName = "Watch Sync Ready (Pair via Wear OS)",
                connectedNodeId = null,
                lastCheckedMs = System.currentTimeMillis(),
                lastAutoSyncMs = _linkStatus.value.lastAutoSyncMs,
                lastError = null
            )
            _linkStatus.value = fallback
            fallback
        }
    }

    /**
     * Actively pulls any pending step batches from Wear OS DataClient and nudges the connected
     * watch to push its latest steps. Invoked on a 5-second auto-sync cadence while active.
     */
    suspend fun pullPendingWatchSteps(): List<WatchStepPayload> {
        val now = System.currentTimeMillis()
        val results = mutableListOf<WatchStepPayload>()
        try {
            val connectedNodeId = _linkStatus.value.connectedNodeId
            if (connectedNodeId != null) {
                runCatching {
                    messageClient.sendMessage(
                        connectedNodeId,
                        WearDataPaths.REQUEST_STEPS_SYNC,
                        byteArrayOf()
                    ).await()
                }
            }

            val buffer = dataClient.dataItems.await()
            try {
                for (item in buffer) {
                    val path = item.uri.path ?: continue
                    if (path == WearDataPaths.STEPS_BATCH) {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        val steps = map.getLong(WearDataPaths.KEY_STEPS_DELTA, 0L)
                        if (steps > 0L) {
                            val recordedAt = map.getLong(WearDataPaths.KEY_TIMESTAMP, now)
                            val batchId = map.getString(WearDataPaths.KEY_BATCH_ID)
                                ?.takeIf { it.isNotBlank() }
                                ?: "watch_data_${recordedAt}_$steps"
                            results.add(
                                WatchStepPayload(
                                    batchId = batchId,
                                    stepDelta = steps,
                                    recordedAtMs = recordedAt
                                )
                            )
                        }
                    }
                }
            } finally {
                buffer.release()
            }
            _linkStatus.value = _linkStatus.value.copy(lastAutoSyncMs = now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("GalaxyWatchBridge", "5s watch step poll skipped: ${e.message}")
            _linkStatus.value = _linkStatus.value.copy(lastAutoSyncMs = now)
        }
        return results
    }

    suspend fun pushHeroSnapshotToWatch(profile: HeroProfile): Boolean {
        return try {
            val request = PutDataMapRequest.create(WearDataPaths.HERO_HUD_SYNC).apply {
                dataMap.putString("hero_name", profile.displayName)
                dataMap.putInt("level", profile.level.toInt())
                dataMap.putInt("hp", profile.currentHp.toInt())
                dataMap.putInt("max_hp", profile.maxHp.toInt())
                dataMap.putInt("energy", profile.strideEnergy.toInt())
                dataMap.putInt("max_energy", profile.maxStrideEnergy.toInt())
                dataMap.putInt("encounter_pct", profile.encounterProgress.toInt())
                dataMap.putLong("weekly_steps", profile.weeklySteps)
                dataMap.putLong("sol_coins", profile.solCoins)
                dataMap.putLong("expedition_end_ms", profile.activeExpeditionEndMs)
                dataMap.putLong("synced_at", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GalaxyWatchBridge", "Deferred watch HUD sync: ${e.message}")
            false
        }
    }

    suspend fun sendEncounterHapticToWatch(enemyName: String): Boolean {
        val nodeId = _linkStatus.value.connectedNodeId ?: return false
        return try {
            messageClient.sendMessage(
                nodeId,
                WearDataPaths.ENCOUNTER_ALERT,
                enemyName.toByteArray(Charsets.UTF_8)
            ).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GalaxyWatchBridge", "Watch encounter alert skipped: ${e.message}")
            false
        }
    }
}
