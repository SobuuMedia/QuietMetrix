package com.quietmetrix.dashboard.ui.components.charts

import kotlin.math.max

// ---------------------------------------------------------------------------
// Pure layout math for the Canvas charts. No Compose dependency — fully
// unit-testable on the JVM. All coordinates are in px within the drawable
// area; the composables pass the measured size in and draw the returned specs.
// ---------------------------------------------------------------------------

/** Linear scale: maps a value in [domainMin, domainMax] to [rangeMin, rangeMax]. */
fun scaleLinear(
    domainMin: Float,
    domainMax: Float,
    rangeMin: Float,
    rangeMax: Float,
    x: Float,
): Float {
    if (domainMax == domainMin) return (rangeMin + rangeMax) / 2f
    val t = (x - domainMin) / (domainMax - domainMin)
    return rangeMin + t * (rangeMax - rangeMin)
}

data class BarRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val value: Float,
)

/**
 * Lays out vertical bars inside the area [areaX, areaX+areaWidth] x
 * [areaY, areaY+areaHeight]. Bars grow upward from the bottom; the tallest bar
 * (relative to [max], defaulting to the max value) reaches the area top.
 * [gapFraction] is the share of each slot reserved as gap (0..1).
 */
fun barRects(
    values: List<Float>,
    areaX: Float,
    areaY: Float,
    areaWidth: Float,
    areaHeight: Float,
    gapFraction: Float = 0.2f,
    max: Float? = null,
): List<BarRect> {
    if (values.isEmpty()) return emptyList()
    val n = values.size
    val effectiveMax = (max ?: values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val slot = areaWidth / n
    val barWidth = slot * (1f - gapFraction)
    val inset = slot * gapFraction / 2f
    return values.mapIndexed { i, v ->
        val h = (v / effectiveMax) * areaHeight
        BarRect(
            x = areaX + i * slot + inset,
            y = areaY + areaHeight - h,
            width = barWidth,
            height = h,
            value = v,
        )
    }
}

data class DonutSlice(
    val startAngle: Float,
    val sweepAngle: Float,
    val fraction: Float,
    val label: String,
    val value: Float,
)

/**
 * Splits a donut/circle into arcs proportional to [values]. Starts at
 * [startAngle] (default -90° = top). Returns an empty list when there is no
 * total so the composable can show an empty state instead.
 */
fun donutArcs(
    values: List<Float>,
    labels: List<String>,
    startAngle: Float = -90f,
): List<DonutSlice> {
    val total = values.sum()
    if (total <= 0f) return emptyList()
    val slices = ArrayList<DonutSlice>(values.size)
    var cursor = startAngle
    for (i in values.indices) {
        val v = values[i]
        val fraction = v / total
        val sweep = fraction * 360f
        slices += DonutSlice(
            startAngle = cursor,
            sweepAngle = sweep,
            fraction = fraction,
            label = labels.getOrElse(i) { "" },
            value = v,
        )
        cursor += sweep
    }
    return slices
}

data class LinePoint(val x: Float, val y: Float, val value: Float)

/**
 * Maps [values] to points across the area. x is spread evenly across the full
 * width; y is inverted (higher value = smaller y, i.e. toward the top).
 */
fun linePoints(
    values: List<Float>,
    areaX: Float,
    areaY: Float,
    areaWidth: Float,
    areaHeight: Float,
    max: Float? = null,
): List<LinePoint> {
    if (values.isEmpty()) return emptyList()
    val effectiveMax = (max ?: values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val n = values.size
    return values.mapIndexed { i, v ->
        val x = if (n == 1) areaX + areaWidth / 2f else areaX + (i.toFloat() / (n - 1)) * areaWidth
        val y = areaY + areaHeight - (v / effectiveMax) * areaHeight
        LinePoint(x, y, v)
    }
}

/** Tick values for a y-axis: 0, 0.25*max, 0.5*max, 0.75*max, max. */
fun yTicks(maxValue: Float): List<Float> {
    val m = max(maxValue, 0f)
    return listOf(0f, 0.25f * m, 0.5f * m, 0.75f * m, m)
}
