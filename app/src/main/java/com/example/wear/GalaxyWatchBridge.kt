package com.example.wear

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
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
    const val SLEEP_STATUS = "/solstride/sleep_status"
    const val KEY_STEPS_DELTA = "steps_delta"
    const val KEY_BATCH_ID = "batch_id"
    const val KEY_TIMESTAMP = "recorded_at_ms"
    const val KEY_WATCH_CONNECTED = "watch_connected"
    const val KEY_WATCH_NAME = "watch_name"
    const val KEY_IS_SLEEPING = "is_sleeping"
    const val KEY_SLEEP_MINUTES = "sleep_minutes"
}

/**
 * Shared in-process heartbeat registry used when a Wear OS companion watch app actively responds
 * to `/solstride/heartbeat_ping` with `/solstride/heartbeat_ack`.
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
    val connectionTransport: String = "None",
    val bluetoothPermissionGranted: Boolean = true,
    val bluetoothEnabled: Boolean = true,
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
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _linkStatus = MutableStateFlow(
        WatchLinkStatus(
            bluetoothPermissionGranted = WatchConnectionDetector.hasBluetoothConnectPermission(appContext),
            bluetoothEnabled = WatchConnectionDetector.isBluetoothEnabled(appContext)
        )
    )
    val linkStatus: StateFlow<WatchLinkStatus> = _linkStatus.asStateFlow()

    @Volatile
    private var wasWatchConnected = false

    @Volatile
    private var headsetProxy: BluetoothHeadset? = null

    @Volatile
    private var lastExplicitDisconnectMs: Long = 0L

    private var btReceiverRegistered = false
    private var audioCallbackRegistered = false

    // Live ACL-connected smartwatches (MAC address -> display name)
    private val activeAclWatches = ConcurrentHashMap<String, String>()
    // Briefly tracks addresses that just received ACTION_ACL_DISCONNECTED to avoid teardown flicker
    private val recentlyDisconnectedAddresses = ConcurrentHashMap<String, Long>()

    private val headsetServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET && proxy is BluetoothHeadset) {
                headsetProxy = proxy
                bridgeScope.launch { refreshWatchConnection() }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                headsetProxy = null
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val hasPerm = WatchConnectionDetector.hasBluetoothConnectPermission(appContext)
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        activeAclWatches.clear()
                        recentlyDisconnectedAddresses.clear()
                        WatchHeartbeatRegistry.clear()
                        lastExplicitDisconnectMs = System.currentTimeMillis()
                        bridgeScope.launch { refreshWatchConnection() }
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        lastExplicitDisconnectMs = 0L
                        recentlyDisconnectedAddresses.clear()
                        attachHeadsetProfileProxyIfPermitted()
                        bridgeScope.launch {
                            refreshWatchConnection()
                            delay(1_500L)
                            refreshWatchConnection()
                        }
                    }
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = extractBluetoothDevice(intent) ?: return
                    recentlyDisconnectedAddresses.remove(device.address)
                    if (hasPerm && WatchDeviceClassifier.isSmartWatchDevice(device, true)) {
                        val name = WatchDeviceClassifier.safeDeviceName(device, true)
                        activeAclWatches[device.address] = name
                        lastExplicitDisconnectMs = 0L
                        Log.d(TAG, "Bluetooth ACL watch CONNECTED: $name (${device.address})")
                        bridgeScope.launch {
                            refreshWatchConnection()
                            delay(1_500L)
                            refreshWatchConnection()
                        }
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = extractBluetoothDevice(intent)
                    val address = device?.address
                    val now = System.currentTimeMillis()
                    val wasTrackedWatch = address != null && activeAclWatches.remove(address) != null
                    val looksLikeWatch = device != null && hasPerm &&
                        WatchDeviceClassifier.isSmartWatchDevice(device, true)
                    val matchesCurrentId = address != null && address == _linkStatus.value.connectedNodeId

                    if (wasTrackedWatch || looksLikeWatch || matchesCurrentId || _linkStatus.value.connected) {
                        if (wasTrackedWatch || looksLikeWatch || matchesCurrentId) {
                            if (address != null) {
                                recentlyDisconnectedAddresses[address] = now
                            }
                            WatchHeartbeatRegistry.clear()
                            lastExplicitDisconnectMs = now
                            Log.d(TAG, "Bluetooth ACL watch DISCONNECTED: $address")
                        }
                        bridgeScope.launch {
                            refreshWatchConnection()
                            delay(1_500L)
                            refreshWatchConnection()
                        }
                    }
                }

                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED
                    )
                    val device = extractBluetoothDevice(intent)
                    if (device != null && hasPerm && WatchDeviceClassifier.isSmartWatchDevice(device, true)) {
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            recentlyDisconnectedAddresses.remove(device.address)
                            activeAclWatches[device.address] = WatchDeviceClassifier.safeDeviceName(device, true)
                            lastExplicitDisconnectMs = 0L
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            val now = System.currentTimeMillis()
                            activeAclWatches.remove(device.address)
                            recentlyDisconnectedAddresses[device.address] = now
                            WatchHeartbeatRegistry.clear()
                            lastExplicitDisconnectMs = now
                        }
                    }
                    bridgeScope.launch { refreshWatchConnection() }
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val watchAdded = addedDevices.orEmpty().any { info ->
                (info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        info.type == AudioDeviceInfo.TYPE_BLE_HEADSET)) &&
                    WatchDeviceClassifier.isSmartWatchName(info.productName?.toString())
            }
            if (watchAdded) {
                lastExplicitDisconnectMs = 0L
                Log.d(TAG, "AudioManager watch SCO/BLE device ADDED")
                bridgeScope.launch { refreshWatchConnection() }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val watchRemoved = removedDevices.orEmpty().any { info ->
                (info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        info.type == AudioDeviceInfo.TYPE_BLE_HEADSET)) &&
                    WatchDeviceClassifier.isSmartWatchName(info.productName?.toString())
            }
            if (watchRemoved) {
                WatchHeartbeatRegistry.clear()
                lastExplicitDisconnectMs = System.currentTimeMillis()
                Log.d(TAG, "AudioManager watch SCO/BLE device REMOVED")
                bridgeScope.launch { refreshWatchConnection() }
            }
        }
    }

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
                        lastExplicitDisconnectMs = 0L
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
                            lastExplicitDisconnectMs = 0L
                            ackReceived = true
                        }
                    }
                }
            }
        }
        if (disconnectDetected) {
            WatchHeartbeatRegistry.clear()
            lastExplicitDisconnectMs = now
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
            } else {
                lastExplicitDisconnectMs = 0L
            }
            bridgeScope.launch { refreshWatchConnection() }
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
                    lastExplicitDisconnectMs = 0L
                    bridgeScope.launch { refreshWatchConnection() }
                }
                WearDataPaths.WATCH_DISCONNECTED -> {
                    WatchHeartbeatRegistry.clear()
                    lastExplicitDisconnectMs = now
                    bridgeScope.launch { refreshWatchConnection() }
                }
            }
        }

    init {
        registerBluetoothMonitors()
        attachHeadsetProfileProxyIfPermitted()
        runCatching { dataClient.addListener(dataChangedListener) }
        runCatching {
            capabilityClient.addListener(
                capabilityChangedListener,
                WearDataPaths.CAPABILITY_SOLSTRIDE_WATCH_STEP_TRACKER
            )
        }
        runCatching { messageClient.addListener(messageReceivedListener) }
    }

    private fun registerBluetoothMonitors() {
        if (!btReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            runCatching {
                ContextCompat.registerReceiver(
                    appContext,
                    bluetoothReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                btReceiverRegistered = true
            }.onFailure { err ->
                Log.w(TAG, "Unable to register Bluetooth monitor receiver: ${err.message}")
            }
        }

        val am = audioManager
        if (!audioCallbackRegistered && am != null) {
            runCatching {
                am.registerAudioDeviceCallback(
                    audioDeviceCallback,
                    Handler(Looper.getMainLooper())
                )
                audioCallbackRegistered = true
            }.onFailure { err ->
                Log.w(TAG, "Unable to register AudioDeviceCallback: ${err.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun attachHeadsetProfileProxyIfPermitted() {
        if (headsetProxy != null) return
        if (!WatchConnectionDetector.hasBluetoothConnectPermission(appContext)) return
        val adapter = bluetoothManager?.adapter ?: return
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return
        runCatching {
            adapter.getProfileProxy(appContext, headsetServiceListener, BluetoothProfile.HEADSET)
        }.onFailure { err ->
            Log.d(TAG, "Headset profile proxy attach skipped: ${err.message}")
        }
    }

    fun onBluetoothPermissionUpdated() {
        attachHeadsetProfileProxyIfPermitted()
        lastExplicitDisconnectMs = 0L
        bridgeScope.launch { refreshWatchConnection() }
    }

    fun hasBluetoothPermission(): Boolean =
        WatchConnectionDetector.hasBluetoothConnectPermission(appContext)

    fun markWatchConnectionFromBroadcast(
        connected: Boolean,
        nodeId: String?,
        displayName: String?,
        heartbeatAckMs: Long = 0L
    ) {
        val now = System.currentTimeMillis()
        val hasPerm = WatchConnectionDetector.hasBluetoothConnectPermission(appContext)
        val btOn = WatchConnectionDetector.isBluetoothEnabled(appContext)
        if (connected) {
            if (heartbeatAckMs > 0L) {
                WatchHeartbeatRegistry.recordHeartbeatAck(nodeId, displayName, heartbeatAckMs)
            }
            wasWatchConnected = true
            _linkStatus.value = _linkStatus.value.copy(
                connected = true,
                watchDisplayName = displayName?.ifBlank { "Samsung Galaxy Watch" } ?: "Samsung Galaxy Watch",
                connectedNodeId = nodeId ?: _linkStatus.value.connectedNodeId,
                bluetoothPermissionGranted = hasPerm,
                bluetoothEnabled = btOn,
                lastCheckedMs = now,
                lastHeartbeatAckMs = if (heartbeatAckMs > 0L) heartbeatAckMs else _linkStatus.value.lastHeartbeatAckMs,
                lastError = null
            )
        } else {
            WatchHeartbeatRegistry.clear()
            wasWatchConnected = false
            _linkStatus.value = _linkStatus.value.copy(
                connected = false,
                watchDisplayName = if (!btOn) {
                    "Bluetooth Off — Phone Pedometer Active"
                } else {
                    "No Watch Connected (Phone Pedometer Active)"
                },
                connectedNodeId = null,
                connectionTransport = if (!btOn) "Bluetooth Off" else "None",
                bluetoothPermissionGranted = hasPerm,
                bluetoothEnabled = btOn,
                lastCheckedMs = now,
                lastHeartbeatAckMs = 0L,
                lastError = null
            )
        }
    }

    /**
     * Performs a live multi-layer watch connection check:
     *  1. Checks live Bluetooth profiles (GATT, GATT_SERVER, HFP Headset), active ACL link,
     *     zero-permission AudioManager SCO/BLE watch link, and Wear OS NodeClient (`isNearby == true`).
     *  2. If a Solstride Wear OS companion app is also installed (`CapabilityClient` nearby node),
     *     probes `/solstride/heartbeat_ping` to keep live DataClient step sync fresh.
     *  3. On any connect or disconnect transition, immediately fires
     *     `WatchConnectionBroadcast.ACTION_TOGGLE_LOCAL_STEP_SENSOR` so the phone's local step
     *     counter switches ON or OFF without delay.
     */
    suspend fun refreshWatchConnection(): WatchLinkStatus {
        val now = System.currentTimeMillis()
        attachHeadsetProfileProxyIfPermitted()
        return try {
            val suppressWearCache = lastExplicitDisconnectMs > 0L &&
                (now - lastExplicitDisconnectMs) < ACL_DISCONNECT_SUPPRESSION_MS

            // Prune expired disconnect-teardown guard entries (> 5s)
            recentlyDisconnectedAddresses.entries.removeIf { (_, ts) ->
                (now - ts) > PROFILE_TEARDOWN_GUARD_MS
            }
            val ignoredAddresses = recentlyDisconnectedAddresses.keys.toSet()

            // 1. Primary real-world detection: live Bluetooth GATT/HFP/ACL + AudioManager + Wear OS Nearby Node
            val detected = WatchConnectionDetector.detectConnectedWatch(
                context = appContext,
                headsetProxy = headsetProxy,
                activeAclWatches = activeAclWatches,
                ignoredAddresses = ignoredAddresses,
                suppressWearNodeCache = suppressWearCache
            )

            // 2. Optional companion app check via CapabilityClient + Heartbeat ACK
            var companionNodeId: String? = null
            var companionWatchName: String? = null
            var companionHeartbeatAlive = false
            if (detected.bluetoothEnabled) {
                val capabilityInfo = withTimeoutOrNull(CAPABILITY_QUERY_TIMEOUT_MS) {
                    runCatching {
                        capabilityClient.getCapability(
                            WearDataPaths.CAPABILITY_SOLSTRIDE_WATCH_STEP_TRACKER,
                            CapabilityClient.FILTER_REACHABLE
                        ).await()
                    }.getOrNull()
                }
                val capableNode = capabilityInfo?.nodes?.firstOrNull { it.isNearby }
                if (capableNode != null) {
                    val pingDelivered = withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                        runCatching {
                            messageClient.sendMessage(
                                capableNode.id,
                                WearDataPaths.HEARTBEAT_PING,
                                now.toString().toByteArray(Charsets.UTF_8)
                            ).await()
                            true
                        }.getOrDefault(false)
                    } ?: false

                    if (!pingDelivered) {
                        WatchHeartbeatRegistry.recordMissedPing()
                    }
                    if (WatchHeartbeatRegistry.isHeartbeatFresh(now)) {
                        companionHeartbeatAlive = true
                        companionNodeId = capableNode.id
                        companionWatchName = capableNode.displayName.ifBlank {
                            WatchHeartbeatRegistry.latestWatchName() ?: "Samsung Galaxy Watch"
                        }
                    }
                }
            }

            val isConnectedNow = detected.connected || companionHeartbeatAlive
            val resolvedName = when {
                detected.connected -> detected.displayName
                companionHeartbeatAlive -> companionWatchName ?: "Samsung Galaxy Watch"
                else -> detected.displayName
            }
            val resolvedId = companionNodeId ?: detected.identifier
            val resolvedTransport = when {
                companionHeartbeatAlive && detected.connected -> "${detected.transport} + Companion"
                companionHeartbeatAlive -> "Wear OS Companion"
                else -> detected.transport
            }

            val prevAutoSync = _linkStatus.value.lastAutoSyncMs
            val previousName = _linkStatus.value.watchDisplayName
            val previousNodeId = _linkStatus.value.connectedNodeId

            val updated = if (isConnectedNow) {
                val latestAck = WatchHeartbeatRegistry.latestAckMs()
                if (!wasWatchConnected) {
                    wasWatchConnected = true
                    WatchConnectionBroadcast.sendToggleLocalStepSensorBroadcast(
                        context = appContext,
                        watchConnected = true,
                        nodeId = resolvedId,
                        watchName = resolvedName,
                        heartbeatAckMs = latestAck
                    )
                }
                WatchLinkStatus(
                    connected = true,
                    watchDisplayName = resolvedName,
                    connectedNodeId = resolvedId,
                    connectionTransport = resolvedTransport,
                    bluetoothPermissionGranted = detected.bluetoothPermissionGranted,
                    bluetoothEnabled = detected.bluetoothEnabled,
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
                    watchDisplayName = resolvedName,
                    connectedNodeId = null,
                    connectionTransport = resolvedTransport,
                    bluetoothPermissionGranted = detected.bluetoothPermissionGranted,
                    bluetoothEnabled = detected.bluetoothEnabled,
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
            Log.w(TAG, "Watch reachability check failed: ${e.message}")
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
                connectionTransport = "None",
                bluetoothPermissionGranted = WatchConnectionDetector.hasBluetoothConnectPermission(appContext),
                bluetoothEnabled = WatchConnectionDetector.isBluetoothEnabled(appContext),
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
     * Pulls pending Wear OS DataClient step batches when a Wear OS companion watch node is active.
     */
    suspend fun pullPendingWatchSteps(): List<WatchStepPayload> {
        val now = System.currentTimeMillis()
        if (!_linkStatus.value.connected) {
            return emptyList()
        }
        val results = mutableListOf<WatchStepPayload>()
        try {
            val connectedNodeId = _linkStatus.value.connectedNodeId
            if (!connectedNodeId.isNullOrBlank() &&
                !connectedNodeId.contains(":") &&
                !connectedNodeId.startsWith("audio_")
            ) {
                runCatching {
                    withTimeoutOrNull(HEARTBEAT_PING_TIMEOUT_MS) {
                        messageClient.sendMessage(
                            connectedNodeId,
                            WearDataPaths.REQUEST_STEPS_SYNC,
                            byteArrayOf()
                        ).await()
                    }
                }
            }

            if (WatchHeartbeatRegistry.isHeartbeatFresh(now)) {
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
            }
            _linkStatus.value = _linkStatus.value.copy(lastAutoSyncMs = now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "5s watch step poll skipped: ${e.message}")
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
            Log.w(TAG, "Deferred watch HUD sync: ${e.message}")
            false
        }
    }

    suspend fun sendEncounterHapticToWatch(enemyName: String): Boolean {
        if (!_linkStatus.value.connected) return false
        val nodeId = _linkStatus.value.connectedNodeId ?: return false
        if (nodeId.contains(":") || nodeId.startsWith("audio_")) return false
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
            Log.w(TAG, "Watch encounter alert skipped: ${e.message}")
            false
        }
    }

    fun release() {
        if (btReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(bluetoothReceiver) }
            btReceiverRegistered = false
        }
        val am = audioManager
        if (audioCallbackRegistered && am != null) {
            runCatching { am.unregisterAudioDeviceCallback(audioDeviceCallback) }
            audioCallbackRegistered = false
        }
        headsetProxy?.let { proxy ->
            runCatching {
                bluetoothManager?.adapter?.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
            }
            headsetProxy = null
        }
        runCatching { dataClient.removeListener(dataChangedListener) }
        runCatching {
            capabilityClient.removeListener(
                capabilityChangedListener,
                WearDataPaths.CAPABILITY_SOLSTRIDE_WATCH_STEP_TRACKER
            )
        }
        runCatching { messageClient.removeListener(messageReceivedListener) }
        bridgeScope.cancel()
    }

    @Suppress("DEPRECATION")
    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private companion object {
        const val TAG = "GalaxyWatchBridge"
        const val CAPABILITY_QUERY_TIMEOUT_MS = 1_200L
        const val HEARTBEAT_PING_TIMEOUT_MS = 1_200L
        const val FRESH_STEP_ITEM_MAX_AGE_MS = 60_000L
        const val ACL_DISCONNECT_SUPPRESSION_MS = 25_000L
        const val PROFILE_TEARDOWN_GUARD_MS = 5_000L
    }
}
