package com.example.wear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class DetectedWatchConnection(
    val connected: Boolean,
    val displayName: String,
    val identifier: String? = null,
    val transport: String = "None",
    val bluetoothEnabled: Boolean = true,
    val bluetoothPermissionGranted: Boolean = true
)

/**
 * Classifies Bluetooth & Audio devices to identify smartwatches/fitness wearables
 * (Samsung Galaxy Watch, Pixel Watch, Garmin, Fitbit, Amazfit, Wear OS, etc.)
 * while strictly ignoring earbuds, headphones, speakers, car stereos, and peripherals.
 */
object WatchDeviceClassifier {

    private val excludedKeywords = listOf(
        "buds", "earbud", "airpod", "headphone", "headset", "earphone",
        "speaker", "soundbar", "handsfree", "hands-free", "uconnect",
        "carplay", "android auto", "toyota", "honda", "ford", "chevy",
        "chevrolet", "bmw", "audi", "mercedes", "tesla", "subaru",
        "nissan", "mazda", "hyundai", "kia ", "jeep", "volkswagen",
        "lexus", "volvo", "bose", "jbl", "beats", "sony wh", "sony wf",
        "sennheiser", "anker", "soundcore", "skullcandy", "jabra",
        "marshall", "sonos", "keyboard", "mouse", "gamepad", "xbox",
        "dualshock", "dualsense", "roku", "chromecast", "printer",
        "stylus", "s pen", "car audio", "car kit", "my car", "stereo"
    )

    private val watchKeywords = listOf(
        "watch",
        "galaxy fit",
        "galaxy ring",
        "gear s",
        "gear live",
        "gear fit",
        "gear sport",
        "wear os",
        "sm-r8",
        "sm-r9",
        "sm-l3",
        "sm-l7",
        "garmin",
        "forerunner",
        "fenix",
        "epix",
        "venu",
        "vivoactive",
        "instinct",
        "enduro",
        "fitbit",
        "versa",
        "sense ",
        "charge 4",
        "charge 5",
        "charge 6",
        "inspire ",
        "amazfit",
        "ticwatch",
        "suunto",
        "coros",
        "polar ",
        "whoop",
        "mi band",
        "smart band",
        "huawei gt",
        "scanwatch",
        "withings",
        "pebble",
        "fossil gen",
        "mobvoi"
    )

    fun isSmartWatchName(rawName: String?): Boolean {
        val name = rawName?.trim()?.lowercase(Locale.US) ?: return false
        if (name.isBlank()) return false
        if (excludedKeywords.any { name.contains(it) }) return false
        return watchKeywords.any { name.contains(it) }
    }

    @SuppressLint("MissingPermission")
    fun isSmartWatchDevice(device: BluetoothDevice, hasPermission: Boolean): Boolean {
        if (!hasPermission) return false
        val name = runCatching { device.name }.getOrNull()
        val normalizedName = name?.trim()?.lowercase(Locale.US).orEmpty()
        if (normalizedName.isNotBlank() && excludedKeywords.any { normalizedName.contains(it) }) {
            return false
        }
        if (isSmartWatchName(name)) {
            return true
        }
        val btClass = runCatching { device.bluetoothClass }.getOrNull() ?: return false
        if (btClass.deviceClass == BluetoothClass.Device.WEARABLE_WRIST_WATCH) {
            return true
        }
        if (btClass.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE) {
            return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun safeDeviceName(device: BluetoothDevice, hasPermission: Boolean): String {
        if (!hasPermission) return "Smart Watch"
        val raw = runCatching { device.name }.getOrNull()?.trim()
        return if (!raw.isNullOrBlank()) raw else "Smart Watch"
    }
}

/**
 * Multi-layer live watch reachability detector for Android:
 *  1. Bluetooth adapter power state (if Bluetooth is OFF, no Bluetooth watch is connected).
 *  2. Live Bluetooth GATT, GATT_SERVER, and HFP Headset connected profiles (requires BLUETOOTH_CONNECT on API 31+).
 *  3. Live ACL-connected smartwatch map tracked from ACTION_ACL_CONNECTED / ACTION_ACL_DISCONNECTED.
 *  4. Zero-permission AudioManager live Bluetooth SCO / BLE Headset check (catches Galaxy Watch Call Audio immediately).
 *  5. Zero-permission Wear OS NodeClient.connectedNodes filtered strictly to `isNearby == true`.
 *
 * Never relies on stale bondedDevices alone and never fakes live connection from historical Health Connect records.
 */
object WatchConnectionDetector {

    private const val TAG = "WatchConnDetector"
    private const val WEAR_NODE_TIMEOUT_MS = 1_500L

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return false
        val adapter: BluetoothAdapter = manager.adapter ?: return false
        return runCatching { adapter.isEnabled }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun findConnectedBluetoothWatch(
        context: Context,
        headsetProxy: BluetoothHeadset? = null,
        activeAclWatches: Map<String, String> = emptyMap(),
        ignoredAddresses: Set<String> = emptySet()
    ): DetectedWatchConnection? {
        val hasPerm = hasBluetoothConnectPermission(context)
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        val btEnabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

        if (adapter != null && !btEnabled) {
            return null
        }

        if (hasPerm && manager != null) {
            // 1. Live BLE GATT connected watches (Galaxy Watch, Wear OS, Garmin, Fitbit, etc.)
            val gattDevices = runCatching {
                manager.getConnectedDevices(BluetoothProfile.GATT)
            }.getOrDefault(emptyList())
            val gattWatch = gattDevices.firstOrNull {
                it.address !in ignoredAddresses && WatchDeviceClassifier.isSmartWatchDevice(it, true)
            }
            if (gattWatch != null) {
                return DetectedWatchConnection(
                    connected = true,
                    displayName = WatchDeviceClassifier.safeDeviceName(gattWatch, true),
                    identifier = gattWatch.address,
                    transport = "Bluetooth GATT",
                    bluetoothEnabled = true,
                    bluetoothPermissionGranted = true
                )
            }

            // 2. Live BLE GATT Server connected watches (Galaxy Wearable / Samsung Accessory Service)
            val gattServerDevices = runCatching {
                manager.getConnectedDevices(BluetoothProfile.GATT_SERVER)
            }.getOrDefault(emptyList())
            val gattServerWatch = gattServerDevices.firstOrNull {
                it.address !in ignoredAddresses && WatchDeviceClassifier.isSmartWatchDevice(it, true)
            }
            if (gattServerWatch != null) {
                return DetectedWatchConnection(
                    connected = true,
                    displayName = WatchDeviceClassifier.safeDeviceName(gattServerWatch, true),
                    identifier = gattServerWatch.address,
                    transport = "Bluetooth BLE Link",
                    bluetoothEnabled = true,
                    bluetoothPermissionGranted = true
                )
            }

            // 3. Live Bluetooth HFP (Headset/Calls) connected watch (Galaxy Watch classic BT link)
            val hfpDevices = runCatching {
                headsetProxy?.connectedDevices.orEmpty()
            }.getOrDefault(emptyList())
            val hfpWatch = hfpDevices.firstOrNull {
                it.address !in ignoredAddresses && WatchDeviceClassifier.isSmartWatchDevice(it, true)
            }
            if (hfpWatch != null) {
                return DetectedWatchConnection(
                    connected = true,
                    displayName = WatchDeviceClassifier.safeDeviceName(hfpWatch, true),
                    identifier = hfpWatch.address,
                    transport = "Bluetooth Call Link",
                    bluetoothEnabled = true,
                    bluetoothPermissionGranted = true
                )
            }

            // 4. Active ACL-connected watch tracked in real time from ACTION_ACL_CONNECTED
            val aclEntry = activeAclWatches.entries.firstOrNull { it.key !in ignoredAddresses }
            if (aclEntry != null) {
                return DetectedWatchConnection(
                    connected = true,
                    displayName = aclEntry.value.ifBlank { "Smart Watch" },
                    identifier = aclEntry.key,
                    transport = "Bluetooth Live Link",
                    bluetoothEnabled = true,
                    bluetoothPermissionGranted = true
                )
            }
        }

        // 5. Zero-permission live AudioManager Bluetooth SCO / BLE Headset watch check
        if (ignoredAddresses.isEmpty()) {
            val audioWatch = findConnectedAudioManagerWatch(context)
            if (audioWatch != null) {
                return audioWatch.copy(
                    bluetoothEnabled = btEnabled,
                    bluetoothPermissionGranted = hasPerm
                )
            }
        }

        return null
    }

    fun findConnectedAudioManagerWatch(context: Context): DetectedWatchConnection? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return null
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())

        for (info in devices) {
            val isBluetoothAudio = info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    info.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            if (!isBluetoothAudio) continue

            val productName = info.productName?.toString()?.trim().orEmpty()
            if (WatchDeviceClassifier.isSmartWatchName(productName)) {
                return DetectedWatchConnection(
                    connected = true,
                    displayName = productName,
                    identifier = "audio_${info.id}",
                    transport = "Bluetooth Audio Link"
                )
            }
        }
        return null
    }

    /**
     * Full live check across Bluetooth profiles, AudioManager, and Wear OS NodeClient (isNearby == true).
     * Used by both GalaxyWatchBridge and StepBackgroundSyncWorker.
     */
    suspend fun detectConnectedWatch(
        context: Context,
        headsetProxy: BluetoothHeadset? = null,
        activeAclWatches: Map<String, String> = emptyMap(),
        ignoredAddresses: Set<String> = emptySet(),
        suppressWearNodeCache: Boolean = false
    ): DetectedWatchConnection {
        val appContext = context.applicationContext
        val hasBtPerm = hasBluetoothConnectPermission(appContext)
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        val btEnabled = if (adapter == null) true else runCatching { adapter.isEnabled }.getOrDefault(false)

        // If the phone's Bluetooth radio is physically OFF, no Bluetooth watch is connected.
        if (adapter != null && !btEnabled) {
            return DetectedWatchConnection(
                connected = false,
                displayName = "Bluetooth Off — Phone Pedometer Active",
                transport = "Bluetooth Off",
                bluetoothEnabled = false,
                bluetoothPermissionGranted = hasBtPerm
            )
        }

        // 1. Live Bluetooth profiles + ACL + AudioManager (highest immediacy & accuracy)
        val btWatch = findConnectedBluetoothWatch(
            context = appContext,
            headsetProxy = headsetProxy,
            activeAclWatches = activeAclWatches,
            ignoredAddresses = ignoredAddresses
        )
        if (btWatch != null) {
            return btWatch
        }

        // 2. Wear OS NodeClient nearby check (works for Galaxy Watch 4/5/6/7/Ultra & Pixel Watch
        //    without needing a custom watch APK or BLUETOOTH_CONNECT permission).
        //    Suppressed briefly after an explicit ACL/Audio disconnect event so stale Play Services
        //    cache cannot flip the UI back to connected while tearing down.
        if (!suppressWearNodeCache) {
            val nearbyWearNode = withTimeoutOrNull(WEAR_NODE_TIMEOUT_MS) {
                runCatching {
                    Wearable.getNodeClient(appContext).connectedNodes.await()
                        .firstOrNull { it.isNearby }
                }.onFailure { err ->
                    Log.d(TAG, "Wear NodeClient check skipped: ${err.message}")
                }.getOrNull()
            }
            if (nearbyWearNode != null) {
                return DetectedWatchConnection(
                    connected = true,
                    displayName = nearbyWearNode.displayName.ifBlank { "Wear OS Watch" },
                    identifier = nearbyWearNode.id,
                    transport = "Wear OS Nearby",
                    bluetoothEnabled = btEnabled,
                    bluetoothPermissionGranted = hasBtPerm
                )
            }
        }

        return DetectedWatchConnection(
            connected = false,
            displayName = "No Watch Connected (Phone Pedometer Active)",
            transport = "None",
            bluetoothEnabled = btEnabled,
            bluetoothPermissionGranted = hasBtPerm
        )
    }
}
