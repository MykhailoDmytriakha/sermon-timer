package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class CoroutineTimerEngine(
    private val reducer: TimerStateReducer,
    private val scope: CoroutineScope,
    initialState: TimerState = TimerState.idle(DEFAULT_DURATIONS),
    eventBufferCapacity: Int = DEFAULT_EVENT_BUFFER_CAPACITY,
) : TimerEngine {

    private val _state = MutableStateFlow(initialState)
    private val _events = MutableSharedFlow<TimerEvent>(
        replay = 0,
        extraBufferCapacity = eventBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val commands = Channel<TimerCommand>(Channel.UNLIMITED)

    override val state: StateFlow<TimerState> = _state.asStateFlow()
    override val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    init {
        scope.coroutineContext.job.invokeOnCompletion { cause ->
            commands.close(cause)
        }
        scope.launch {
            commands.consumeEach { command ->
                process(command)
            }
        }
    }

    override fun submit(command: TimerCommand) {
        val result = commands.trySend(command)
        if (result.isFailure && result.exceptionOrNull() !is ClosedSendChannelException) {
            scope.launch {
                commands.send(command)
            }
        }
    }

    private suspend fun process(command: TimerCommand) {
        val current = _state.value
        val reduction = reducer.reduce(current, command)
        if (reduction.newState != current) {
            _state.value = reduction.newState
        }
        if (reduction.events.isNotEmpty()) {
            emitEvents(reduction.events)
        }
    }

    private suspend fun emitEvents(events: List<TimerEvent>) {
        for (event in events) {
            if (!_events.tryEmit(event)) {
                _events.emit(event)
            }
        }
    }

    companion object {
        private val DEFAULT_DURATIONS = SegmentDurations(0, 0, 0)
        private const val DEFAULT_EVENT_BUFFER_CAPACITY = 8
    }
}
