package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.Segment

/**
 * Discrete signal emitted by the timer engine that the outer layers (service, tile, haptics) react to.
 */
sealed interface TimerEvent {
    /** Fired as soon as a boundary is crossed. */
    data class BoundaryReached(
        val completedSegment: Segment,
        val nextSegment: Segment,
    ) : TimerEvent

    /** Fired whenever the timer transitions into a paused state. */
    data class Paused(
        val segment: Segment,
        val remainingInSegmentSec: Int,
    ) : TimerEvent

    /** Fired when timer resumes from paused state. */
    data class Resumed(
        val segment: Segment,
    ) : TimerEvent

    /** Fired when the timer transitions to DONE. */
    data object Completed : TimerEvent

    /** Fired when the timer is fully stopped/reset. */
    data object Stopped : TimerEvent

    /** Indicates that skip was ignored due to policy (e.g. not allowed). */
    data object SkipRejected : TimerEvent

    /** Fired when entering preroll (preparation countdown) — distinct from RUNNING start. */
    data object PrerollStarted : TimerEvent

    /** Fired when preroll ends and the actual timer begins (the user steps to the podium). */
    data object PrerollEnded : TimerEvent

    /** Fired when the segments have all elapsed and overtime begins. */
    data object OvertimeStarted : TimerEvent

    /** Fired when the overtime cap is reached and we transition to DONE. */
    data object OvertimeCapped : TimerEvent
}

