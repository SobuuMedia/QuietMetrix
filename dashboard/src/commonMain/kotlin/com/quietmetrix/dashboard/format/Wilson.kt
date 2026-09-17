package com.quietmetrix.dashboard.format

import kotlin.math.round
import kotlin.math.sqrt

/**
 * A confidence interval on a rate, e.g. from [wilsonInterval]. [lower]/[upper] are fractions
 * in `[0, 1]`.
 */
data class WilsonInterval(val lower: Double, val upper: Double)

/** z-scores for the confidence levels this dashboard offers. */
private val Z_SCORES = mapOf(
    0.80 to 1.2815515655446004,
    0.90 to 1.6448536269514722,
    0.95 to 1.959963984540054,
    0.99 to 2.5758293035489004,
)

/**
 * The Wilson score confidence interval for [successes] out of [total] — the honest-error-bars
 * primitive every rate in this dashboard is shown with by default. Sampling and thresholds
 * introduce real uncertainty; a naive `successes/total` percentage hides it, especially at the
 * small sample sizes an indie app actually has (a "40%" from 5 of 10 devices and a "40%" from
 * 400 of 1000 are not the same claim). Unlike a naive normal-approximation interval, Wilson
 * never extends outside `[0, 1]` and stays sane even at 0% or 100% observed.
 *
 * Returns null when [total] is 0 — a rate with no sample has no interval, not a degenerate one.
 */
fun wilsonInterval(successes: Long, total: Long, confidence: Double = 0.95): WilsonInterval? {
    if (total <= 0) return null
    val z = Z_SCORES[confidence] ?: Z_SCORES.getValue(0.95)
    val n = total.toDouble()
    val p = successes.toDouble() / n
    val zSquaredOverN = z * z / n
    val denominator = 1.0 + zSquaredOverN
    val center = (p + zSquaredOverN / 2.0) / denominator
    val margin = (z / denominator) * sqrt(p * (1.0 - p) / n + zSquaredOverN / (4.0 * n))
    return WilsonInterval(
        lower = (center - margin).coerceIn(0.0, 1.0),
        upper = (center + margin).coerceIn(0.0, 1.0),
    )
}

/** [WilsonInterval] -> `"37–43%"`, whole-percent bounds. */
fun formatWilsonRange(interval: WilsonInterval): String {
    val lowerPct = round(interval.lower * 100).toInt()
    val upperPct = round(interval.upper * 100).toInt()
    return "$lowerPct–$upperPct%"
}
