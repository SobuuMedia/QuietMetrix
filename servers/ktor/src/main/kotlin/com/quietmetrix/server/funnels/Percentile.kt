package com.quietmetrix.server.funnels

/**
 * Nearest-rank percentile: rank = ceil(p/100 * n), 1-indexed into the sorted values, clamped
 * to [1, n]. Chosen over interpolation because funnel time-to-convert values are durations —
 * reporting an actually-observed value is a more defensible summary than an interpolated one
 * that no actor experienced. Returns null for an empty input.
 */
object Percentile {
    fun of(values: List<Long>, p: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = kotlin.math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
