package com.quietmetrix.server.funnels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [FunnelCounterAnalyzer] is the counter-based replacement for [FunnelAnalyzer]: instead of
 * matching a raw event trail, it aggregates the `funnel_step{f, rev, step}` cells the device
 * itself already reduced its progress to. `step` is the 1-based depth reached, recorded once
 * per device per depth (see the SDK's FunnelEvaluator), so summing `n` at each depth directly
 * gives the step counts a funnel report needs — no per-actor reconstruction required.
 */
class FunnelCounterAnalyzerTest {

    private fun steps(n: Int) = (1..n).map { FunnelStepDefinition(key = "step$it", event = "e$it") }

    @Test
    fun `counts at each depth become each step's count`() {
        val result = FunnelCounterAnalyzer.analyze(
            steps = steps(3),
            cells = listOf(FunnelStepCell(1, 100), FunnelStepCell(2, 60), FunnelStepCell(3, 20)),
        )
        assertEquals(listOf(100, 60, 20), result.steps.map { it.count })
        assertEquals(100, result.entered)
        assertEquals(20, result.converted)
    }

    @Test
    fun `conversion and drop rates are computed from entry and from the previous step`() {
        val result = FunnelCounterAnalyzer.analyze(
            steps = steps(3),
            cells = listOf(FunnelStepCell(1, 100), FunnelStepCell(2, 50), FunnelStepCell(3, 25)),
        )
        assertEquals(1.0, result.steps[0].conversionFromEntry)
        assertEquals(0.5, result.steps[1].conversionFromEntry)
        assertEquals(0.25, result.steps[2].conversionFromEntry)

        assertEquals(0.0, result.steps[0].conversionFromPrevious) // no previous step
        assertEquals(0.5, result.steps[1].conversionFromPrevious)
        assertEquals(0.5, result.steps[2].conversionFromPrevious)

        assertEquals(0, result.steps[0].dropped)
        assertEquals(50, result.steps[1].dropped)
        assertEquals(25, result.steps[2].dropped)
        assertEquals(0.5, result.steps[1].dropRate)
    }

    @Test
    fun `overall conversion is converted over entered`() {
        val result = FunnelCounterAnalyzer.analyze(
            steps = steps(2),
            cells = listOf(FunnelStepCell(1, 40), FunnelStepCell(2, 10)),
        )
        assertEquals(0.25, result.overallConversion)
    }

    @Test
    fun `a step with no cell at all reached counts as zero`() {
        val result = FunnelCounterAnalyzer.analyze(
            steps = steps(3),
            cells = listOf(FunnelStepCell(1, 10)), // nobody (past k-anon) reached step 2 or 3
        )
        assertEquals(listOf(10, 0, 0), result.steps.map { it.count })
        assertEquals(0.0, result.overallConversion)
    }

    @Test
    fun `cells for an out-of-range step index are ignored`() {
        // e.g. leftover data from a since-shortened funnel definition.
        val result = FunnelCounterAnalyzer.analyze(
            steps = steps(2),
            cells = listOf(FunnelStepCell(1, 10), FunnelStepCell(5, 3)),
        )
        assertEquals(listOf(10, 0), result.steps.map { it.count })
    }

    @Test
    fun `no timing or breakdown data is available from counters`() {
        val result = FunnelCounterAnalyzer.analyze(steps = steps(2), cells = listOf(FunnelStepCell(1, 5)))
        assertNull(result.medianTotalMs)
        assertNull(result.breakdown)
        assertNull(result.trend)
        assertNull(result.steps[0].medianMsFromPrevious)
        assertNull(result.steps[0].p90MsFromPrevious)
    }

    @Test
    fun `countedBy reflects attempt mode`() {
        assertEquals("install", FunnelCounterAnalyzer.analyze(steps(1), emptyList(), countMode = "actor").countedBy)
        assertEquals("install attempt", FunnelCounterAnalyzer.analyze(steps(1), emptyList(), countMode = "attempt").countedBy)
    }

    @Test
    fun `an empty step list produces an empty, zeroed result`() {
        val result = FunnelCounterAnalyzer.analyze(steps = emptyList(), cells = listOf(FunnelStepCell(1, 10)))
        assertEquals(0, result.entered)
        assertEquals(0, result.converted)
        assertEquals(0.0, result.overallConversion)
        assertEquals(emptyList(), result.steps)
    }
}
