package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.domain.model.toActivePresetMeta
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Behaviour tests for the preroll countdown and overtime overrun added in 1.1.
 * Existing reducer tests in DefaultTimerStateReducerTest cover the IDLE/RUNNING/PAUSED/DONE flow;
 * this file focuses on the new states.
 */
class PrerollOvertimeReducerTest {

    private val reducer = DefaultTimerStateReducer()
    private val preset = Preset(
        id = "p",
        title = "Sermon",
        introSec = 30,
        mainSec = 60,
        outroSec = 30,
    )
    private val durations = SegmentDurations(30, 60, 30)
    private val start = 1_000_000L
    private fun at(elapsedSec: Int) = start + elapsedSec * 1000L

    @Test
    fun `start with preroll enters PREROLL with countdown intact`() {
        val idle = TimerState.idle(durations)
        val result = reducer.reduce(idle, TimerCommand.Start(preset, start, prerollSec = 60))

        assertThat(result.newState.status).isEqualTo(RunStatus.PREROLL)
        assertThat(result.newState.prerollRemainingSec).isEqualTo(60)
        assertThat(result.newState.prerollTotalSec).isEqualTo(60)
        assertThat(result.newState.segment).isEqualTo(Segment.INTRO)
        assertThat(result.newState.elapsedTotalSec).isEqualTo(0)
        assertThat(result.events).contains(TimerEvent.PrerollStarted)
    }

    @Test
    fun `tick during preroll counts down preroll without touching segments`() {
        val idle = TimerState.idle(durations)
        val started = reducer.reduce(idle, TimerCommand.Start(preset, start, prerollSec = 60)).newState

        val ticked = reducer.reduce(started, TimerCommand.Tick(at(10))).newState
        assertThat(ticked.status).isEqualTo(RunStatus.PREROLL)
        assertThat(ticked.prerollRemainingSec).isEqualTo(50)
        assertThat(ticked.elapsedTotalSec).isEqualTo(0)
    }

    @Test
    fun `preroll completing transitions to RUNNING and emits PrerollEnded`() {
        val idle = TimerState.idle(durations)
        val started = reducer.reduce(idle, TimerCommand.Start(preset, start, prerollSec = 5)).newState

        val result = reducer.reduce(started, TimerCommand.Tick(at(5)))
        assertThat(result.newState.status).isEqualTo(RunStatus.RUNNING)
        assertThat(result.newState.segment).isEqualTo(Segment.INTRO)
        assertThat(result.newState.elapsedTotalSec).isEqualTo(0)
        assertThat(result.events).contains(TimerEvent.PrerollEnded)
    }

    @Test
    fun `skip during preroll starts the timer immediately`() {
        val idle = TimerState.idle(durations)
        val started = reducer.reduce(idle, TimerCommand.Start(preset, start, prerollSec = 60)).newState

        val result = reducer.reduce(started, TimerCommand.SkipSegment(at(3)))
        assertThat(result.newState.status).isEqualTo(RunStatus.RUNNING)
        assertThat(result.events).contains(TimerEvent.PrerollEnded)
    }

    @Test
    fun `stop during preroll resets to idle`() {
        val idle = TimerState.idle(durations)
        val started = reducer.reduce(idle, TimerCommand.Start(preset, start, prerollSec = 60)).newState

        val result = reducer.reduce(started, TimerCommand.Stop)
        assertThat(result.newState.status).isEqualTo(RunStatus.IDLE)
        assertThat(result.events).contains(TimerEvent.Stopped)
    }

    @Test
    fun `total time elapsed enters OVERTIME instead of DONE when overtime configured`() {
        val idle = TimerState.idle(durations)
        val started = reducer.reduce(idle, TimerCommand.Start(preset, start, overtimeMaxSec = 120)).newState

        // Tick to total = 120s elapsed (intro 30 + main 60 + outro 30)
        val result = reducer.reduce(started, TimerCommand.Tick(at(120)))
        assertThat(result.newState.status).isEqualTo(RunStatus.OVERTIME)
        assertThat(result.newState.segment).isEqualTo(Segment.DONE)
        assertThat(result.newState.elapsedTotalSec).isEqualTo(120)
        assertThat(result.newState.overtimeElapsedSec).isEqualTo(0)
        assertThat(result.events).contains(TimerEvent.OvertimeStarted)
    }

    @Test
    fun `overtime ticks count up until cap`() {
        val idle = TimerState.idle(durations)
        val started = reducer.reduce(idle, TimerCommand.Start(preset, start, overtimeMaxSec = 60)).newState
        val toOvertime = reducer.reduce(started, TimerCommand.Tick(at(120))).newState

        val mid = reducer.reduce(toOvertime, TimerCommand.Tick(at(150))).newState
        assertThat(mid.status).isEqualTo(RunStatus.OVERTIME)
        assertThat(mid.overtimeElapsedSec).isEqualTo(30)
    }

    @Test
    fun `overtime cap reached transitions to DONE and emits OvertimeCapped`() {
        val idle = TimerState.idle(durations)
        val started = reducer.reduce(idle, TimerCommand.Start(preset, start, overtimeMaxSec = 60)).newState
        val toOvertime = reducer.reduce(started, TimerCommand.Tick(at(120))).newState

        val result = reducer.reduce(toOvertime, TimerCommand.Tick(at(180)))
        assertThat(result.newState.status).isEqualTo(RunStatus.DONE)
        assertThat(result.events).contains(TimerEvent.OvertimeCapped)
    }

    @Test
    fun `total elapsed without overtime still goes straight to DONE`() {
        val idle = TimerState.idle(durations)
        val started = reducer.reduce(idle, TimerCommand.Start(preset, start)).newState

        val result = reducer.reduce(started, TimerCommand.Tick(at(120)))
        assertThat(result.newState.status).isEqualTo(RunStatus.DONE)
        assertThat(result.events).contains(TimerEvent.Completed)
    }
}
