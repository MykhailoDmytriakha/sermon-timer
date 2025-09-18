package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.TimerState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Contract for the deterministic timer state machine.
 * Implementation must be coroutine-safe and side-effect free except for its exposed [events].
 */
interface TimerEngine {
    /** Hot observable of current timer state. */
    val state: StateFlow<TimerState>

    /** One-shot signals for transitions (boundary reached, completion, etc.). */
    val events: SharedFlow<TimerEvent>

    /** Submit a command originating from UI, tile, or scheduler. */
    fun submit(command: TimerCommand)
}

