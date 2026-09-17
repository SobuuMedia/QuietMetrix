package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [FrictionCounterAnalyzer] sums `friction{screen, kind="rage_tap"}` cells' `n` per screen.
 * Mirrors php-hosting's `frictionCounterAnalyze()` -- keep the two in sync.
 */
class FrictionCounterAnalyzerTest {

    private fun cell(screen: String, kind: String, n: Long) = CounterCell(mapOf("screen" to screen, "kind" to kind), n, n)

    @Test
    fun `sums rage taps per screen`() {
        val cells = listOf(cell("Checkout", "rage_tap", 5), cell("Checkout", "rage_tap", 3))
        val result = FrictionCounterAnalyzer.analyze(cells)
        assertEquals(1, result.size)
        assertEquals("Checkout", result[0].screen)
        assertEquals(8L, result[0].rageTaps)
    }

    @Test
    fun `distinct screens produce distinct results`() {
        val cells = listOf(cell("Checkout", "rage_tap", 5), cell("Home", "rage_tap", 2))
        val result = FrictionCounterAnalyzer.analyze(cells)
        assertEquals(setOf("Checkout" to 5L, "Home" to 2L), result.map { it.screen to it.rageTaps }.toSet())
    }

    @Test
    fun `sorts by rage-tap count descending`() {
        val cells = listOf(cell("Quiet", "rage_tap", 1), cell("Loud", "rage_tap", 50))
        val result = FrictionCounterAnalyzer.analyze(cells)
        assertEquals(listOf("Loud", "Quiet"), result.map { it.screen })
    }

    @Test
    fun `an empty range reports no results`() {
        assertEquals(emptyList(), FrictionCounterAnalyzer.analyze(emptyList()))
    }
}
