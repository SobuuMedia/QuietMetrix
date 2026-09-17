package com.quietmetrix.server.retention

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [RetentionCounterAnalyzer] aggregates `retention{cohort, day}` cells (see the SDK's
 * RetentionReporter) into per-cohort size + day-N percentages. day="0" is the cohort-size
 * signal (every device reports it once, on first launch); day-N marks are "at least N days
 * later," so a cohort's day-N count can never exceed its size.
 */
class RetentionCounterAnalyzerTest {

    private fun cell(cohort: String, day: Int, n: Long) = RetentionCounterCell(cohort, day, n)

    @Test
    fun `size comes from the day-0 cell`() {
        val result = RetentionCounterAnalyzer.analyze(listOf(cell("2026-W36", 0, 100)))
        assertEquals(100, result.single().size)
    }

    @Test
    fun `day-N percentages are n over the cohort size`() {
        val result = RetentionCounterAnalyzer.analyze(
            listOf(cell("2026-W36", 0, 100), cell("2026-W36", 1, 40), cell("2026-W36", 7, 10)),
        )
        val cohort = result.single()
        assertEquals(0.4, cohort.day1)
        assertEquals(0.1, cohort.day7)
        assertNull(cohort.day3)
        assertNull(cohort.day14)
        assertNull(cohort.day30)
    }

    @Test
    fun `a cohort with no size (no day-0 cell) has null percentages, not a divide-by-zero`() {
        val result = RetentionCounterAnalyzer.analyze(listOf(cell("2026-W36", 7, 10)))
        val cohort = result.single()
        assertEquals(0, cohort.size)
        assertNull(cohort.day7)
    }

    @Test
    fun `multiple cohorts are kept separate and sorted by cohort label`() {
        val result = RetentionCounterAnalyzer.analyze(
            listOf(cell("2026-W37", 0, 50), cell("2026-W36", 0, 100)),
        )
        assertEquals(listOf("2026-W36", "2026-W37"), result.map { it.cohort })
    }

    @Test
    fun `an empty cell list produces an empty result`() {
        assertEquals(emptyList(), RetentionCounterAnalyzer.analyze(emptyList()))
    }
}
