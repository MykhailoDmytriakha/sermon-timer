package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.Segment

/** User or system intent that mutates the timer engine state. */
sealed interface TimerCommand {

    data class Start(
        val preset: Preset,
        val monotonicStartMs: Long,
    ) : TimerCommand

    data class Pause(
        val monotonicNowMs: Long,
    ) : TimerCommand

    data class Resume(
        val monotonicResumeMs: Long,
    ) : TimerCommand

    /** Skip current segment if policy permits. */
    data class SkipSegment(
        val monotonicNowMs: Long,
    ) : TimerCommand

    /** Stop timer and reset to idle. */
    data object Stop : TimerCommand

    /**
     * Monotonic time tick produced by a scheduler inside the foreground service.
     * The engine uses it to recompute remaining time with drift correction.
     */
    data class Tick(
        val monotonicNowMs: Long,
    ) : TimerCommand

    /**
     * Explicit signal that the user cancelled an automatic transition (e.g. dismissed notification).
     */
    data object Cancel : TimerCommand

    /** Internal command representing segment boundary reached via exact alarm. */
    data class SegmentBoundary(
        val segment: Segment,
        val atMonotonicMs: Long,
    ) : TimerCommand
}
