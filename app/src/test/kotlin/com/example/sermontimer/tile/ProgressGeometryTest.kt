package com.example.sermontimer.tile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressGeometryTest {

    @Test
    fun computeArcSegmentsRespectsDurationsAndGaps() {
        val totalSweep = 300f
        val gap = 10f
        val result = computeArcSegmentSpecs(
            segmentDurationsSec = listOf(60, 120, 60),
            totalSweepDegrees = totalSweep,
            startAngleDegrees = -150f,
            gapDegrees = gap,
        )

        assertThat(result).hasSize(3)
        val first = result[0]
        val second = result[1]
        val third = result[2]

        assertThat(first.segmentIndex).isEqualTo(0)
        assertThat(first.startAngleDegrees).isWithin(0.001f).of(-150f)

        val expectedSecondStart = first.startAngleDegrees + first.sweepAngleDegrees + gap
        assertThat(second.segmentIndex).isEqualTo(1)
        assertThat(second.startAngleDegrees).isWithin(0.001f).of(expectedSecondStart)

        val expectedThirdStart = second.startAngleDegrees + second.sweepAngleDegrees + gap
        assertThat(third.segmentIndex).isEqualTo(2)
        assertThat(third.startAngleDegrees).isWithin(0.001f).of(expectedThirdStart)

        val totalSweepApplied = result.sumOf { it.sweepAngleDegrees.toDouble() }.toFloat()
        val expectedAvailableSweep = totalSweep - gap * 2
        assertThat(totalSweepApplied).isWithin(0.01f).of(expectedAvailableSweep)
    }

    @Test
    fun computeArcSegmentsSkipsZeroLengthDurations() {
        val result = computeArcSegmentSpecs(
            segmentDurationsSec = listOf(0, 90, 0),
            totalSweepDegrees = 280f,
            startAngleDegrees = -140f,
            gapDegrees = 6f,
        )

        assertThat(result).hasSize(1)
        assertThat(result[0].segmentIndex).isEqualTo(1)
        assertThat(result[0].startAngleDegrees).isWithin(0.001f).of(-140f)
    }

    @Test
    fun computeArcSegmentsReturnsEmptyWhenNoDurations() {
        val result = computeArcSegmentSpecs(
            segmentDurationsSec = listOf(0, 0, 0),
            totalSweepDegrees = 280f,
            startAngleDegrees = -140f,
            gapDegrees = 6f,
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun computeLinearSegmentSpecsRespectsDurations() {
        val result = computeLinearSegmentSpecs(listOf(60, 120, 60))

        assertThat(result).hasSize(3)
        val first = result[0]
        val second = result[1]
        val third = result[2]

        assertThat(first.segmentIndex).isEqualTo(0)
        assertThat(first.startFraction).isWithin(0.001f).of(0f)
        assertThat(first.widthFraction).isWithin(0.001f).of(0.25f) // 60/240

        assertThat(second.segmentIndex).isEqualTo(1)
        assertThat(second.startFraction).isWithin(0.001f).of(0.25f)
        assertThat(second.widthFraction).isWithin(0.001f).of(0.5f) // 120/240

        assertThat(third.segmentIndex).isEqualTo(2)
        assertThat(third.startFraction).isWithin(0.001f).of(0.75f)
        assertThat(third.widthFraction).isWithin(0.001f).of(0.25f) // 60/240

        val totalWidth = result.sumOf { it.widthFraction.toDouble() }.toFloat()
        assertThat(totalWidth).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun computeLinearSegmentSpecsSkipsZeroLengthDurations() {
        val result = computeLinearSegmentSpecs(listOf(0, 90, 0))

        assertThat(result).hasSize(1)
        assertThat(result[0].segmentIndex).isEqualTo(1)
        assertThat(result[0].startFraction).isWithin(0.001f).of(0f)
        assertThat(result[0].widthFraction).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun computeLinearSegmentSpecsReturnsEmptyWhenNoDurations() {
        val result = computeLinearSegmentSpecs(listOf(0, 0, 0))

        assertThat(result).isEmpty()
    }

    @Test
    fun computeLinearSegmentSpecsHandlesEmptyList() {
        val result = computeLinearSegmentSpecs(emptyList())

        assertThat(result).isEmpty()
    }
}
