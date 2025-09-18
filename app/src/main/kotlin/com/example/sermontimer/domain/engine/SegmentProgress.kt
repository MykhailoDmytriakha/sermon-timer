package com.example.sermontimer.domain.engine

import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.SegmentDurations

internal data class SegmentProgress(
    val segment: Segment,
    val elapsedInSegmentSec: Int,
    val remainingInSegmentSec: Int,
)

internal fun SegmentDurations.progressForElapsed(elapsedSec: Int): SegmentProgress {
    val introBoundary = introSec
    val mainBoundary = introBoundary + mainSec
    val outroBoundary = mainBoundary + outroSec

    return when {
        elapsedSec < introBoundary -> SegmentProgress(
            segment = Segment.INTRO,
            elapsedInSegmentSec = elapsedSec,
            remainingInSegmentSec = introBoundary - elapsedSec,
        )
        elapsedSec < mainBoundary -> {
            val elapsedInMain = elapsedSec - introBoundary
            SegmentProgress(
                segment = Segment.MAIN,
                elapsedInSegmentSec = elapsedInMain,
                remainingInSegmentSec = mainBoundary - elapsedSec,
            )
        }
        elapsedSec < outroBoundary -> {
            val elapsedInOutro = elapsedSec - mainBoundary
            SegmentProgress(
                segment = Segment.OUTRO,
                elapsedInSegmentSec = elapsedInOutro,
                remainingInSegmentSec = outroBoundary - elapsedSec,
            )
        }
        else -> SegmentProgress(
            segment = Segment.DONE,
            elapsedInSegmentSec = outroSec,
            remainingInSegmentSec = 0,
        )
    }
}
