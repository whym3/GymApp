package com.example.gymapp

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Small haptics helper. Fails soft on devices without a vibrator. Amplitude
 * values are honoured where supported; otherwise the OS falls back to default
 * strength, so durations are tuned to still feel light vs. firm.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    /** Firm single buzz — a workout has started. */
    fun workoutStart(context: Context) {
        play(context, VibrationEffect.createOneShot(140, 255))
    }

    /** Light tap — a set/rep was completed. */
    fun repComplete(context: Context) {
        play(context, VibrationEffect.createOneShot(25, 80))
    }

    /** Very short tick — a toggle/selection was confirmed. */
    fun tick(context: Context) {
        play(context, VibrationEffect.createOneShot(12, 60))
    }

    /** Two very short pulses — the workout is complete. */
    fun workoutComplete(context: Context) {
        // Timing-only waveform (no amplitude dependency): off, on, off, on.
        play(context, VibrationEffect.createWaveform(longArrayOf(0, 45, 90, 45), -1))
    }

    /** Rising triple pulse — a new personal record. */
    fun prCelebration(context: Context) {
        play(
            context,
            VibrationEffect.createWaveform(
                longArrayOf(0, 30, 70, 40, 70, 70),
                intArrayOf(0, 110, 0, 180, 0, 255),
                -1,
            ),
        )
    }

    private fun play(context: Context, effect: VibrationEffect) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect) }
    }
}
