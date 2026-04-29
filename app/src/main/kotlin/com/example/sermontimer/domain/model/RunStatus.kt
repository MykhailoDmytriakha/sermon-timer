package com.example.sermontimer.domain.model

import kotlinx.serialization.Serializable

/** Lifecycle status of the timer, independent from the active segment. */
@Serializable
enum class RunStatus {
    IDLE,

    /** Pre-start countdown before the actual sermon timer begins. */
    PREROLL,

    RUNNING,
    PAUSED,

    /** Timer crossed the configured total duration; counting up to overtimeMaxSec. */
    OVERTIME,

    DONE,
}

