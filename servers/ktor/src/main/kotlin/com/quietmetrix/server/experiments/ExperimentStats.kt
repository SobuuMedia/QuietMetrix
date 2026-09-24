package com.quietmetrix.server.experiments

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Two-proportion z-test statistics, mirrored exactly by php-hosting/src/experimentStats.php —
 *  both backends must agree on a given input's p-value to the vectors in ExperimentStatsTest. */
object ExperimentStats {
    const val SIGNIFICANCE_LEVEL = 0.05

    /** [z] and [pValue] for a two-sided test; [significant] is `pValue < SIGNIFICANCE_LEVEL`. */
    data class ZTestResult(val z: Double, val pValue: Double, val significant: Boolean)

    /**
     * Two-proportion z-test comparing [c2]/[n2] (challenger) against [c1]/[n1] (baseline).
     * Returns null when the standard error is 0 (the pooled proportion is exactly 0 or 1, or
     * either sample is empty) — there is nothing to compare.
     */
    fun twoProportionZTest(c1: Long, n1: Long, c2: Long, n2: Long): ZTestResult? {
        if (n1 <= 0 || n2 <= 0) return null
        val p1 = c1.toDouble() / n1
        val p2 = c2.toDouble() / n2
        val pooled = (c1 + c2).toDouble() / (n1 + n2)
        val se = sqrt(pooled * (1.0 - pooled) * (1.0 / n1 + 1.0 / n2))
        if (se == 0.0) return null
        val z = (p2 - p1) / se
        val pValue = 2.0 * (1.0 - normalCdf(abs(z)))
        return ZTestResult(z, pValue, pValue < SIGNIFICANCE_LEVEL)
    }

    /** Standard normal CDF via `erf`. */
    fun normalCdf(x: Double): Double = 0.5 * (1.0 + erf(x / sqrt(2.0)))

    /** Abramowitz & Stegun formula 7.1.26 approximation of the error function (max error ~1.5e-7). */
    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-ax * ax)
        return sign * y
    }
}
