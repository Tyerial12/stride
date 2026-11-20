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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

enum class StepSensorStatus {
    TRACKING_ACTIVE,
    STANDBY_WATCH_CONNECTED,
    PERMISSION_NEEDED,
    SENSOR_UNAVAILABLE,
    PAUSED
}

data class StepTrackerTelemetry(
    val status: StepSensorStatus = StepSensorStatus.PAUSED,
    val sensorName: String = "Hardware Pedometer",
    val sessionSteps: Long = 0L,
    val cadenceSpm: Int = 0,
    val lastStepAtMs: Long = 0L,
    val batchingActive: Boolean = false
)

/**
 * Real on-device Android pedometer backed by hardware SensorManager:
 *  1. Primary: Sensor.TYPE_STEP_COUNTER (low-power cumulative hardware counter with persisted baseline)
 *  2. Secondary: Sensor.TYPE_STEP_DETECTOR (instant per-step hardware trigger)
 *  3. Fallback: Sensor.TYPE_ACCELEROMETER (filtered walking peak detector on devices without step co-processor)
 */
class StepTrackerManager(
    context: Context,
    private val onBaselineUpdated: (Long) -> Unit = {},
    private val onStepDelta: (Long) -> Unit
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _telemetry = MutableStateFlow(StepTrackerTelemetry())
    val telemetry: StateFlow<StepTrackerTelemetry> = _telemetry.asStateFlow()

    private var activeSensor: Sensor? = null
    private var isRegistered: Boolean = false
    private var lastHardwareBaseline: Long = -1L
    private var sessionSteps: Long = 0L
    private var lastBatchingEnabled: Boolean = true

    // Rolling timestamps (ms) of recent individual steps for live cadence (steps/min) calculation
    private val recentStepTimestamps = ArrayDeque<Long>()

    // Accelerometer fallback high-pass & peak detection state
    private var gravityEstimate = SensorManager.GRAVITY_EARTH
    private var lastAccelStepMs = 0L
    private var abovePeakThreshold = false

    fun setPersistedBaseline(savedBaseline: Long, force: Boolean = false) {
        if (savedBaseline >= 0L && (force || lastHardwareBaseline < 0L)) {
            lastHardwareBaseline = savedBaseline
        }
    }

    fun hasActivityPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startTracking(batterySaverBatching: Boolean) {
        lastBatchingEnabled = batterySaverBatching
        val mgr = sensorManager
        if (mgr == null) {
            _telemetry.value = _telemetry.value.copy(
                status = StepSensorStatus.SENSOR_UNAVAILABLE,
                sensorName = "Sensor Service Unavailable",
                batchingActive = false
            )
            return
        }

        val stepCounter = mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val stepDetector = mgr.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accelSensor = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Android 10+ requires ACTIVITY_RECOGNITION for dedicated step sensors
        if ((stepCounter != null || stepDetector != null) && !hasActivityPermission()) {
            stopListenerOnly()
            _telemetry.value = _telemetry.value.copy(
                status = StepSensorStatus.PERMISSION_NEEDED,
                sensorName = stepCounter?.name ?: stepDetector?.name ?: "Phone Step Sensor",
                batchingActive = batterySaverBatching
            )
            return
        }

        val chosenSensor = stepCounter ?: stepDetector ?: accelSensor
        if (chosenSensor == null) {
            stopListenerOnly()
            _telemetry.value = _telemetry.value.copy(
                status = StepSensorStatus.SENSOR_UNAVAILABLE,
                sensorName = "No Motion Sensor Detected",
                batchingActive = false
            )
            return
        }

        stopListenerOnly()

        val samplingPeriodUs = when (chosenSensor.type) {
            Sensor.TYPE_ACCELEROMETER -> SensorManager.SENSOR_DELAY_GAME
            else -> SensorManager.SENSOR_DELAY_UI
        }
        // Keep FIFO latency responsive (2s when battery saver enabled, 0 for immediate live pedometer)
        val maxReportLatencyUs = when {
            chosenSensor.type == Sensor.TYPE_ACCELEROMETER -> 0
            batterySaverBatching -> 2_000_000
            else -> 0
        }

        val registered = try {
            mgr.registerListener(this, chosenSensor, samplingPeriodUs, maxReportLatencyUs)
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing ACTIVITY_RECOGNITION permission when registering step sensor", se)
            _telemetry.value = _telemetry.value.copy(
                status = StepSensorStatus.PERMISSION_NEEDED,
                sensorName = chosenSensor.name ?: "Phone Step Sensor",
                batchingActive = batterySaverBatching
            )
            return
        }

        if (registered) {
            activeSensor = chosenSensor
            isRegistered = true
            val label = when (chosenSensor.type) {
                Sensor.TYPE_STEP_COUNTER -> "Hardware Step Counter"
                Sensor.TYPE_STEP_DETECTOR -> "Hardware Step Detector"
                else -> "Accelerometer Pedometer"
            }
            _telemetry.value = _telemetry.value.copy(
                status = StepSensorStatus.TRACKING_ACTIVE,
                sensorName = label,
                sessionSteps = sessionSteps,
                batchingActive = batterySaverBatching && chosenSensor.type != Sensor.TYPE_ACCELEROMETER
            )
        } else {
            _telemetry.value = _telemetry.value.copy(
                status = StepSensorStatus.SENSOR_UNAVAILABLE,
                sensorName = "Unable to Start Step Sensor",
                batchingActive = false
            )
        }
    }

    fun pauseForWatch(watchName: String) {
        stopListenerOnly()
        lastHardwareBaseline = -1L
        recentStepTimestamps.clear()
        _telemetry.value = _telemetry.value.copy(
            status = StepSensorStatus.STANDBY_WATCH_CONNECTED,
            sensorName = "Standby — $watchName Connected",
            cadenceSpm = 0,
            batchingActive = false
        )
    }

    fun clearBaseline() {
        lastHardwareBaseline = -1L
    }

    fun stopTracking() {
        stopListenerOnly()
        val current = _telemetry.value
        if (current.status == StepSensorStatus.TRACKING_ACTIVE) {
            _telemetry.value = current.copy(
                status = StepSensorStatus.PAUSED,
                cadenceSpm = 0
            )
        }
    }

    fun flush() {
        if (isRegistered) {
            runCatching { sensorManager?.flush(this) }
        }
    }

    fun resetSession() {
        sessionSteps = 0L
        recentStepTimestamps.clear()
        _telemetry.value = _telemetry.value.copy(
            sessionSteps = 0L,
            cadenceSpm = 0
        )
    }

    fun latestBaseline(): Long = lastHardwareBaseline

    private fun stopListenerOnly() {
        if (isRegistered) {
            runCatching { sensorManager?.unregisterListener(this) }
            isRegistered = false
            activeSensor = null
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        val now = System.currentTimeMillis()
        when (sensorEvent.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val cumulative = sensorEvent.values.firstOrNull()?.toLong() ?: return
                if (cumulative < 0L) return

                val delta = when {
                    lastHardwareBaseline < 0L -> {
                        // First reading ever: anchor & persist baseline immediately so steps taken after closing the app count!
                        lastHardwareBaseline = cumulative
                        onBaselineUpdated(cumulative)
                        0L
                    }
                    cumulative < lastHardwareBaseline -> {
                        // Device rebooted since last reading: all steps since boot are new
                        lastHardwareBaseline = cumulative
                        onBaselineUpdated(cumulative)
                        cumulative.coerceAtMost(MAX_SINGLE_DELTA)
                    }
                    else -> {
                        val diff = cumulative - lastHardwareBaseline
                        if (diff > 0L) {
                            lastHardwareBaseline = cumulative
                            onBaselineUpdated(cumulative)
                        }
                        diff.coerceAtMost(MAX_SINGLE_DELTA)
                    }
                }
                if (delta > 0L) {
                    emitVerifiedSteps(delta, now)
                }
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                val detected = sensorEvent.values.firstOrNull() ?: 0f
                if (detected >= 1f) {
                    emitVerifiedSteps(1L, now)
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = sensorEvent.values.getOrNull(0) ?: return
                val y = sensorEvent.values.getOrNull(1) ?: return
                val z = sensorEvent.values.getOrNull(2) ?: return
                val magnitude = sqrt(x * x + y * y + z * z)
                // Low-pass filter tracks Earth gravity; linear delta isolates walking bounce
                gravityEstimate = 0.85f * gravityEstimate + 0.15f * magnitude
                val linearAccel = magnitude - gravityEstimate

                if (linearAccel > ACCEL_STEP_THRESHOLD && !abovePeakThreshold) {
                    abovePeakThreshold = true
                    if (now - lastAccelStepMs >= ACCEL_MIN_STEP_INTERVAL_MS) {
                        lastAccelStepMs = now
                        emitVerifiedSteps(1L, now)
                    }
                } else if (linearAccel < ACCEL_RESET_THRESHOLD) {
                    abovePeakThreshold = false
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun emitVerifiedSteps(delta: Long, nowMs: Long) {
        sessionSteps += delta

        // Track up to 40 recent step timestamps over a 15s sliding window for live cadence (steps/min)
        val stepsToRecord = delta.coerceAtMost(15L).toInt()
        repeat(stepsToRecord) {
            recentStepTimestamps.addLast(nowMs)
        }
        val windowCutoff = nowMs - CADENCE_WINDOW_MS
        while (recentStepTimestamps.isNotEmpty() && recentStepTimestamps.first() < windowCutoff) {
            recentStepTimestamps.removeFirst()
        }
        val cadenceSpm = if (recentStepTimestamps.size >= 2) {
            val spanMs = (nowMs - recentStepTimestamps.first()).coerceAtLeast(3_000L)
            ((recentStepTimestamps.size.toDouble() / spanMs.toDouble()) * 60_000.0)
                .toInt()
                .coerceIn(20, 220)
        } else {
            0
        }

        _telemetry.value = _telemetry.value.copy(
            status = StepSensorStatus.TRACKING_ACTIVE,
            sessionSteps = sessionSteps,
            cadenceSpm = cadenceSpm,
            lastStepAtMs = nowMs
        )
        onStepDelta(delta)
    }

    private companion object {
        const val TAG = "StepTrackerManager"
        const val MAX_SINGLE_DELTA = 25_000L
        const val CADENCE_WINDOW_MS = 15_000L
        const val ACCEL_STEP_THRESHOLD = 2.0f
        const val ACCEL_RESET_THRESHOLD = 0.8f
        const val ACCEL_MIN_STEP_INTERVAL_MS = 330L
    }
}
