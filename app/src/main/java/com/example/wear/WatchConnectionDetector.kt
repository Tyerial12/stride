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

    /**
     * Returns true when the connected smartwatch / wearable supports sleep tracking
     * (Samsung Galaxy Watch, Galaxy Fit, Galaxy Ring, Pixel Watch, Wear OS, Garmin, Fitbit,
     * Amazfit, Oura, Whoop, Polar, Suunto, Withings, etc.).
     */
    fun isSleepCapableWatch(rawName: String?): Boolean {
        val name = rawName?.trim()?.lowercase(Locale.US) ?: return false
        if (name.isBlank() || name == "no watch connected" || name == "none") return false
        if (excludedKeywords.any { name.contains(it) }) return false
        // Vintage pebble-only or basic pedometer clips without sleep sensors
        if (name.contains("basic pedometer") || name.contains("step clip")) return false
        return isSmartWatchName(name) ||
            name.contains("galaxy") ||
            name.contains("samsung") ||
            name.contains("smart watch") ||
            name.contains("smartwatch") ||
            name.contains("wearable")
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
            // 1. Live Bluetooth HFP (Headset/Calls) connected watch (Galaxy Watch / Wear OS active BT link).
            //    Unlike BLE GATT / GATT_SERVER (which stay registered in the background via autoConnect=true
            //    even when the watch is disconnected), BluetoothProfile.HEADSET only lists watches that are
            //    actively connected right now and drops immediately on disconnect.
            val hfpDevices = runCatching {
                headsetProxy?.connectedDevices.orEmpty()
            }.getOrDefault(emptyList())
            val hfpWatch = hfpDevices.firstOrNull { device ->
                device.address !in ignoredAddresses &&
                    WatchDeviceClassifier.isSmartWatchDevice(device, true) &&
                    runCatching {
                        headsetProxy?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
                    }.getOrDefault(true)
            }
            if (hfpWatch != null) {
                return DetectedWatchConnection(
                    connected = true,
                    displayName = WatchDeviceClassifier.safeDeviceName(hfpWatch, true),
                    identifier = hfpWatch.address,
                    transport = "Bluetooth Watch Link",
                    bluetoothEnabled = true,
                    bluetoothPermissionGranted = true
                )
            }
        }

        // 2. Zero-permission live AudioManager Bluetooth SCO / BLE Headset watch check.
        //    Drops immediately the instant the Galaxy Watch disconnects.
        val audioWatch = findConnectedAudioManagerWatch(context)
        if (audioWatch != null) {
            val isIgnored = ignoredAddresses.any { addr ->
                addr.equals(audioWatch.identifier, ignoreCase = true) ||
                    addr.equals(audioWatch.displayName, ignoreCase = true)
            }
            if (!isIgnored) {
                return audioWatch.copy(
                    bluetoothEnabled = btEnabled,
                    bluetoothPermissionGranted = hasPerm
                )
            }
        }

        // 3. Active ACL-connected watch tracked in real time from ACTION_ACL_CONNECTED
        //    (only when no HFP proxy is bound yet or for non-voice fitness wearables).
        if (hasPerm && manager != null && headsetProxy == null) {
            val aclEntry = activeAclWatches.entries.firstOrNull { (addr, _) ->
                addr !in ignoredAddresses
            }
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

        return null
    }

    fun findConnectedAudioManagerWatch(context: Context): DetectedWatchConnection? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return null
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        }.getOrDefault(emptyArray())

        for (info in devices) {
            val isBluetoothAudio = info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
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
     * Full live check across Bluetooth HFP Headset profile and AudioManager.
     * Used by both GalaxyWatchBridge and StepBackgroundSyncWorker.
     * Never uses persistent background BLE GATT/GATT_SERVER autoConnect registrations or stale
     * Play Services Wear OS NodeClient caches that stay true when the watch is disconnected.
     */
    @SuppressLint("MissingPermission")
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

        // Live Bluetooth HFP Headset + AudioManager + verified ACL state
        val btWatch = findConnectedBluetoothWatch(
            context = appContext,
            headsetProxy = headsetProxy,
            activeAclWatches = activeAclWatches,
            ignoredAddresses = ignoredAddresses
        )
        if (btWatch != null) {
            return btWatch
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
