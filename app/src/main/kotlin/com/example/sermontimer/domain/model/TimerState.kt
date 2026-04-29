package com.example.sermontimer.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Snapshot of the timer engine suitable for persistence and UI/state observers.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TimerState(
    val status: RunStatus,
    val segment: Segment,
    val remainingInSegmentSec: Int,
    val elapsedTotalSec: Int,
    val durations: SegmentDurations,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val startedAtElapsedRealtime: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val activePreset: ActivePresetMeta? = null,
    /** Preroll countdown (preparation phase before the actual timer starts). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val prerollRemainingSec: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val prerollTotalSec: Int = 0,
    /** Overtime tracking (after total duration is exceeded). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val overtimeElapsedSec: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val overtimeMaxSec: Int = 0,
) {
    init {
        val totalSec = durations.totalSec
        require(remainingInSegmentSec >= 0) { "Remaining in segment cannot be negative" }
        val maxForSegment = durations.durationFor(segment)
        require(remainingInSegmentSec <= maxForSegment) {
            "Remaining ($remainingInSegmentSec) cannot exceed segment duration ($maxForSegment)"
        }
        require(elapsedTotalSec in 0..totalSec) {
            "Elapsed total seconds ($elapsedTotalSec) must be between 0 and total ($totalSec)"
        }
        require(!(status == RunStatus.RUNNING && startedAtElapsedRealtime == null)) {
            "RUNNING state requires a monotonic start reference"
        }
        require(!(status == RunStatus.PREROLL && startedAtElapsedRealtime == null)) {
            "PREROLL state requires a monotonic start reference"
        }
        require(!(status == RunStatus.OVERTIME && startedAtElapsedRealtime == null)) {
            "OVERTIME state requires a monotonic start reference"
        }
        if (status == RunStatus.RUNNING ||
            status == RunStatus.PAUSED ||
            status == RunStatus.PREROLL ||
            status == RunStatus.OVERTIME
        ) {
            require(activePreset != null) { "Active preset is required when timer is running, paused, in preroll, or in overtime" }
            require(activePreset!!.durations == durations) {
                "Active preset durations must match state durations"
            }
        }
        require(!(status == RunStatus.IDLE && segment != Segment.INTRO)) {
            "IDLE state must report INTRO segment"
        }
        if (status == RunStatus.PREROLL) {
            require(prerollTotalSec > 0) { "PREROLL requires positive prerollTotalSec" }
            require(prerollRemainingSec in 0..prerollTotalSec) {
                "Preroll remaining ($prerollRemainingSec) must be in [0, $prerollTotalSec]"
            }
        }
        if (status == RunStatus.OVERTIME) {
            require(overtimeMaxSec > 0) { "OVERTIME requires positive overtimeMaxSec" }
            require(overtimeElapsedSec in 0..overtimeMaxSec) {
                "Overtime elapsed ($overtimeElapsedSec) must be in [0, $overtimeMaxSec]"
            }
            require(segment == Segment.DONE) { "OVERTIME segment must be DONE" }
            require(elapsedTotalSec == totalSec) { "OVERTIME requires elapsedTotalSec == totalSec" }
        }
        if (segment == Segment.DONE) {
            require(status == RunStatus.DONE || status == RunStatus.OVERTIME) {
                "DONE segment requires DONE or OVERTIME status"
            }
            require(remainingInSegmentSec == 0) { "DONE segment must report zero remaining" }
            require(elapsedTotalSec == totalSec) { "DONE must equal total duration" }
            require(activePreset != null) { "Completed timer must retain preset metadata for reporting" }
        }
    }

    val isActive: Boolean
        get() = status == RunStatus.RUNNING ||
                status == RunStatus.PREROLL ||
                status == RunStatus.OVERTIME

    val totalSec: Int get() = durations.totalSec

    fun withElapsed(elapsedSec: Int): TimerState = copy(elapsedTotalSec = elapsedSec)

    companion object {
        fun idle(durations: SegmentDurations): TimerState = TimerState(
            status = RunStatus.IDLE,
            segment = Segment.INTRO,
            remainingInSegmentSec = durations.introSec,
            elapsedTotalSec = 0,
            durations = durations,
            startedAtElapsedRealtime = null,
            activePreset = null,
        )
    }
}
