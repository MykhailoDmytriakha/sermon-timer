package com.example.sermontimer.tile

/**
 * Represents the geometry information for a single arc segment on the Tile.
 *
 * @property segmentIndex Index of the segment in the original preset order (0 = intro, etc.).
 * @property startAngleDegrees Starting angle in degrees, where 0 is at 12 o'clock and values grow clockwise.
 * @property sweepAngleDegrees Sweep amount in degrees for this segment.
 */
internal data class ArcSegmentSpec(
    val segmentIndex: Int,
    val startAngleDegrees: Float,
    val sweepAngleDegrees: Float,
) {
    init {
        require(segmentIndex >= 0) { "segmentIndex must be non-negative" }
        require(startAngleDegrees >= -360f && startAngleDegrees <= 360f) { "startAngleDegrees must be between -360 and 360" }
        require(sweepAngleDegrees >= 0f) { "sweepAngleDegrees must be non-negative" }
    }
}

/**
 * Represents the geometry information for a single linear segment in a progress bar.
 *
 * @property segmentIndex Index of the segment in the original preset order (0 = intro, etc.).
 * @property startFraction Starting position as a fraction of total width (0.0 to 1.0).
 * @property widthFraction Width as a fraction of total width (0.0 to 1.0).
 */
internal data class LinearSegmentSpec(
    val segmentIndex: Int,
    val startFraction: Float,
    val widthFraction: Float,
) {
    init {
        require(segmentIndex >= 0) { "segmentIndex must be non-negative" }
        require(startFraction >= 0f && startFraction <= 1f) { "startFraction must be between 0.0 and 1.0" }
        require(widthFraction >= 0f && widthFraction <= 1f) { "widthFraction must be between 0.0 and 1.0" }
    }
}

/**
 * Calculates arc segment specifications for the provided segment durations.
 *
 * This function computes the angular positions and sweep angles for each active segment
 * in a circular progress indicator, distributing the total sweep angle proportionally
 * to segment durations while accounting for gaps between segments.
 *
 * @param segmentDurationsSec Durations for each segment in seconds. Negative values are treated as zero.
 * @param totalSweepDegrees Total angular sweep allocated for all segments combined (typically 240-300 degrees).
 * @param startAngleDegrees Starting angle for the first segment in degrees (0° = 12 o'clock position).
 * @param gapDegrees Gap in degrees between adjacent segments (typically 5-15 degrees).
 * @return List of [ArcSegmentSpec] objects representing each active segment's geometry.
 *         Returns empty list if no valid segments or total duration is zero.
 *
 * @throws IllegalArgumentException if totalSweepDegrees or gapDegrees are negative.
 */
internal fun computeArcSegmentSpecs(
    segmentDurationsSec: List<Int>,
    totalSweepDegrees: Float,
    startAngleDegrees: Float,
    gapDegrees: Float,
): List<ArcSegmentSpec> {
    require(totalSweepDegrees >= 0f) { "totalSweepDegrees must be non-negative" }
    require(gapDegrees >= 0f) { "gapDegrees must be non-negative" }
    if (segmentDurationsSec.isEmpty()) return emptyList()

    val sanitizedDurations = segmentDurationsSec.map { duration -> duration.coerceAtLeast(0) }
    val totalDuration = sanitizedDurations.sum()
    if (totalDuration <= 0) return emptyList()

    val activeSegments = sanitizedDurations.withIndex().filter { it.value > 0 }
    if (activeSegments.isEmpty()) return emptyList()

    val gapCount = (activeSegments.size - 1).coerceAtLeast(0)
    val totalGap = gapDegrees * gapCount
    val availableSweep = (totalSweepDegrees - totalGap).coerceAtLeast(0f)

    var currentAngle = startAngleDegrees
    return buildList {
        activeSegments.forEachIndexed { index, entry ->
            val proportion = entry.value.toFloat() / totalDuration.toFloat()
            val sweep = availableSweep * proportion
            if (sweep > 0f) {
                add(
                    ArcSegmentSpec(
                        segmentIndex = entry.index,
                        startAngleDegrees = currentAngle,
                        sweepAngleDegrees = sweep,
                    )
                )
                currentAngle += sweep
                if (index < activeSegments.lastIndex) {
                    currentAngle += gapDegrees
                }
            }
        }
    }
}

/**
 * Calculates linear segment specifications for the provided segment durations.
 *
 * This function computes the horizontal positions and widths for each active segment
 * in a linear progress bar, distributing the total width proportionally to segment durations.
 * Segments are placed sequentially from left to right without gaps.
 *
 * @param segmentDurationsSec Durations for each segment in seconds. Negative values are treated as zero.
 * @return List of [LinearSegmentSpec] objects representing each active segment's geometry.
 *         Returns empty list if no valid segments or total duration is zero.
 *
 * @throws IllegalArgumentException if segmentDurationsSec is null.
 */
internal fun computeLinearSegmentSpecs(
    segmentDurationsSec: List<Int>,
): List<LinearSegmentSpec> {
    if (segmentDurationsSec.isEmpty()) return emptyList()

    val sanitizedDurations = segmentDurationsSec.map { duration -> duration.coerceAtLeast(0) }
    val totalDuration = sanitizedDurations.sum()
    if (totalDuration <= 0) return emptyList()

    val activeSegments = sanitizedDurations.withIndex().filter { it.value > 0 }
    if (activeSegments.isEmpty()) return emptyList()

    var currentFraction = 0f
    return buildList {
        activeSegments.forEach { entry ->
            val proportion = entry.value.toFloat() / totalDuration.toFloat()
            if (proportion > 0f) {
                add(
                    LinearSegmentSpec(
                        segmentIndex = entry.index,
                        startFraction = currentFraction,
                        widthFraction = proportion,
                    )
                )
                currentFraction += proportion
            }
        }
    }
}
