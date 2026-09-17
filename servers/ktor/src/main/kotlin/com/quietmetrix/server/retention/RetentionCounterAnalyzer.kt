package com.quietmetrix.server.retention

/**
 * One `retention` counter cell (see the SDK's `RetentionReporter`): [day] `0` is the
 * cohort-size signal, every other tracked value (1/3/7/14/30) is an "at least N days later"
 * mark. [n] is the summed count from `CounterRepository.readCells`, already k-anonymity
 * gated.
 */
data class RetentionCounterCell(val cohort: String, val day: Int, val n: Long)

data class RetentionCohortResult(
    val cohort: String,
    val size: Int,
    val day1: Double?,
    val day3: Double?,
    val day7: Double?,
    val day14: Double?,
    val day30: Double?,
)

/**
 * Aggregates `retention{cohort, day}` cells into per-cohort size and day-N percentages.
 * Mirrors php-hosting's `retentionCounterAnalyze()` — keep the two in sync.
 *
 * Unlike the old session-based cohort model, a cohort's `size` here is an exact count of
 * devices that reported joining it (day="0"), not an inference from session first-seen dates
 * — and day-N counts can never exceed it, since both come from the same persisted per-device
 * signal (see RetentionReporter's "at least N days later, once per mark, ever" semantics).
 */
object RetentionCounterAnalyzer {

    fun analyze(cells: List<RetentionCounterCell>): List<RetentionCohortResult> =
        cells.groupBy { it.cohort }
            .map { (cohort, group) ->
                val byDay = group.associate { it.day to it.n }
                val size = (byDay[0] ?: 0L).toInt()
                RetentionCohortResult(
                    cohort = cohort,
                    size = size,
                    day1 = ratio(byDay[1], size),
                    day3 = ratio(byDay[3], size),
                    day7 = ratio(byDay[7], size),
                    day14 = ratio(byDay[14], size),
                    day30 = ratio(byDay[30], size),
                )
            }
            .sortedBy { it.cohort }

    private fun ratio(n: Long?, size: Int): Double? {
        if (n == null || size == 0) return null
        return n.toDouble() / size.toDouble()
    }
}
