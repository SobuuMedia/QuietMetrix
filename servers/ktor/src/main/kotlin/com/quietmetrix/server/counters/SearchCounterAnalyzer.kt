package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell

data class SearchScreenResult(val screen: String, val total: Long, val zeroResult: Long, val rate: Double?)

/**
 * Joins `search{screen}` and `search_zero_result{screen}` cells into a per-screen zero-result
 * rate — the query text itself is never sent (see the SDK's `trackSearch`), only whether a
 * search on that screen came back empty. Sorted worst-first: the highest zero-result rate is
 * the most actionable row. Mirrors php-hosting's `searchCounterAnalyze()` — keep in sync.
 */
object SearchCounterAnalyzer {

    fun analyze(searchCells: List<CounterCell>, zeroResultCells: List<CounterCell>): List<SearchScreenResult> {
        val totalByScreen = searchCells.groupBy { it.dims["screen"] }
            .mapNotNull { (screen, group) -> screen?.let { it to group.sumOf { c -> c.n } } }
            .toMap()
        val zeroByScreen = zeroResultCells.groupBy { it.dims["screen"] }
            .mapNotNull { (screen, group) -> screen?.let { it to group.sumOf { c -> c.n } } }
            .toMap()

        return (totalByScreen.keys + zeroByScreen.keys).distinct()
            .map { screen ->
                val total = totalByScreen[screen] ?: 0L
                val zeroResult = zeroByScreen[screen] ?: 0L
                SearchScreenResult(screen, total, zeroResult, ratio(zeroResult, total))
            }
            .sortedByDescending { it.rate ?: -1.0 }
    }

    private fun ratio(zeroResult: Long, total: Long): Double? {
        if (total == 0L) return null
        return zeroResult.toDouble() / total.toDouble()
    }
}
