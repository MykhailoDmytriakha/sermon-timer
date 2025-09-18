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
        if (status == RunStatus.RUNNING || status == RunStatus.PAUSED) {
            require(activePreset != null) { "Active preset is required when timer is running or paused" }
            require(activePreset!!.durations == durations) {
                "Active preset durations must match state durations"
            }
        }
        require(!(status == RunStatus.IDLE && segment != Segment.INTRO)) {
            "IDLE state must report INTRO segment"
        }
        if (segment == Segment.DONE) {
            require(status == RunStatus.DONE) { "DONE segment requires DONE status" }
            require(remainingInSegmentSec == 0) { "DONE segment must report zero remaining" }
            require(elapsedTotalSec == totalSec) { "DONE must equal total duration" }
            require(activePreset != null) { "Completed timer must retain preset metadata for reporting" }
        }
    }

    val isActive: Boolean
        get() = status == RunStatus.RUNNING

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
