package com.example.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class GameplayHaptic {
    ENCOUNTER_ALERT,
    PLAYER_STRIKE,
    SOLAR_CRITICAL,
    GUARD_BLOCK,
    DAMAGE_TAKEN,
    VICTORY_FANFARE,
    SYNC_CONFIRM
}

class HapticFeedbackManager(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    var enabled: Boolean = true

    fun trigger(cue: GameplayHaptic) {
        if (!enabled) return
        val vibrator = resolveVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        val (timings, amplitudes) = waveformFor(cue)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    private fun waveformFor(cue: GameplayHaptic): Pair<LongArray, IntArray> = when (cue) {
        GameplayHaptic.ENCOUNTER_ALERT ->
            longArrayOf(0L, 65L, 85L, 125L) to intArrayOf(0, 190, 0, 255)
        GameplayHaptic.PLAYER_STRIKE ->
            longArrayOf(0L, 40L) to intArrayOf(0, 210)
        GameplayHaptic.SOLAR_CRITICAL ->
            longArrayOf(0L, 35L, 45L, 90L) to intArrayOf(0, 200, 0, 255)
        GameplayHaptic.GUARD_BLOCK ->
            longArrayOf(0L, 28L, 35L, 28L) to intArrayOf(0, 130, 0, 150)
        GameplayHaptic.DAMAGE_TAKEN ->
            longArrayOf(0L, 55L, 55L, 70L) to intArrayOf(0, 220, 0, 180)
        GameplayHaptic.VICTORY_FANFARE ->
            longArrayOf(0L, 30L, 60L, 45L, 60L, 100L) to intArrayOf(0, 140, 0, 190, 0, 255)
        GameplayHaptic.SYNC_CONFIRM ->
            longArrayOf(0L, 22L) to intArrayOf(0, 120)
    }

    private fun resolveVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
