package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [SearchCounterAnalyzer] joins `search{screen}` and `search_zero_result{screen}` cells into a
 * per-screen zero-result rate — never the query text itself, which is never sent. Mirrors
 * php-hosting's `searchCounterAnalyze()` -- keep the two in sync.
 */
class SearchCounterAnalyzerTest {

    private fun cell(screen: String, n: Long) = CounterCell(mapOf("screen" to screen), n, n)

    @Test
    fun `computes the zero-result rate per screen`() {
        val result = SearchCounterAnalyzer.analyze(
            searchCells = listOf(cell("Library", 100)),
            zeroResultCells = listOf(cell("Library", 25)),
        )
        assertEquals(1, result.size)
        assertEquals("Library", result[0].screen)
        assertEquals(100L, result[0].total)
        assertEquals(25L, result[0].zeroResult)
        assertEquals(0.25, result[0].rate)
    }

    @Test
    fun `a screen with searches but no zero-result cell reports a zero rate`() {
        val result = SearchCounterAnalyzer.analyze(
            searchCells = listOf(cell("Library", 50)),
            zeroResultCells = emptyList(),
        )
        assertEquals(0L, result[0].zeroResult)
        assertEquals(0.0, result[0].rate)
    }

    @Test
    fun `a screen with zero total searches reports a null rate rather than dividing by zero`() {
        // Not expected in practice (a zero-result cell implies at least one search), but the
        // analyzer must not crash if the counter data is ever inconsistent.
        val result = SearchCounterAnalyzer.analyze(searchCells = emptyList(), zeroResultCells = listOf(cell("Library", 1)))
        assertEquals(1, result.size)
        assertNull(result[0].rate)
    }

    @Test
    fun `sorts worst-first by zero-result rate descending`() {
        val result = SearchCounterAnalyzer.analyze(
            searchCells = listOf(cell("Good", 100), cell("Bad", 100)),
            zeroResultCells = listOf(cell("Good", 5), cell("Bad", 80)),
        )
        assertEquals(listOf("Bad", "Good"), result.map { it.screen })
    }

    @Test
    fun `an empty range reports no results`() {
        assertEquals(emptyList(), SearchCounterAnalyzer.analyze(emptyList(), emptyList()))
    }
}
