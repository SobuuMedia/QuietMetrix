package com.quietmetrix.server.counters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [ActivationCounterAnalyzer] joins `activation{cohort}` cells against the same per-cohort
 * size [com.quietmetrix.server.retention.RetentionCounterAnalyzer] already computes from
 * retention's `day="0"` signal, so an activation rate has the identical denominator a
 * retention rate for the same cohort would. Mirrors php-hosting's
 * `activationCounterAnalyze()` -- keep the two in sync.
 */
class ActivationCounterAnalyzerTest {

    @Test
    fun `computes activation rate against the cohort size`() {
        val cells = listOf(ActivationCounterCell("2026-W36", 30))
        val result = ActivationCounterAnalyzer.analyze(cells, mapOf("2026-W36" to 100))
        assertEquals(1, result.size)
        assertEquals("2026-W36", result[0].cohort)
        assertEquals(100, result[0].size)
        assertEquals(30, result[0].activated)
        assertEquals(0.3, result[0].rate)
    }

    @Test
    fun `a cohort with no activation cells still appears, with a zero rate`() {
        val result = ActivationCounterAnalyzer.analyze(emptyList(), mapOf("2026-W36" to 50))
        assertEquals(1, result.size)
        assertEquals(0, result[0].activated)
        assertEquals(0.0, result[0].rate)
    }

    @Test
    fun `a zero-size cohort reports a null rate rather than dividing by zero`() {
        val result = ActivationCounterAnalyzer.analyze(emptyList(), mapOf("2026-W36" to 0))
        assertNull(result[0].rate)
    }

    @Test
    fun `distinct cohorts sum activations independently and sort by cohort label`() {
        val cells = listOf(
            ActivationCounterCell("2026-W37", 5),
            ActivationCounterCell("2026-W36", 10),
        )
        val result = ActivationCounterAnalyzer.analyze(cells, mapOf("2026-W36" to 20, "2026-W37" to 20))
        assertEquals(listOf("2026-W36", "2026-W37"), result.map { it.cohort })
        assertEquals(10, result[0].activated)
        assertEquals(5, result[1].activated)
    }

    @Test
    fun `multiple cells for the same cohort sum together`() {
        val cells = listOf(ActivationCounterCell("2026-W36", 10), ActivationCounterCell("2026-W36", 5))
        val result = ActivationCounterAnalyzer.analyze(cells, mapOf("2026-W36" to 30))
        assertEquals(15, result[0].activated)
    }
}
