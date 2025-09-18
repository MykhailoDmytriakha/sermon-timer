package com.example.sermontimer.domain.model

import kotlinx.serialization.Serializable

/**
 * Immutable preset describing the three sequential segments of the sermon timer.
 */
@Serializable
data class Preset(
    val id: String,
    val title: String,
    val introSec: Int,
    val mainSec: Int,
    val outroSec: Int,
    val allowSkip: Boolean = true,
    val soundEnabled: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Preset id must not be blank" }
        require(title.isNotBlank()) { "Preset title must not be blank" }
        require(introSec >= 0) { "Intro duration cannot be negative" }
        require(mainSec >= 0) { "Main duration cannot be negative" }
        require(outroSec >= 0) { "Outro duration cannot be negative" }
    }

    val totalSec: Int get() = introSec + mainSec + outroSec
}

fun Preset.toActivePresetMeta(): ActivePresetMeta = ActivePresetMeta(
    id = id,
    durations = SegmentDurations(introSec, mainSec, outroSec),
    allowSkip = allowSkip,
    soundEnabled = soundEnabled,
)
