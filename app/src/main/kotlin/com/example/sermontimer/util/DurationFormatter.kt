package com.example.sermontimer.util

import kotlin.math.abs

/**
 * Utility for formatting time durations in a user-friendly way.
 * Supports both seconds and minutes display formats.
 */
object DurationFormatter {

    /**
     * Formats seconds into a human-readable duration string.
     * Examples: "5m", "2m 30s", "45s", "1h 30m"
     */
    fun formatDuration(seconds: Int): String {
        if (seconds == 0) return "0s"

        val absSeconds = abs(seconds)
        val hours = absSeconds / 3600
        val minutes = (absSeconds % 3600) / 60
        val remainingSeconds = absSeconds % 60

        val parts = mutableListOf<String>()

        if (hours > 0) {
            parts.add("${hours}h")
        }
        if (minutes > 0) {
            parts.add("${minutes}m")
        }
        if (remainingSeconds > 0) {
            parts.add("${remainingSeconds}s")
        }

        return parts.joinToString(" ")
    }

    /**
     * Formats seconds into a compact duration string suitable for presets.
     * Examples: "5:00", "2:30", "1:15:30"
     */
    fun formatDurationCompact(seconds: Int): String {
        if (seconds == 0) return "0:00"

        val absSeconds = abs(seconds)
        val hours = absSeconds / 3600
        val minutes = (absSeconds % 3600) / 60
        val remainingSeconds = absSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format("%d:%02d", minutes, remainingSeconds)
        }
    }

    /**
     * Formats a preset's durations in a compact format for list display.
     * Example: "5:00 → 20:00 → 5:00"
     */
    fun formatPresetDurations(introSec: Int, mainSec: Int, outroSec: Int): String {
        return "${formatDurationCompact(introSec)} → ${formatDurationCompact(mainSec)} → ${formatDurationCompact(outroSec)}"
    }

    /**
     * Formats seconds as MM:SS for timers and countdown displays.
     */
    fun formatTimerDisplay(seconds: Int): String {
        val absSeconds = abs(seconds)
        val minutes = absSeconds / 60
        val remainingSeconds = absSeconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
}
