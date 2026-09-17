package com.quietmetrix.server.counters

/** One `activation{cohort}` counter cell (see the SDK's `ActivationReporter`). [n] is the
 *  summed count from `CounterRepository.readCells`, already k-anonymity gated. */
data class ActivationCounterCell(val cohort: String, val n: Long)

data class ActivationCohortResult(val cohort: String, val size: Int, val activated: Int, val rate: Double?)

/**
 * Joins `activation{cohort}` cells against [cohortSizes] — each cohort's device count from
 * retention's `day="0"` signal (see [com.quietmetrix.server.retention.RetentionCounterAnalyzer]) —
 * so an activation rate has the identical denominator a retention rate for the same cohort
 * would. Every cohort in [cohortSizes] appears in the result, even with zero activations, so a
 * dashboard cohort table doesn't silently drop a row. Mirrors php-hosting's
 * `activationCounterAnalyze()` — keep the two in sync.
 */
object ActivationCounterAnalyzer {

    fun analyze(cells: List<ActivationCounterCell>, cohortSizes: Map<String, Int>): List<ActivationCohortResult> {
        val activatedByCohort = cells.groupBy { it.cohort }.mapValues { (_, group) -> group.sumOf { it.n }.toInt() }
        return cohortSizes.entries
            .map { (cohort, size) ->
                val activated = activatedByCohort[cohort] ?: 0
                ActivationCohortResult(cohort, size, activated, ratio(activated, size))
            }
            .sortedBy { it.cohort }
    }

    private fun ratio(activated: Int, size: Int): Double? {
        if (size == 0) return null
        return activated.toDouble() / size.toDouble()
    }
}
