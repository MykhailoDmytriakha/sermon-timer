package com.example.sermontimer.domain.model

import kotlinx.serialization.Serializable

/** Lifecycle status of the timer, independent from the active segment. */
@Serializable
enum class RunStatus {
    IDLE,
    RUNNING,
    PAUSED,
    DONE,
}

