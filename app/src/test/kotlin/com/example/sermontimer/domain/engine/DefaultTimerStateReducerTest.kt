package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultTimerStateReducerTest {

    private val reducer = DefaultTimerStateReducer()
    private val testPreset = Preset(
        id = "test-preset",
        title = "Test Sermon",
        introSec = 300,  // 5 minutes
        mainSec = 1200,  // 20 minutes
        outroSec = 300   // 5 minutes
    )
    private val testDurations = SegmentDurations(300, 1200, 300)

    @Test
    fun `start from idle state transitions to running intro`() {
        val idleState = TimerState.idle(testDurations)
        val command = TimerCommand.Start(testPreset, 1000L)

        val result = reducer.reduce(idleState, command)

        assertThat(result.newState.status).isEqualTo(RunStatus.RUNNING)
        assertThat(result.newState.segment).isEqualTo(Segment.INTRO)
        assertThat(result.newState.remainingInSegmentSec).isEqualTo(300)
        assertThat(result.newState.elapsedTotalSec).isEqualTo(0)
        assertThat(result.newState.startedAtElapsedRealtime).isEqualTo(1000L)
        assertThat(result.newState.activePreset?.id).isEqualTo("test-preset")
        assertThat(result.events).isEmpty() // No boundary events on start
    }

    @Test
    fun `start from running state is idempotent`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = 250,
            elapsedTotalSec = 50,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Start(testPreset, 2000L)

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState).isEqualTo(runningState)
        assertThat(result.events).isEmpty()
    }

    @Test
    fun `tick advances elapsed time within segment`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = 250,
            elapsedTotalSec = 50,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Tick(2000L) // 1000ms elapsed

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState.elapsedTotalSec).isEqualTo(51) // 1 second elapsed
        assertThat(result.newState.remainingInSegmentSec).isEqualTo(249)
        assertThat(result.newState.segment).isEqualTo(Segment.INTRO)
    }

    @Test
    fun `tick handles boundary crossing to main segment`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = 1,
            elapsedTotalSec = 299,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Tick(2000L) // 1000ms elapsed, crosses boundary

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState.elapsedTotalSec).isEqualTo(300)
        assertThat(result.newState.segment).isEqualTo(Segment.MAIN)
        assertThat(result.newState.remainingInSegmentSec).isEqualTo(1200)
        assertThat(result.events).hasSize(1)
        assertThat(result.events[0]).isInstanceOf(TimerEvent.BoundaryReached::class.java)
        val boundaryEvent = result.events[0] as TimerEvent.BoundaryReached
        assertThat(boundaryEvent.completedSegment).isEqualTo(Segment.INTRO)
        assertThat(boundaryEvent.nextSegment).isEqualTo(Segment.MAIN)
    }

    @Test
    fun `tick handles completion of timer`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.OUTRO,
            remainingInSegmentSec = 1,
            elapsedTotalSec = 1799,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Tick(2000L) // 1000ms elapsed, completes timer

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState.status).isEqualTo(RunStatus.DONE)
        assertThat(result.newState.segment).isEqualTo(Segment.DONE)
        assertThat(result.newState.elapsedTotalSec).isEqualTo(1800)
        assertThat(result.newState.remainingInSegmentSec).isEqualTo(0)
        assertThat(result.newState.startedAtElapsedRealtime).isNull()
        assertThat(result.events).hasSize(2) // Boundary + Completed
        assertThat(result.events.any { it is TimerEvent.Completed }).isTrue()
    }

    @Test
    fun `pause transitions to paused state with current progress`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.MAIN,
            remainingInSegmentSec = 1000,
            elapsedTotalSec = 350,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Pause(1500L) // 500ms elapsed

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState.status).isEqualTo(RunStatus.PAUSED)
        assertThat(result.newState.elapsedTotalSec).isEqualTo(350) // Should use current elapsed
        assertThat(result.newState.startedAtElapsedRealtime).isNull()
        assertThat(result.events).hasSize(1)
        assertThat(result.events[0]).isInstanceOf(TimerEvent.Paused::class.java)
    }

    @Test
    fun `resume from paused transitions back to running with updated baseline`() {
        val pausedState = TimerState(
            status = RunStatus.PAUSED,
            segment = Segment.MAIN,
            remainingInSegmentSec = 1000,
            elapsedTotalSec = 350,
            durations = testDurations,
            startedAtElapsedRealtime = null,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Resume(2000L)

        val result = reducer.reduce(pausedState, command)

        assertThat(result.newState.status).isEqualTo(RunStatus.RUNNING)
        assertThat(result.newState.startedAtElapsedRealtime).isEqualTo(1650L) // 2000 - 350
        assertThat(result.events).hasSize(1)
        assertThat(result.events[0]).isInstanceOf(TimerEvent.Resumed::class.java)
    }

    @Test
    fun `skip segment advances to next segment when allowed`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = 200,
            elapsedTotalSec = 100,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.SkipSegment(1500L)

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState.elapsedTotalSec).isEqualTo(300) // Jump to intro boundary
        assertThat(result.newState.segment).isEqualTo(Segment.MAIN)
        assertThat(result.newState.remainingInSegmentSec).isEqualTo(1200)
    }

    @Test
    fun `skip segment rejected when not allowed`() {
        val presetWithoutSkip = testPreset.copy(allowSkip = false)
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = 200,
            elapsedTotalSec = 100,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = presetWithoutSkip.toActivePresetMeta()
        )
        val command = TimerCommand.SkipSegment(1500L)

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState).isEqualTo(runningState)
        assertThat(result.events).hasSize(1)
        assertThat(result.events[0]).isEqualTo(TimerEvent.SkipRejected)
    }

    @Test
    fun `stop resets to idle state from any state`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.MAIN,
            remainingInSegmentSec = 1000,
            elapsedTotalSec = 350,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Stop

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState.status).isEqualTo(RunStatus.IDLE)
        assertThat(result.newState.segment).isEqualTo(Segment.INTRO)
        assertThat(result.newState.elapsedTotalSec).isEqualTo(0)
        assertThat(result.newState.remainingInSegmentSec).isEqualTo(300)
        assertThat(result.newState.startedAtElapsedRealtime).isNull()
        assertThat(result.newState.activePreset).isNull()
        assertThat(result.events).hasSize(1)
        assertThat(result.events[0]).isEqualTo(TimerEvent.Stopped)
    }

    @Test
    fun `zero duration segments are automatically skipped on start`() {
        val presetWithZeroIntro = testPreset.copy(introSec = 0)
        val idleState = TimerState.idle(SegmentDurations(0, 1200, 300))
        val command = TimerCommand.Start(presetWithZeroIntro, 1000L)

        val result = reducer.reduce(idleState, command)

        assertThat(result.newState.segment).isEqualTo(Segment.MAIN) // Skipped intro
        assertThat(result.newState.remainingInSegmentSec).isEqualTo(1200)
        assertThat(result.newState.elapsedTotalSec).isEqualTo(0)
        assertThat(result.events).hasSize(1) // Boundary event for skipped segment
    }

    @Test
    fun `segment boundary command advances progress correctly`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = 50,
            elapsedTotalSec = 250,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.SegmentBoundary(Segment.INTRO, 1500L)

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState.elapsedTotalSec).isEqualTo(300) // Boundary of intro
        assertThat(result.newState.segment).isEqualTo(Segment.MAIN)
        assertThat(result.newState.startedAtElapsedRealtime).isEqualTo(1200L) // Adjusted baseline
    }

    @Test
    fun `cancel command does nothing`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = 250,
            elapsedTotalSec = 50,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Cancel

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState).isEqualTo(runningState)
        assertThat(result.events).isEmpty()
    }

    @Test
    fun `tick with no change returns same state`() {
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = 250,
            elapsedTotalSec = 50,
            durations = testDurations,
            startedAtElapsedRealtime = 1000L,
            activePreset = testPreset.toActivePresetMeta()
        )
        val command = TimerCommand.Tick(1000L) // Same time as start

        val result = reducer.reduce(runningState, command)

        assertThat(result.newState).isEqualTo(runningState)
        assertThat(result.events).isEmpty()
    }
}
