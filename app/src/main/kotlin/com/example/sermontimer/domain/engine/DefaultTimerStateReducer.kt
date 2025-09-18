package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.toActivePresetMeta
import kotlin.math.max
import kotlin.math.min

class DefaultTimerStateReducer : TimerStateReducer {

    override fun reduce(current: TimerState, command: TimerCommand): TimerStateReducer.ReductionResult = when (command) {
        is TimerCommand.Start -> handleStart(current, command)
        is TimerCommand.Tick -> handleTick(current, command)
        is TimerCommand.Pause -> handlePause(current, command)
        is TimerCommand.Resume -> handleResume(current, command)
        is TimerCommand.SkipSegment -> handleSkip(current, command)
        is TimerCommand.Stop -> handleStop(current)
        is TimerCommand.SegmentBoundary -> handleSegmentBoundary(current, command)
        is TimerCommand.Cancel -> TimerStateReducer.ReductionResult(current)
    }

    private fun handleStart(current: TimerState, command: TimerCommand.Start): TimerStateReducer.ReductionResult {
        if (current.status == RunStatus.RUNNING) {
            return TimerStateReducer.ReductionResult(current)
        }
        val presetMeta = command.preset.toActivePresetMeta()
        val durations = presetMeta.durations
        var newState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = durations.introSec,
            elapsedTotalSec = 0,
            durations = durations,
            startedAtElapsedRealtime = command.monotonicStartMs,
            activePreset = presetMeta,
        )
        val events = mutableListOf<TimerEvent>()
        newState = advancePastZeroSegments(newState, events)
        return TimerStateReducer.ReductionResult(newState, events)
    }

    private fun handleTick(current: TimerState, command: TimerCommand.Tick): TimerStateReducer.ReductionResult {
        if (current.status != RunStatus.RUNNING || current.startedAtElapsedRealtime == null) {
            return TimerStateReducer.ReductionResult(current)
        }
        val totalSec = current.durations.totalSec
        val elapsedSinceStartSec = secondsBetween(current.startedAtElapsedRealtime, command.monotonicNowMs)
        val newElapsed = min(totalSec, elapsedSinceStartSec)
        if (newElapsed <= current.elapsedTotalSec) {
            return TimerStateReducer.ReductionResult(current)
        }
        return updateProgress(current, newElapsed)
    }

    private fun handlePause(current: TimerState, command: TimerCommand.Pause): TimerStateReducer.ReductionResult {
        if (current.status != RunStatus.RUNNING || current.startedAtElapsedRealtime == null) {
            return TimerStateReducer.ReductionResult(current)
        }
        val totalSec = current.durations.totalSec
        val newElapsed = min(totalSec, secondsBetween(current.startedAtElapsedRealtime, command.monotonicNowMs))
        val (updatedState, events) = updateProgress(current, newElapsed)
        val pausedState = updatedState.copy(
            status = RunStatus.PAUSED,
            startedAtElapsedRealtime = null,
        )
        val pauseEvent = TimerEvent.Paused(pausedState.segment, pausedState.remainingInSegmentSec)
        return TimerStateReducer.ReductionResult(pausedState, events + pauseEvent)
    }

    private fun handleResume(current: TimerState, command: TimerCommand.Resume): TimerStateReducer.ReductionResult {
        if (current.status != RunStatus.PAUSED) {
            return TimerStateReducer.ReductionResult(current)
        }
        val resumedState = current.copy(
            status = RunStatus.RUNNING,
            startedAtElapsedRealtime = adjustBaseline(command.monotonicResumeMs, current.elapsedTotalSec),
        )
        val events = mutableListOf<TimerEvent>(TimerEvent.Resumed(resumedState.segment))
        val advancedState = advancePastZeroSegments(resumedState, events)
        return TimerStateReducer.ReductionResult(advancedState, events)
    }

    private fun handleSkip(current: TimerState, command: TimerCommand.SkipSegment): TimerStateReducer.ReductionResult {
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
        val (stateAfterProgress, progressEvents) = updateProgress(recalibrated, newElapsed)
        return TimerStateReducer.ReductionResult(stateAfterProgress, progressEvents)
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
        val newElapsed = min(current.durations.totalSec, current.durations.cumulativeBoundaryFor(command.segment))
        val adjusted = current.copy(
            elapsedTotalSec = max(current.elapsedTotalSec, newElapsed),
            startedAtElapsedRealtime = adjustBaseline(command.atMonotonicMs, newElapsed),
        )
        return updateProgress(adjusted, adjusted.elapsedTotalSec)
    }

    private fun updateProgress(
        state: TimerState,
        newElapsed: Int,
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
            newState = newState.copy(
                status = RunStatus.DONE,
                segment = Segment.DONE,
                remainingInSegmentSec = 0,
                startedAtElapsedRealtime = null,
            )
            if (!events.any { it is TimerEvent.Completed }) {
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
                state = state.copy(
                    status = RunStatus.DONE,
                    segment = Segment.DONE,
                    remainingInSegmentSec = 0,
                    elapsedTotalSec = state.durations.totalSec,
                    startedAtElapsedRealtime = null,
                )
                accumulator += TimerEvent.Completed
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
