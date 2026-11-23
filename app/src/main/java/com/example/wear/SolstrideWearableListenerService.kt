package com.example.wear

import android.content.Intent
import android.util.Log
import com.example.data.local.PendingStepBatchEntity
import com.example.data.local.SolstrideLocalDatabase
import com.example.data.local.SolstridePreferences
import com.example.data.model.StepSource
import com.example.hardware.StepBackgroundSyncScheduler
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Broadcast contract emitted whenever a Wear OS / Galaxy Watch capability node becomes reachable
 * or unreachable, allowing StepTrackerManager and SolstrideViewModel to immediately toggle the
 * phone's local step counter sensor ON when the watch is unreachable and OFF (standby) when the
 * watch is verified reachable via CapabilityClient + heartbeat ACK.
 */
object WatchConnectionBroadcast {
    const val ACTION_TOGGLE_LOCAL_STEP_SENSOR =
        "com.example.solstride.action.TOGGLE_LOCAL_STEP_SENSOR"
    const val EXTRA_WATCH_CONNECTED = "extra_watch_connected"
    const val EXTRA_ENABLE_LOCAL_STEP_SENSOR = "extra_enable_local_step_sensor"
    const val EXTRA_WATCH_NODE_ID = "extra_watch_node_id"
    const val EXTRA_WATCH_DISPLAY_NAME = "extra_watch_display_name"
    const val EXTRA_HEARTBEAT_ACK_MS = "extra_heartbeat_ack_ms"

    fun sendToggleLocalStepSensorBroadcast(
        context: android.content.Context,
        watchConnected: Boolean,
        nodeId: String? = null,
        watchName: String? = null,
        heartbeatAckMs: Long = 0L
    ) {
        val appContext = context.applicationContext
        val intent = Intent(ACTION_TOGGLE_LOCAL_STEP_SENSOR).apply {
            setPackage(appContext.packageName)
            putExtra(EXTRA_WATCH_CONNECTED, watchConnected)
            putExtra(EXTRA_ENABLE_LOCAL_STEP_SENSOR, !watchConnected)
            if (nodeId != null) putExtra(EXTRA_WATCH_NODE_ID, nodeId)
            if (watchName != null) putExtra(EXTRA_WATCH_DISPLAY_NAME, watchName)
            if (heartbeatAckMs > 0L) putExtra(EXTRA_HEARTBEAT_ACK_MS, heartbeatAckMs)
        }
        appContext.sendBroadcast(intent)
    }
}

class SolstrideWearableListenerService :
    WearableListenerService(),
    DataClient.OnDataChangedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataClient: DataClient
    private lateinit var capabilityClient: CapabilityClient

    override fun onCreate() {
        super.onCreate()
        dataClient = Wearable.getDataClient(applicationContext)
        capabilityClient = Wearable.getCapabilityClient(applicationContext)
        runCatching {
            dataClient.addListener(this)
        }.onFailure { err ->
            Log.d(TAG, "DataClient listener registration skipped: ${err.message}")
        }
        runCatching {
            capabilityClient.addListener(
                this,
                WearDataPaths.CAPABILITY_SOLSTRIDE_WATCH_STEP_TRACKER
            )
        }.onFailure { err ->
            Log.d(TAG, "CapabilityClient listener registration skipped: ${err.message}")
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val dao = SolstrideLocalDatabase.getInstance(applicationContext).offlineSyncDao()
        val preferences = SolstridePreferences(applicationContext)
        val activeUid = com.example.data.firebase.safeCurrentUser(applicationContext)?.uid ?: "__WATCH_UNBOUND__"
        val now = System.currentTimeMillis()

        val pendingBatches = mutableListOf<PendingStepBatchEntity>()
        var watchDisconnectedViaDataEvent = false
        var disconnectedNodeId: String? = null
        var disconnectedWatchName: String? = null

        for (event in dataEvents) {
            val path = event.dataItem.uri.path ?: continue
            val hostNodeId = event.dataItem.uri.host

            if (event.type == DataEvent.TYPE_DELETED &&
                (path == WearDataPaths.WATCH_NODE_STATUS ||
                    path == WearDataPaths.HEARTBEAT_ACK ||
                    path == WearDataPaths.STEPS_BATCH)
            ) {
                watchDisconnectedViaDataEvent = true
                disconnectedNodeId = hostNodeId
                continue
            }

            if (event.type != DataEvent.TYPE_CHANGED) continue

            when (path) {
                WearDataPaths.HEARTBEAT_ACK -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val watchName = map.getString(WearDataPaths.KEY_WATCH_NAME)
                        ?.takeIf { it.isNotBlank() }
                        ?: "Samsung Galaxy Watch"
                    val ackTimestamp = map.getLong(WearDataPaths.KEY_TIMESTAMP, now)
                    WatchHeartbeatRegistry.recordHeartbeatAck(
                        nodeId = hostNodeId,
                        watchName = watchName,
                        ackMs = ackTimestamp
                    )
                    handleWatchNodeReachable(
                        nodeId = hostNodeId,
                        displayName = watchName,
                        ackMs = ackTimestamp
                    )
                }

                WearDataPaths.WATCH_NODE_STATUS -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val connected = map.getBoolean(WearDataPaths.KEY_WATCH_CONNECTED, true)
                    val watchName = map.getString(WearDataPaths.KEY_WATCH_NAME)
                    if (!connected) {
                        watchDisconnectedViaDataEvent = true
                        disconnectedNodeId = hostNodeId
                        disconnectedWatchName = watchName
                    } else {
                        WatchHeartbeatRegistry.recordHeartbeatAck(
                            nodeId = hostNodeId,
                            watchName = watchName,
                            ackMs = now
                        )
                        handleWatchNodeReachable(
                            nodeId = hostNodeId,
                            displayName = watchName ?: "Samsung Galaxy Watch",
                            ackMs = now
                        )
                    }
                }

                WearDataPaths.STEPS_BATCH -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val steps = map.getLong(WearDataPaths.KEY_STEPS_DELTA, 0L)
                    if (steps > 0L) {
                        val batchId = map.getString(WearDataPaths.KEY_BATCH_ID)
                            ?.takeIf { it.isNotBlank() }
                            ?: "watch_${UUID.randomUUID()}"
                        val recordedAt = map.getLong(WearDataPaths.KEY_TIMESTAMP, now)
                        WatchHeartbeatRegistry.recordHeartbeatAck(
                            nodeId = hostNodeId,
                            watchName = "Samsung Galaxy Watch",
                            ackMs = now
                        )
                        handleWatchNodeReachable(
                            nodeId = hostNodeId,
                            displayName = "Samsung Galaxy Watch",
                            ackMs = now
                        )
                        pendingBatches.add(
                            PendingStepBatchEntity(
                                batchId = batchId,
                                userId = activeUid,
                                source = StepSource.GALAXY_WATCH.name,
                                stepDelta = steps,
                                recordedAtMs = recordedAt,
                                synced = false
                            )
                        )
                    }
                }

                WearDataPaths.SLEEP_STATUS -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val sleeping = map.getBoolean(WearDataPaths.KEY_IS_SLEEPING, false)
                    val watchName = map.getString(WearDataPaths.KEY_WATCH_NAME)
                        ?.takeIf { it.isNotBlank() }
                        ?: "Samsung Galaxy Watch"
                    serviceScope.launch {
                        preferences.setWatchLiveSleeping(sleeping, watchName, now)
                        com.example.widget.SolstrideWidgetSnapshotStore.updateHeroSleepingOnly(
                            context = applicationContext,
                            isSleeping = sleeping,
                            sleepSourceLabel = watchName
                        )
                        com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(applicationContext)
                        Log.d(TAG, "Watch sleep signal received via DataLayer: sleeping=$sleeping ($watchName)")
                    }
                }
            }
        }

        if (watchDisconnectedViaDataEvent) {
            handleWatchNodeUnreachable(
                nodeId = disconnectedNodeId,
                displayName = disconnectedWatchName
            )
        }

        if (pendingBatches.isNotEmpty()) {
            serviceScope.launch {
                preferences.setLastWatchStepReceivedMs(now)
                pendingBatches.forEach { dao.insertBatch(it) }
                Log.d(TAG, "Queued ${pendingBatches.size} step batch(es) from Galaxy Watch")
                StepBackgroundSyncScheduler.enqueueImmediateSync(applicationContext)
                com.example.widget.WidgetAutoSyncScheduler.triggerImmediateSync(applicationContext)
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val reachableNode = capabilityInfo.nodes.firstOrNull { it.isNearby }
        if (reachableNode == null) {
            handleWatchNodeUnreachable(nodeId = null, displayName = capabilityInfo.name)
        } else {
            // Capability detected a nearby node: probe via heartbeat ping before declaring reachable
            serviceScope.launch {
                val pingDelivered = runCatching {
                    Wearable.getMessageClient(applicationContext).sendMessage(
                        reachableNode.id,
                        WearDataPaths.HEARTBEAT_PING,
                        System.currentTimeMillis().toString().toByteArray(Charsets.UTF_8)
                    ).await()
                    true
                }.getOrDefault(false)

                if (!pingDelivered) {
                    handleWatchNodeUnreachable(
                        nodeId = reachableNode.id,
                        displayName = reachableNode.displayName
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        if (peer.isNearby) {
            handleWatchNodeReachable(
                nodeId = peer.id,
                displayName = peer.displayName.ifBlank { "Samsung Galaxy Watch" },
                ackMs = System.currentTimeMillis()
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onPeerDisconnected(peer: Node) {
        super.onPeerDisconnected(peer)
        handleWatchNodeUnreachable(nodeId = peer.id, displayName = peer.displayName)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
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
                handleWatchNodeReachable(
                    nodeId = messageEvent.sourceNodeId,
                    displayName = watchName,
                    ackMs = now
                )
            }

            WearDataPaths.WATCH_DISCONNECTED -> {
                handleWatchNodeUnreachable(
                    nodeId = messageEvent.sourceNodeId,
                    displayName = null
                )
            }

            WearDataPaths.STEPS_BATCH -> {
                val rawSteps = String(messageEvent.data, Charsets.UTF_8).trim().toLongOrNull() ?: return
                if (rawSteps <= 0L) return
                WatchHeartbeatRegistry.recordHeartbeatAck(
                    nodeId = messageEvent.sourceNodeId,
                    watchName = "Samsung Galaxy Watch",
                    ackMs = now
                )
                handleWatchNodeReachable(
                    nodeId = messageEvent.sourceNodeId,
                    displayName = "Samsung Galaxy Watch",
                    ackMs = now
                )
                val dao = SolstrideLocalDatabase.getInstance(applicationContext).offlineSyncDao()
                val preferences = SolstridePreferences(applicationContext)
                val activeUid = com.example.data.firebase.safeCurrentUser(applicationContext)?.uid ?: "__WATCH_UNBOUND__"
                serviceScope.launch {
                    preferences.setLastWatchStepReceivedMs(now)
                    dao.insertBatch(
                        PendingStepBatchEntity(
                            batchId = "watch_msg_${now}_${UUID.randomUUID().toString().take(6)}",
                            userId = activeUid,
                            source = StepSource.GALAXY_WATCH.name,
                            stepDelta = rawSteps,
                            recordedAtMs = now,
                            synced = false
                        )
                    )
                    StepBackgroundSyncScheduler.enqueueImmediateSync(applicationContext)
                    com.example.widget.WidgetAutoSyncScheduler.triggerImmediateSync(applicationContext)
                }
            }

            WearDataPaths.SLEEP_STATUS -> {
                val payload = runCatching { String(messageEvent.data, Charsets.UTF_8).trim().lowercase() }.getOrDefault("")
                val sleeping = payload == "1" || payload == "true" || payload == "sleeping" || payload == "asleep"
                val preferences = SolstridePreferences(applicationContext)
                serviceScope.launch {
                    preferences.setWatchLiveSleeping(sleeping, "Samsung Galaxy Watch", now)
                    com.example.widget.SolstrideWidgetSnapshotStore.updateHeroSleepingOnly(
                        context = applicationContext,
                        isSleeping = sleeping,
                        sleepSourceLabel = "Samsung Galaxy Watch"
                    )
                    com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(applicationContext)
                    Log.d(TAG, "Watch sleep message received: sleeping=$sleeping")
                }
            }
        }
    }

    private fun handleWatchNodeReachable(nodeId: String?, displayName: String?, ackMs: Long) {
        serviceScope.launch {
            val preferences = SolstridePreferences(applicationContext)
            preferences.setLastWatchStepReceivedMs(ackMs)
            WatchConnectionBroadcast.sendToggleLocalStepSensorBroadcast(
                context = applicationContext,
                watchConnected = true,
                nodeId = nodeId,
                watchName = displayName,
                heartbeatAckMs = ackMs
            )
            com.example.widget.SolstrideWidgetSnapshotStore.updateWatchConnectionOnly(
                context = applicationContext,
                isWatchConnected = true
            )
            com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(applicationContext)
            Log.d(TAG, "Watch verified reachable ($displayName): pausing phone pedometer & syncing widgets")
        }
    }

    private fun handleWatchNodeUnreachable(nodeId: String?, displayName: String?) {
        serviceScope.launch {
            val liveDetected = WatchConnectionDetector.detectConnectedWatch(
                context = applicationContext,
                suppressWearNodeCache = true
            )
            if (!liveDetected.connected) {
                WatchHeartbeatRegistry.clear()
                val preferences = SolstridePreferences(applicationContext)
                preferences.setLastWatchStepReceivedMs(0L)
                preferences.setWatchLiveSleeping(false)
                preferences.setPedometerEnabled(true)
                StepBackgroundSyncScheduler.ensureScheduled(applicationContext)
                WatchConnectionBroadcast.sendToggleLocalStepSensorBroadcast(
                    context = applicationContext,
                    watchConnected = false,
                    nodeId = nodeId,
                    watchName = displayName
                )
                com.example.widget.SolstrideWidgetSnapshotStore.updateWatchConnectionOnly(
                    context = applicationContext,
                    isWatchConnected = false
                )
                com.example.widget.SolstrideWidgetSnapshotStore.updateHeroSleepingOnly(
                    context = applicationContext,
                    isSleeping = false,
                    sleepSourceLabel = ""
                )
                com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(applicationContext)
                Log.d(TAG, "Watch unreachable ($displayName): broadcast sent to activate phone pedometer & clear sleep state")
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            if (::dataClient.isInitialized) {
                dataClient.removeListener(this)
            }
        }
        runCatching {
            if (::capabilityClient.isInitialized) {
                capabilityClient.removeListener(
                    this,
                    WearDataPaths.CAPABILITY_SOLSTRIDE_WATCH_STEP_TRACKER
                )
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SolstrideWearService"
    }
}
