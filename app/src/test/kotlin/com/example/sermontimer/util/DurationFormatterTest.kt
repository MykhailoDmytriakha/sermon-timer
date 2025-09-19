package com.example.sermontimer.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun `formatDuration handles zero seconds`() {
        val result = DurationFormatter.formatDuration(0)
        assertThat(result).isEqualTo("0s")
    }

    @Test
    fun `formatDuration formats seconds only`() {
        val result = DurationFormatter.formatDuration(45)
        assertThat(result).isEqualTo("45s")
    }

    @Test
    fun `formatDuration formats minutes and seconds`() {
        val result = DurationFormatter.formatDuration(150) // 2 minutes 30 seconds
        assertThat(result).isEqualTo("2m 30s")
    }

    @Test
    fun `formatDuration formats hours minutes and seconds`() {
        val result = DurationFormatter.formatDuration(3661) // 1 hour, 1 minute, 1 second
        assertThat(result).isEqualTo("1h 1m 1s")
    }

    @Test
    fun `formatDuration handles pure minutes`() {
        val result = DurationFormatter.formatDuration(300) // 5 minutes
        assertThat(result).isEqualTo("5m")
    }

    @Test
    fun `formatDuration handles pure hours`() {
        val result = DurationFormatter.formatDuration(3600) // 1 hour
        assertThat(result).isEqualTo("1h")
    }

    @Test
    fun `formatDurationCompact handles zero seconds`() {
        val result = DurationFormatter.formatDurationCompact(0)
        assertThat(result).isEqualTo("0:00")
    }

    @Test
    fun `formatDurationCompact formats minutes and seconds under hour`() {
        val result = DurationFormatter.formatDurationCompact(150) // 2:30
        assertThat(result).isEqualTo("2:30")
    }

    @Test
    fun `formatDurationCompact formats hours minutes and seconds`() {
        val result = DurationFormatter.formatDurationCompact(3661) // 1:01:01
        assertThat(result).isEqualTo("1:01:01")
    }

    @Test
    fun `formatPresetDurations formats three segment durations`() {
        val result = DurationFormatter.formatPresetDurations(300, 1200, 300) // 5:00, 20:00, 5:00
        assertThat(result).isEqualTo("5:00 → 20:00 → 5:00")
    }

    @Test
    fun `formatTimerDisplay formats as MMSS with leading zeros`() {
        val result = DurationFormatter.formatTimerDisplay(125) // 2:05
        assertThat(result).isEqualTo("02:05")
    }

    @Test
    fun `formatTimerDisplay handles single digit minutes and seconds`() {
        val result = DurationFormatter.formatTimerDisplay(65) // 1:05
        assertThat(result).isEqualTo("01:05")
    }

    @Test
    fun `formatTimerDisplay handles pure seconds`() {
        val result = DurationFormatter.formatTimerDisplay(45) // 0:45
        assertThat(result).isEqualTo("00:45")
    }

    @Test
    fun `formatTimerDisplay handles zero seconds`() {
        val result = DurationFormatter.formatTimerDisplay(0) // 0:00
        assertThat(result).isEqualTo("00:00")
    }

    @Test
    fun `formatTimerDisplay handles large durations`() {
        val result = DurationFormatter.formatTimerDisplay(7265) // 121:05 (2 hours, 1 minute, 5 seconds)
        assertThat(result).isEqualTo("121:05")
    }
}
