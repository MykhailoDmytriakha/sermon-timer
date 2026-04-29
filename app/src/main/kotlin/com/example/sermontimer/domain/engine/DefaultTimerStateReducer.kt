package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.domain.model.toActivePresetMeta
import kotlin.math.max
import kotlin.math.min

class DefaultTimerStateReducer : TimerStateReducer {

    override fun reduce(
        current: TimerState,
        command: TimerCommand
    ): TimerStateReducer.ReductionResult = when (command) {
        is TimerCommand.Start -> handleStart(current, command)
        is TimerCommand.Tick -> handleTick(current, command)
        is TimerCommand.Pause -> handlePause(current, command)
        is TimerCommand.Resume -> handleResume(current, command)
        is TimerCommand.SkipSegment -> handleSkip(current, command)
        is TimerCommand.Stop -> handleStop(current)
        is TimerCommand.SegmentBoundary -> handleSegmentBoundary(current, command)
        is TimerCommand.Cancel -> TimerStateReducer.ReductionResult(current)
    }

    private fun handleStart(
        current: TimerState,
        command: TimerCommand.Start
    ): TimerStateReducer.ReductionResult {
        if (current.status == RunStatus.RUNNING || current.status == RunStatus.PREROLL || current.status == RunStatus.OVERTIME) {
            return TimerStateReducer.ReductionResult(current)
        }
        val presetMeta = command.preset.toActivePresetMeta()
        val durations = presetMeta.durations
        val prerollSec = command.prerollSec.coerceAtLeast(0)
        val overtimeMaxSec = command.overtimeMaxSec.coerceAtLeast(0)

        if (prerollSec > 0) {
            val prerollState = TimerState(
                status = RunStatus.PREROLL,
                segment = Segment.INTRO,
                remainingInSegmentSec = durations.introSec,
                elapsedTotalSec = 0,
                durations = durations,
                startedAtElapsedRealtime = command.monotonicStartMs,
                activePreset = presetMeta,
                prerollRemainingSec = prerollSec,
                prerollTotalSec = prerollSec,
                overtimeMaxSec = overtimeMaxSec,
            )
            return TimerStateReducer.ReductionResult(
                prerollState,
                listOf(TimerEvent.PrerollStarted),
            )
        }

        var newState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = durations.introSec,
            elapsedTotalSec = 0,
            durations = durations,
            startedAtElapsedRealtime = command.monotonicStartMs,
            activePreset = presetMeta,
            overtimeMaxSec = overtimeMaxSec,
        )
        val events = mutableListOf<TimerEvent>()
        newState = advancePastZeroSegments(newState, events)
        return TimerStateReducer.ReductionResult(newState, events)
    }

    private fun handleTick(
        current: TimerState,
        command: TimerCommand.Tick
    ): TimerStateReducer.ReductionResult {
        return when (current.status) {
            RunStatus.PREROLL -> tickPreroll(current, command)
            RunStatus.RUNNING -> tickRunning(current, command)
            RunStatus.OVERTIME -> tickOvertime(current, command)
            else -> TimerStateReducer.ReductionResult(current)
        }
    }

    private fun tickPreroll(
        current: TimerState,
        command: TimerCommand.Tick,
    ): TimerStateReducer.ReductionResult {
        val baseline = current.startedAtElapsedRealtime ?: return TimerStateReducer.ReductionResult(current)
        val elapsed = secondsBetween(baseline, command.monotonicNowMs)
        if (elapsed >= current.prerollTotalSec) {
            // Preroll done — transition to RUNNING, baseline = now.
            val running = current.copy(
                status = RunStatus.RUNNING,
                segment = Segment.INTRO,
                remainingInSegmentSec = current.durations.introSec,
                elapsedTotalSec = 0,
                startedAtElapsedRealtime = command.monotonicNowMs,
                prerollRemainingSec = 0,
                prerollTotalSec = 0,
            )
            val events = mutableListOf<TimerEvent>(TimerEvent.PrerollEnded)
            val advanced = advancePastZeroSegments(running, events)
            return TimerStateReducer.ReductionResult(advanced, events)
        }
        val remaining = (current.prerollTotalSec - elapsed).coerceAtLeast(0)
        if (remaining == current.prerollRemainingSec) {
            return TimerStateReducer.ReductionResult(current)
        }
        return TimerStateReducer.ReductionResult(
            current.copy(prerollRemainingSec = remaining),
        )
    }

    private fun tickRunning(
        current: TimerState,
        command: TimerCommand.Tick,
    ): TimerStateReducer.ReductionResult {
        val baseline = current.startedAtElapsedRealtime ?: return TimerStateReducer.ReductionResult(current)
        val totalSec = current.durations.totalSec
        val elapsedSinceBaselineSec = secondsBetween(baseline, command.monotonicNowMs)
        val newElapsed = min(totalSec, elapsedSinceBaselineSec)
        if (newElapsed <= current.elapsedTotalSec) {
            return TimerStateReducer.ReductionResult(current)
        }
        return updateProgress(current, newElapsed, command.monotonicNowMs)
    }

    private fun tickOvertime(
        current: TimerState,
        command: TimerCommand.Tick,
    ): TimerStateReducer.ReductionResult {
        val baseline = current.startedAtElapsedRealtime ?: return TimerStateReducer.ReductionResult(current)
        val elapsed = secondsBetween(baseline, command.monotonicNowMs)
        if (elapsed >= current.overtimeMaxSec) {
            // Cap reached — go to DONE.
            val done = current.copy(
                status = RunStatus.DONE,
                overtimeElapsedSec = current.overtimeMaxSec,
                startedAtElapsedRealtime = null,
            )
            return TimerStateReducer.ReductionResult(
                done,
                listOf(TimerEvent.OvertimeCapped, TimerEvent.Completed),
            )
        }
        val capped = elapsed.coerceAtMost(current.overtimeMaxSec)
        if (capped == current.overtimeElapsedSec) {
            return TimerStateReducer.ReductionResult(current)
        }
        return TimerStateReducer.ReductionResult(
            current.copy(overtimeElapsedSec = capped),
        )
    }

    private fun handlePause(
        current: TimerState,
        command: TimerCommand.Pause
    ): TimerStateReducer.ReductionResult {
        if (current.status != RunStatus.RUNNING || current.startedAtElapsedRealtime == null) {
            return TimerStateReducer.ReductionResult(current)
        }
        val totalSec = current.durations.totalSec
        val elapsedSinceBaseline =
            secondsBetween(current.startedAtElapsedRealtime, command.monotonicNowMs)
        val newElapsed = min(totalSec, elapsedSinceBaseline)
        val (updatedState, events) = updateProgress(current, newElapsed, command.monotonicNowMs)
        if (updatedState.status != RunStatus.RUNNING) {
            // Hit completion / overtime mid-pause — keep that transition.
            return TimerStateReducer.ReductionResult(updatedState, events)
        }
        val pausedState = updatedState.copy(
            status = RunStatus.PAUSED,
            startedAtElapsedRealtime = null,
        )
        val pauseEvent = TimerEvent.Paused(pausedState.segment, pausedState.remainingInSegmentSec)
        return TimerStateReducer.ReductionResult(pausedState, events + pauseEvent)
    }

    private fun handleResume(
        current: TimerState,
        command: TimerCommand.Resume
    ): TimerStateReducer.ReductionResult {
        if (current.status != RunStatus.PAUSED) {
            return TimerStateReducer.ReductionResult(current)
        }
        val resumedState = current.copy(
            status = RunStatus.RUNNING,
            startedAtElapsedRealtime = adjustBaseline(
                command.monotonicResumeMs,
                current.elapsedTotalSec
            ),
        )
        val events = mutableListOf<TimerEvent>(TimerEvent.Resumed(resumedState.segment))
        val advancedState = advancePastZeroSegments(resumedState, events)
        return TimerStateReducer.ReductionResult(advancedState, events)
    }

    private fun handleSkip(
        current: TimerState,
        command: TimerCommand.SkipSegment
    ): TimerStateReducer.ReductionResult {
        // Skipping during PREROLL = start the timer immediately.
        if (current.status == RunStatus.PREROLL) {
            val durations = current.durations
            val running = current.copy(
                status = RunStatus.RUNNING,
                segment = Segment.INTRO,
                remainingInSegmentSec = durations.introSec,
                elapsedTotalSec = 0,
                startedAtElapsedRealtime = command.monotonicNowMs,
                prerollRemainingSec = 0,
                prerollTotalSec = 0,
            )
            val events = mutableListOf<TimerEvent>(TimerEvent.PrerollEnded)
            val advanced = advancePastZeroSegments(running, events)
            return TimerStateReducer.ReductionResult(advanced, events)
        }
        val activePreset = current.activePreset ?: return TimerStateReducer.ReductionResult(current)
        if (!activePreset.allowSkip || current.status != RunStatus.RUNNING) {
            return if (!activePreset.allowSkip) {
                TimerStateReducer.ReductionResult(current, listOf(TimerEvent.SkipRejected))
            } else {
                TimerStateReducer.ReductionResult(current)
            }
        }
        if (current.segment == Segment.DONE) {
            return TimerStateReducer.ReductionResult(current)
        }
        val boundarySec = activePreset.durations.cumulativeBoundaryFor(current.segment)
        val newElapsed = min(activePreset.durations.totalSec, boundarySec)
        val recalibrated = current.copy(
            elapsedTotalSec = newElapsed,
            startedAtElapsedRealtime = adjustBaseline(command.monotonicNowMs, newElapsed),
        )
        return updateProgress(recalibrated, newElapsed, command.monotonicNowMs)
    }

    private fun handleStop(current: TimerState): TimerStateReducer.ReductionResult {
        if (current.status == RunStatus.IDLE) {
            return TimerStateReducer.ReductionResult(current)
        }
        val durations = current.durations
        val resetState = TimerState.idle(durations).copy(
            remainingInSegmentSec = durations.introSec,
        )
        return TimerStateReducer.ReductionResult(resetState, listOf(TimerEvent.Stopped))
    }

    private fun handleSegmentBoundary(
        current: TimerState,
        command: TimerCommand.SegmentBoundary,
    ): TimerStateReducer.ReductionResult {
        if (current.status != RunStatus.RUNNING) {
            return TimerStateReducer.ReductionResult(current)
        }
        val newElapsed = min(
            current.durations.totalSec,
            current.durations.cumulativeBoundaryFor(command.segment)
        )
        val adjusted = current.copy(
            elapsedTotalSec = max(current.elapsedTotalSec, newElapsed),
            startedAtElapsedRealtime = adjustBaseline(command.atMonotonicMs, newElapsed),
        )
        return updateProgress(adjusted, adjusted.elapsedTotalSec, command.atMonotonicMs)
    }

    private fun updateProgress(
        state: TimerState,
        newElapsed: Int,
        nowMonotonicMs: Long,
    ): TimerStateReducer.ReductionResult {
        val durations = state.durations
        val events = mutableListOf<TimerEvent>()
        val previousElapsed = state.elapsedTotalSec
        val boundaries = collectBoundariesCrossed(previousElapsed, newElapsed, durations)
        var newState = state.copy(elapsedTotalSec = newElapsed)
        boundaries.forEach { completedSegment ->
            val next = durations.nextSegmentAfter(completedSegment)
            events += TimerEvent.BoundaryReached(completedSegment, next)
        }
        if (newElapsed >= durations.totalSec) {
            newState = if (state.overtimeMaxSec > 0) {
                newState.copy(
                    status = RunStatus.OVERTIME,
                    segment = Segment.DONE,
                    remainingInSegmentSec = 0,
                    elapsedTotalSec = durations.totalSec,
                    startedAtElapsedRealtime = nowMonotonicMs,
                    overtimeElapsedSec = 0,
                )
            } else {
                newState.copy(
                    status = RunStatus.DONE,
                    segment = Segment.DONE,
                    remainingInSegmentSec = 0,
                    startedAtElapsedRealtime = null,
                )
            }
            if (newState.status == RunStatus.OVERTIME) {
                events += TimerEvent.OvertimeStarted
            }
            if (!events.any { it is TimerEvent.Completed } && newState.status == RunStatus.DONE) {
                events += TimerEvent.Completed
            }
            return TimerStateReducer.ReductionResult(newState, events)
        }
        val progress = durations.progressForElapsed(newElapsed)
        newState = newState.copy(
            segment = progress.segment,
            remainingInSegmentSec = progress.remainingInSegmentSec,
        )
        return TimerStateReducer.ReductionResult(newState, events)
    }

    private fun advancePastZeroSegments(
        startingState: TimerState,
        accumulator: MutableList<TimerEvent>,
    ): TimerState {
        var state = startingState
        while (state.status == RunStatus.RUNNING && state.remainingInSegmentSec == 0 && state.segment != Segment.DONE) {
            val completed = state.segment
            val next = state.durations.nextSegmentAfter(completed)
            val boundaryElapsed = state.durations.cumulativeBoundaryFor(completed)
            accumulator += TimerEvent.BoundaryReached(completed, next)
            if (next == Segment.DONE) {
                if (state.overtimeMaxSec > 0) {
                    state = state.copy(
                        status = RunStatus.OVERTIME,
                        segment = Segment.DONE,
                        remainingInSegmentSec = 0,
                        elapsedTotalSec = state.durations.totalSec,
                        startedAtElapsedRealtime = state.startedAtElapsedRealtime,
                        overtimeElapsedSec = 0,
                    )
                    accumulator += TimerEvent.OvertimeStarted
                } else {
                    state = state.copy(
                        status = RunStatus.DONE,
                        segment = Segment.DONE,
                        remainingInSegmentSec = 0,
                        elapsedTotalSec = state.durations.totalSec,
                        startedAtElapsedRealtime = null,
                    )
                    accumulator += TimerEvent.Completed
                }
            } else {
                state = state.copy(
                    elapsedTotalSec = boundaryElapsed,
                    segment = next,
                    remainingInSegmentSec = state.durations.durationFor(next),
                )
            }
        }
        return state
    }

    private fun collectBoundariesCrossed(
        previousElapsed: Int,
        newElapsed: Int,
        durations: SegmentDurations,
    ): List<Segment> {
        if (newElapsed <= previousElapsed) return emptyList()
        val boundaries = listOf(
            Segment.INTRO to durations.cumulativeBoundaryFor(Segment.INTRO),
            Segment.MAIN to durations.cumulativeBoundaryFor(Segment.MAIN),
            Segment.OUTRO to durations.cumulativeBoundaryFor(Segment.OUTRO),
        )
        return boundaries
            .filter { (_, boundary) -> previousElapsed < boundary && newElapsed >= boundary }
            .map { it.first }
    }

    private fun secondsBetween(startMonotonicMs: Long, nowMonotonicMs: Long): Int {
        val delta = max(0L, nowMonotonicMs - startMonotonicMs)
        return (delta / 1000L).toInt()
    }

    private fun adjustBaseline(monotonicNowMs: Long, elapsedSec: Int): Long {
        return monotonicNowMs - elapsedSec * 1000L
    }
}
