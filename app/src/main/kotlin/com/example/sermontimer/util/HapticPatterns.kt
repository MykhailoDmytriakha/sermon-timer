package com.example.sermontimer.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
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

    fun playBoundaryPattern(segment: Segment) {
        if (!vibrator.hasVibrator()) return

        val pattern = when (segment) {
            Segment.INTRO -> SHORT_DOUBLE_PATTERN
            Segment.MAIN -> TRIPLE_LONG_PATTERN
            Segment.OUTRO -> LONG_SHORT_LONG_PATTERN
            Segment.DONE -> LONG_SHORT_LONG_PATTERN
        }

        val effect = VibrationEffect.createWaveform(pattern.first, pattern.second, -1)
        vibrator.vibrate(effect)
    }

    fun playCompletionPattern() {
        if (!vibrator.hasVibrator()) return

        val effect = VibrationEffect.createWaveform(LONG_SHORT_LONG_PATTERN.first, LONG_SHORT_LONG_PATTERN.second, -1)
        vibrator.vibrate(effect)
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
    }
}
