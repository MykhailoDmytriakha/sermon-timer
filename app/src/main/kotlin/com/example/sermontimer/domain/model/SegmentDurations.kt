package com.example.sermontimer.domain.model

import kotlinx.serialization.Serializable

/** Holds the three segment durations in seconds for runtime calculations. */
@Serializable
data class SegmentDurations(
    val introSec: Int,
    val mainSec: Int,
    val outroSec: Int,
) {
    init {
        require(introSec >= 0) { "Intro duration cannot be negative" }
        require(mainSec >= 0) { "Main duration cannot be negative" }
        require(outroSec >= 0) { "Outro duration cannot be negative" }
    }

    val totalSec: Int get() = introSec + mainSec + outroSec

    fun durationFor(segment: Segment): Int = when (segment) {
        Segment.INTRO -> introSec
        Segment.MAIN -> mainSec
        Segment.OUTRO -> outroSec
        Segment.DONE -> 0
    }

    fun cumulativeBoundaryFor(segment: Segment): Int = when (segment) {
        Segment.INTRO -> introSec
        Segment.MAIN -> introSec + mainSec
        Segment.OUTRO -> introSec + mainSec + outroSec
        Segment.DONE -> introSec + mainSec + outroSec
    }

    fun nextSegmentAfter(segment: Segment): Segment = when (segment) {
        Segment.INTRO -> when {
            mainSec > 0 -> Segment.MAIN
            outroSec > 0 -> Segment.OUTRO
            else -> Segment.DONE
        }
        Segment.MAIN -> if (outroSec > 0) Segment.OUTRO else Segment.DONE
        Segment.OUTRO, Segment.DONE -> Segment.DONE
    }
}
