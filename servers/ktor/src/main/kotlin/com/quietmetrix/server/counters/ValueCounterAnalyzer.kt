package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell

/**
 * Sums `value{name}` cells' `n` — an amount in integer minor units, not an occurrence count —
 * per name. Mirrors php-hosting's `valueCounterAnalyze()` — keep the two in sync.
 */
object ValueCounterAnalyzer {

    data class Result(val name: String, val totalMinorUnits: Long)

    fun analyze(cells: List<CounterCell>): List<Result> =
        cells.groupBy { it.dims["name"] }
            .mapNotNull { (name, group) -> name?.let { Result(it, group.sumOf { c -> c.n }) } }
            .sortedByDescending { it.totalMinorUnits }
}
