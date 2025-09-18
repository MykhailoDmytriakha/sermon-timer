package com.example.sermontimer.domain.model

import kotlinx.serialization.Serializable

/**
 * Minimal preset details required by the timer engine at runtime.
 * Keep this lightweight so it can be persisted in DataStore alongside [TimerState].
 */
@Serializable
data class ActivePresetMeta(
    val id: String,
    val durations: SegmentDurations,
    val allowSkip: Boolean,
    val soundEnabled: Boolean,
)

