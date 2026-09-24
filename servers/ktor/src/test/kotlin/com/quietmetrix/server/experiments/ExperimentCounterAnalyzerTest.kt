package com.quietmetrix.server.experiments

import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.ExperimentRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentCounterAnalyzerTest {

    private fun cell(exp: String, variant: String, action: String, n: Long) =
        CounterRepository.CounterCell(dims = mapOf("exp" to exp, "variant" to variant, "action" to action), n = n, devices = n)

    private fun experiment(key: String = "checkout_cta") = ExperimentRecord(
        experimentKey = key,
        name = "Checkout button colour",
        screen = "Checkout",
        status = "active",
        trafficPercent = 100,
        variants = listOf(VariantSpec("a", 50), VariantSpec("b", 50)),
        countries = null,
        createdAt = "2026-09-24T10:00:00",
        updatedAt = "2026-09-24T10:00:00",
        startedAt = "2026-09-24T10:00:00",
        endedAt = null,
    )

    @Test
    fun `both variants present with enough sample produces rates and a comparison`() {
        val cells = listOf(
            cell("checkout_cta", "a", "impression", 1000),
            cell("checkout_cta", "a", "interaction", 100),
            cell("checkout_cta", "b", "impression", 1000),
            cell("checkout_cta", "b", "interaction", 130),
        )
        val analysis = ExperimentCounterAnalyzer.analyze(experiment(), cells)

        assertEquals(2, analysis.variants.size)
        assertEquals(0.1, analysis.variants[0].rate)
        assertEquals(0.13, analysis.variants[1].rate)

        val comparison = assertNotNull(analysis.comparison)
        assertEquals("a", comparison.baseline)
        assertEquals("b", comparison.challenger)
        assertTrue(comparison.significant)
        assertEquals("b", comparison.winner)
    }

    @Test
    fun `a missing interaction cell yields a null rate and no comparison`() {
        val cells = listOf(
            cell("checkout_cta", "a", "impression", 1000),
            cell("checkout_cta", "a", "interaction", 100),
            cell("checkout_cta", "b", "impression", 1000),
            // no "b" interaction cell -- hidden by k-anonymity or no interactions yet
        )
        val analysis = ExperimentCounterAnalyzer.analyze(experiment(), cells)

        assertEquals(1000L, analysis.variants[1].impressions)
        assertNull(analysis.variants[1].interactions)
        assertNull(analysis.variants[1].rate)
        assertNull(analysis.comparison)
    }

    @Test
    fun `below the minimum sample size yields no comparison even with both rates present`() {
        val cells = listOf(
            cell("checkout_cta", "a", "impression", 29),
            cell("checkout_cta", "a", "interaction", 5),
            cell("checkout_cta", "b", "impression", 1000),
            cell("checkout_cta", "b", "interaction", 130),
        )
        val analysis = ExperimentCounterAnalyzer.analyze(experiment(), cells)
        assertNotNull(analysis.variants[0].rate)
        assertNull(analysis.comparison)
    }

    @Test
    fun `cells for another experiment key are ignored`() {
        val cells = listOf(
            cell("other_experiment", "a", "impression", 1000),
            cell("other_experiment", "a", "interaction", 500),
        )
        val analysis = ExperimentCounterAnalyzer.analyze(experiment(), cells)
        assertNull(analysis.variants[0].impressions)
        assertNull(analysis.variants[0].rate)
        assertNull(analysis.variants[1].impressions)
    }
}
