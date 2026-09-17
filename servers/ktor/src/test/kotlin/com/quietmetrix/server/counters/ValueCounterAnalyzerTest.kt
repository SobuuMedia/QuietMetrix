package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ValueCounterAnalyzer] sums `value{name}` cells' `n` (an amount, not an occurrence count) per
 * name. Mirrors php-hosting's `valueCounterAnalyze()` -- keep the two in sync.
 */
class ValueCounterAnalyzerTest {

    private fun valueCell(name: String, n: Long) = CounterCell(mapOf("name" to name), n, n)

    @Test
    fun `sums amounts per name`() {
        val cells = listOf(valueCell("purchase", 499), valueCell("purchase", 999))
        val result = ValueCounterAnalyzer.analyze(cells)
        assertEquals(1, result.size)
        assertEquals("purchase", result[0].name)
        assertEquals(1498L, result[0].totalMinorUnits)
    }

    @Test
    fun `distinct names produce distinct results`() {
        val cells = listOf(valueCell("purchase", 499), valueCell("subscription", 999))
        val result = ValueCounterAnalyzer.analyze(cells)
        assertEquals(setOf("purchase" to 499L, "subscription" to 999L), result.map { it.name to it.totalMinorUnits }.toSet())
    }

    @Test
    fun `sorts by total amount descending`() {
        val cells = listOf(valueCell("small", 100), valueCell("big", 100_000))
        val result = ValueCounterAnalyzer.analyze(cells)
        assertEquals(listOf("big", "small"), result.map { it.name })
    }

    @Test
    fun `an empty range reports no results`() {
        assertEquals(emptyList(), ValueCounterAnalyzer.analyze(emptyList()))
    }
}
