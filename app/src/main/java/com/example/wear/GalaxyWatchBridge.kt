package com.example.wear

import android.content.Context
import android.util.Log
import com.example.data.model.HeroProfile
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object WearDataPaths {
    const val CAPABILITY_SOLSTRIDE_WATCH_STEP_TRACKER = "solstride_watch_step_tracker"
    const val STEPS_BATCH = "/solstride/steps"
    const val WATCH_NODE_STATUS = "/solstride/watch_node_status"
    const val WATCH_DISCONNECTED = "/solstride/watch_disconnected"
    const val HEARTBEAT_PING = "/solstride/heartbeat_ping"
    const val HEARTBEAT_ACK = "/solstride/heartbeat_ack"
    const val REQUEST_STEPS_SYNC = "/solstride/request_steps"
    const val HERO_HUD_SYNC = "/solstride/hero_state"
    const val ENCOUNTER_ALERT = "/solstride/encounter_alert"
    const val KEY_STEPS_DELTA = "steps_delta"
    const val KEY_BATCH_ID = "batch_id"
    const val KEY_TIMESTAMP = "recorded_at_ms"
    const val KEY_WATCH_CONNECTED = "watch_connected"
    const val KEY_WATCH_NAME = "watch_name"
}

/**
 * Shared in-process heartbeat registry updated by both SolstrideWearableListenerService and
 * GalaxyWatchBridge whenever the watch responds with a heartbeat ACK or live step payload.
 *
 * A watch is ONLY considered reachable when:
 *  1. CapabilityClient reports a nearby reachable node advertising `solstride_watch_step_tracker`
 *     (or an active ACK was received from the watch), AND
 *  2. The latest heartbeat ACK is fresher than HEARTBEAT_TIMEOUT_MS (12 seconds) with fewer than
 *     MAX_MISSED_HEARTBEATS consecutive missed pings.
 *
 * This prevents stale Wear OS / Galaxy Wearable Bluetooth phantom nodes from falsely reporting
 * "Watch Connected" when the watch is actually disconnected, out of range, or unreachable.
 */
object WatchHeartbeatRegistry {
    const val HEARTBEAT_TIMEOUT_MS = 12_000L
    const val MAX_MISSED_HEARTBEATS = 2

    private val lastAckTimestampMs = AtomicLong(0L)
    private val lastNodeId = AtomicReference<String?>(null)
    private val lastWatchName = AtomicReference<String?>("Samsung Galaxy Watch")
    private val consecutiveMissedPings = AtomicInteger(0)

    fun recordHeartbeatAck(
        nodeId: String?,
        watchName: String?,
        ackMs: Long = System.currentTimeMillis()
    ) {
        lastAckTimestampMs.set(ackMs)
        if (!nodeId.isNullOrBlank()) {
            lastNodeId.set(nodeId)
        }
        if (!watchName.isNullOrBlank()) {
            lastWatchName.set(watchName)
        }
        consecutiveMissedPings.set(0)
    }

    fun recordMissedPing(): Int = consecutiveMissedPings.incrementAndGet()

    fun isHeartbeatFresh(nowMs: Long = System.currentTimeMillis()): Boolean {
        val lastAck = lastAckTimestampMs.get()
        if (lastAck <= 0L) return false
        if (consecutiveMissedPings.get() >= MAX_MISSED_HEARTBEATS) return false
        return (nowMs - lastAck) <= HEARTBEAT_TIMEOUT_MS
    }

    fun latestAckMs(): Long = lastAckTimestampMs.get()
    fun latestNodeId(): String? = lastNodeId.get()
    fun latestWatchName(): String? = lastWatchName.get()

    fun clear() {
        lastAckTimestampMs.set(0L)
        lastNodeId.set(null)
        consecutiveMissedPings.set(MAX_MISSED_HEARTBEATS)
    }
}

data class WatchStepPayload(
    val batchId: String,
    val stepDelta: Long,
    val recordedAtMs: Long
)

data class WatchLinkStatus(
    val connected: Boolean = false,
    val watchDisplayName: String = "No Watch Connected",
    val connectedNodeId: String? = null,
    val lastCheckedMs: Long = 0L,
    val lastAutoSyncMs: Long = 0L,
    val lastHeartbeatAckMs: Long = 0L,
    val autoSyncIntervalSec: Int = 5,
    val lastError: String? = null
)

class GalaxyWatchBridge(context: Context) {
    private val appContext = context.applicationContext
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val capabilityClient = Wearable.getCapabilityClient(appContext)
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)

    private val _linkStatus = MutableStateFlow(WatchLinkStatus())
    val linkStatus: StateFlow<WatchLinkStatus> = _linkStatus.asStateFlow()

    private var wasWatchConnected = false

    private val dataChangedListener = DataClient.OnDataChangedListener { dataEvents: DataEventBuffer ->
        val now = System.currentTimeMillis()
        var disconnectDetected = false
        var ackReceived = false

        for (event in dataEvents) {
            val path = event.dataItem.uri.path ?: continue
            val hostNodeId = event.dataItem.uri.host

            if (event.type == DataEvent.TYPE_DELETED &&
                (path == WearDataPaths.WATCH_NODE_STATUS ||
                    path == WearDataPaths.HEARTBEAT_ACK ||
                    path == WearDataPaths.STEPS_BATCH)
            ) {
                disconnectDetected = true
            } else if (event.type == DataEvent.TYPE_CHANGED) {
                when (path) {
                    WearDataPaths.HEARTBEAT_ACK -> {
                        val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val name = map.getString(WearDataPaths.KEY_WATCH_NAME)
                        val ackAt = map.getLong(WearDataPaths.KEY_TIMESTAMP, now)
                        WatchHeartbeatRegistry.recordHeartbeatAck(hostNodeId, name, ackAt)
                        ackReceived = true
                    }
                    WearDataPaths.WATCH_NODE_STATUS -> {
                        val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val isConn = map.getBoolean(WearDataPaths.KEY_WATCH_CONNECTED, true)
                        val name = map.getString(WearDataPaths.KEY_WATCH_NAME)
                        if (!isConn) {
                            disconnectDetected = true
                        } else {
                            WatchHeartbeatRegistry.recordHeartbeatAck(hostNodeId, name, now)
                            ackReceived = true
                        }
                    }
                }
            }
        }
        if (disconnectDetected) {
            WatchHeartbeatRegistry.clear()
            bridgeScope.launch { refreshWatchConnection() }
        } else if (ackReceived) {
            bridgeScope.launch { refreshWatchConnection() }
        }
    }

    private val capabilityChangedListener =
        CapabilityClient.OnCapabilityChangedListener { capabilityInfo: CapabilityInfo ->
            val hasNearbyNode = capabilityInfo.nodes.any { it.isNearby }
            if (!hasNearbyNode) {
                WatchHeartbeatRegistry.clear()
            }
            bridgeScope.launch {
                refreshWatchConnection()
            }
        }

    private val messageReceivedListener =
        MessageClient.OnMessageReceivedListener { messageEvent: MessageEvent ->
            val now = System.currentTimeMillis()
            when (messageEvent.path) {
                WearDataPaths.HEARTBEAT_ACK -> {
                    val payload = runCatching {
                        String(messageEvent.data, Charsets.UTF_8).trim()
                    }.getOrDefault("")
                    val watchName = payload.takeIf { it.isNotBlank() && it.toLongOrNull() == null }
                        ?: "Samsung Galaxy Watch"
                    WatchHeartbeatRegistry.recordHeartbeatAck(
                        nodeId = messageEvent.sourceNodeId,
                        watchName = watchName,
                        ackMs = now
                    )
                    bridgeScope.launch { refreshWatchConnection() }
                }
                WearDataPaths.WATCH_DISCONNECTED -> {
                    WatchHeartbeatRegistry.clear()
                    bridgeScope.launch { refreshWatchConnection() }
                }
            }
        }

    init {
        runCatching { dataClient.addListener(dataChangedListener) }
        runCatching {
            capabilityClient.addListener(
                capabilityChangedListener,
                WearDataPaths.CAPABILITY_SOLSTRIDE_WATCH_STEP_TRACKER
            )
        }
        runCatching { messageClient.addListener(messageReceivedListener) }
    }

    fun markWatchConnectionFromBroadcast(
        connected: Boolean,
        nodeId: String?,
        displayName: String?,
        heartbeatAckMs: Long = 0L
    ) {
        val now = System.currentTimeMillis()
        if (connected) {
            val effectiveAck = if (heartbeatAckMs > 0L) heartbeatAckMs else now
            WatchHeartbeatRegistry.recordHeartbeatAck(nodeId, displayName, effectiveAck)
            wasWatchConnected = true
            _linkStatus.value = _linkStatus.value.copy(
                connected = true,
                watchDisplayName = displayName?.ifBlank { "Samsung Galaxy Watch" } ?: "Samsung Galaxy Watch",
                connectedNodeId = nodeId ?: _linkStatus.value.connectedNodeId,
                lastCheckedMs = now,
                lastHeartbeatAckMs = effectiveAck,
                lastError = null
            )
        } else {
            WatchHeartbeatRegistry.clear()
            wasWatchConnected = false
            _linkStatus.value = _linkStatus.value.copy(
                connected = false,
                watchDisplayName = "Watch Disconnected — Phone Pedometer Active",
                connectedNodeId = null,
                lastCheckedMs = now,
                lastHeartbeatAckMs = 0L,
                lastError = null
            )
        }
    }

    /**
     * Queries CapabilityClient (`FILTER_REACHABLE`) for a nearby watch advertising
     * `solstride_watch_step_tracker`, sends a live `/solstride/heartbeat_ping` probe with a short
     * timeout, and verifies a fresh heartbeat ACK in WatchHeartbeatRegistry before declaring the
     * watch connected. If the capability is absent or the heartbeat expires, the watch is marked
     * unreachable and a broadcast intent is fired to enable the phone's local step counter sensor.
     */
    suspend fun refreshWatchConnection(): WatchLinkStatus {
        val now = System.currentTimeMillis()
        return try {
            val capabilityInfo = withTimeoutOrNull(CAPABILITY_QUERY_TIMEOUT_MS) {
                capabilityClient.getCapability(
                    WearDataPaths.CAPABILITY_SOLSTRIDE_WATCH_STEP_TRACKER,
                    CapabilityClient.FILTER_REACHABLE
                ).await()
            }

            // Require an actively nearby capable node (ignores stale cloud-routed phantom nodes)
            val reachableNode = capabilityInfo?.nodes?.firstOrNull { it.isNearby }

            if (reachableNode != null) {
                // Active heartbeat ping probe to verify the watch is genuinely reachable right now
                val pingDelivered = withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                    runCatching {
                        messageClient.sendMessage(
                            reachableNode.id,
                            WearDataPaths.HEARTBEAT_PING,
                            now.toString().toByteArray(Charsets.UTF_8)
                        ).await()
                        true
                    }.getOrDefault(false)
                } ?: false

                if (!pingDelivered) {
                    WatchHeartbeatRegistry.recordMissedPing()
                }
            } else {
                WatchHeartbeatRegistry.recordMissedPing()
            }

            val heartbeatFresh = WatchHeartbeatRegistry.isHeartbeatFresh(now)
            val isReachableAndAlive = reachableNode != null && heartbeatFresh
            val prevAutoSync = _linkStatus.value.lastAutoSyncMs
            val previousName = _linkStatus.value.watchDisplayName
            val previousNodeId = _linkStatus.value.connectedNodeId

            val updated = if (isReachableAndAlive && reachableNode != null) {
                val resolvedName = reachableNode.displayName.ifBlank {
                    WatchHeartbeatRegistry.latestWatchName() ?: "Samsung Galaxy Watch"
                }
                val latestAck = WatchHeartbeatRegistry.latestAckMs()
                if (!wasWatchConnected) {
                    wasWatchConnected = true
                    WatchConnectionBroadcast.sendToggleLocalStepSensorBroadcast(
                        context = appContext,
                        watchConnected = true,
                        nodeId = reachableNode.id,
                        watchName = resolvedName,
                        heartbeatAckMs = latestAck
                    )
                }
                WatchLinkStatus(
                    connected = true,
                    watchDisplayName = resolvedName,
                    connectedNodeId = reachableNode.id,
                    lastCheckedMs = now,
                    lastAutoSyncMs = prevAutoSync,
                    lastHeartbeatAckMs = latestAck,
                    lastError = null
                )
            } else {
                if (wasWatchConnected) {
                    wasWatchConnected = false
                    WatchHeartbeatRegistry.clear()
                    WatchConnectionBroadcast.sendToggleLocalStepSensorBroadcast(
                        context = appContext,
                        watchConnected = false,
                        nodeId = previousNodeId,
                        watchName = previousName
                    )
                }
                WatchLinkStatus(
                    connected = false,
                    watchDisplayName = "No Watch Connected (Phone Pedometer Active)",
                    connectedNodeId = null,
                    lastCheckedMs = now,
                    lastAutoSyncMs = prevAutoSync,
                    lastHeartbeatAckMs = 0L,
                    lastError = null
                )
            }
            _linkStatus.value = updated
            updated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GalaxyWatchBridge", "Capability/heartbeat check indicates watch unreachable: ${e.message}")
            if (wasWatchConnected) {
                wasWatchConnected = false
                WatchHeartbeatRegistry.clear()
                WatchConnectionBroadcast.sendToggleLocalStepSensorBroadcast(
                    context = appContext,
                    watchConnected = false
                )
            }
            val fallback = WatchLinkStatus(
                connected = false,
                watchDisplayName = "No Watch Connected (Phone Pedometer Active)",
                connectedNodeId = null,
                lastCheckedMs = now,
                lastAutoSyncMs = _linkStatus.value.lastAutoSyncMs,
                lastHeartbeatAckMs = 0L,
                lastError = null
            )
            _linkStatus.value = fallback
            fallback
        }
    }

    /**
     * Actively pulls any pending step batches from Wear OS DataClient ONLY when the watch is
     * verified reachable via CapabilityClient + heartbeat ACK, and deletes consumed data items
     * so cached historical DataClient items are never re-read when the watch is disconnected.
     */
    suspend fun pullPendingWatchSteps(): List<WatchStepPayload> {
        val now = System.currentTimeMillis()
        if (!_linkStatus.value.connected || !WatchHeartbeatRegistry.isHeartbeatFresh(now)) {
            return emptyList()
        }
        val results = mutableListOf<WatchStepPayload>()
        try {
            val connectedNodeId = _linkStatus.value.connectedNodeId ?: return emptyList()
            runCatching {
                withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                    messageClient.sendMessage(
                        connectedNodeId,
                        WearDataPaths.REQUEST_STEPS_SYNC,
                        byteArrayOf()
                    ).await()
                }
            }

            val buffer = dataClient.dataItems.await()
            val consumedUris = mutableListOf<android.net.Uri>()
            try {
                for (item in buffer) {
                    val path = item.uri.path ?: continue
                    if (path == WearDataPaths.STEPS_BATCH) {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        val steps = map.getLong(WearDataPaths.KEY_STEPS_DELTA, 0L)
                        val recordedAt = map.getLong(WearDataPaths.KEY_TIMESTAMP, 0L)
                        if (steps > 0L && (now - recordedAt) <= FRESH_STEP_ITEM_MAX_AGE_MS) {
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
                        consumedUris.add(item.uri)
                    }
                }
            } finally {
                buffer.release()
            }

            for (uri in consumedUris) {
                runCatching { dataClient.deleteDataItems(uri).await() }
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
        if (!_linkStatus.value.connected) return false
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
        if (!_linkStatus.value.connected) return false
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

    private companion object {
        const val CAPABILITY_QUERY_TIMEOUT_MS = 1_500L
        const val HEARTBEAT_PING_TIMEOUT_MS = 1_500L
        const val FRESH_STEP_ITEM_MAX_AGE_MS = 60_000L
    }
}
