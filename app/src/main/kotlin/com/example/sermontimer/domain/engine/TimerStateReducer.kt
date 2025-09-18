package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.TimerState

/**
 * Pure function style reducer that transforms a [TimerState] in response to a [TimerCommand].
 * Implementation will be covered by deterministic unit tests.
 */
fun interface TimerStateReducer {
    fun reduce(current: TimerState, command: TimerCommand): ReductionResult

    data class ReductionResult(
        val newState: TimerState,
        val events: List<TimerEvent> = emptyList(),
    )
}

