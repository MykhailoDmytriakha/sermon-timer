package com.example.sermontimer.util

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import android.util.Log
import com.example.sermontimer.domain.model.Segment

/**
 * Utility for providing haptic feedback patterns for timer events.
 * Follows AGENTS.md §10 haptic patterns specification.
 */
class HapticPatterns(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: context.getSystemService<Vibrator>()!!
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val feedbackAttributes: VibrationAttributes by lazy {
        VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
            .build()
    }

    private val alarmAttributes: VibrationAttributes by lazy {
        VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_ALARM)
            .build()
    }

    fun playBoundaryPattern(segment: Segment) {
        if (!vibrator.hasVibrator()) return

        val pattern = when (segment) {
            Segment.INTRO -> SHORT_DOUBLE_PATTERN
            Segment.MAIN -> TRIPLE_LONG_PATTERN
            Segment.OUTRO -> LONG_SHORT_LONG_PATTERN
            Segment.DONE -> LONG_SHORT_LONG_PATTERN
        }

        val effect = VibrationEffect.createWaveform(pattern.first, pattern.second, -1)
        vibrator.vibrate(effect, feedbackAttributes)
    }

    fun playCompletionPattern() {
        if (!vibrator.hasVibrator()) return

        val effect = VibrationEffect.createWaveform(LONG_SHORT_LONG_PATTERN.first, LONG_SHORT_LONG_PATTERN.second, -1)
        vibrator.vibrate(effect, feedbackAttributes)
    }

    /**
     * Start countdown haptics as a single waveform of N pulses (N in 1..10).
     * This avoids per-second scheduling and keeps working with screen off/Doze.
     * @param remainingSeconds 1..10 seconds to play
     */
    fun startCountdownVibration(remainingSeconds: Int) {
        if (!vibrator.hasVibrator() || remainingSeconds !in 1..10) {
            if (Log.isLoggable("HAPTIC", Log.DEBUG)) {
                Log.d("HAPTIC", "startCountdownVibration: skipped - hasVibrator=${vibrator.hasVibrator()}, remainingSeconds=$remainingSeconds")
            }
            return
        }
        // Stop any ongoing effect
        stopCountdownVibration()

        val (timings, amplitudes) = buildCountdownWaveform(remainingSeconds)
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        if (Log.isLoggable("HAPTIC", Log.DEBUG)) {
            Log.d("HAPTIC", "VIBRATION: starting countdown waveform with ${remainingSeconds} pulses")
        }
        vibrator.vibrate(effect, alarmAttributes)
    }

    /**
     * Stops the continuous countdown vibration.
     */
    fun stopCountdownVibration() {
        if (Log.isLoggable("HAPTIC", Log.DEBUG)) {
            Log.d("HAPTIC", "stopCountdownVibration: stopping countdown vibration")
        }
        // Cancel any ongoing vibration
        vibrator.cancel()
    }

    companion object {
        // Pattern definitions: Pair<timing array, amplitude array>
        // Timings: milliseconds for on/off cycles
        // Amplitudes: vibration strength (0-255), -1 for default

        // Short double: two quick vibrations
        private val SHORT_DOUBLE_PATTERN = Pair(
            longArrayOf(0, 100, 100, 100), // wait 0ms, vibrate 100ms, wait 100ms, vibrate 100ms
            intArrayOf(0, 150, 0, 150)     // amplitude for each segment
        )

        // Triple longer: three longer vibrations
        private val TRIPLE_LONG_PATTERN = Pair(
            longArrayOf(0, 200, 100, 200, 100, 200), // wait 0ms, vibrate 200ms, wait 100ms, vibrate 200ms, wait 100ms, vibrate 200ms
            intArrayOf(0, 200, 0, 200, 0, 200)       // amplitude for each segment
        )

        // Long-short-long: final completion pattern
        private val LONG_SHORT_LONG_PATTERN = Pair(
            longArrayOf(0, 300, 150, 150, 150, 300), // wait 0ms, vibrate 300ms, wait 150ms, vibrate 150ms, wait 150ms, vibrate 300ms
            intArrayOf(0, 255, 0, 180, 0, 255)       // amplitude for each segment
        )

        private fun buildCountdownWaveform(seconds: Int): Pair<LongArray, IntArray> {
            // Pattern: immediate start, then repeat [500ms on, 500ms off] seconds times
            val pulses = seconds.coerceIn(1, 10)
            val steps = pulses * 2 + 1 // initial 0ms wait + on/off pairs
            val timings = LongArray(steps)
            val amplitudes = IntArray(steps)
            timings[0] = 0L
            amplitudes[0] = 0
            var idx = 1
            repeat(pulses) {
                timings[idx] = 500L; amplitudes[idx] = 200; idx++  // on
                timings[idx] = 500L; amplitudes[idx] = 0;   idx++  // off
            }
            return Pair(timings, amplitudes)
        }
    }
}
