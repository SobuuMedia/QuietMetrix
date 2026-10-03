package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.*
import com.quietmetrix.dashboard.ui.screens.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Exercises the asynchronous result commit seam against responses from a superseded selection. */
class ExperimentResultRangeTest {
    /** A declared test with a real-shaped thirty-day winning response. */
    private val experiment = ExperimentDto("cta", "CTA", status = "active", variants = listOf(VariantDto("a", 50), VariantDto("b", 50)))

    /** The awaited result content deliberately has a convincing winner. */
    private val result = ExperimentResultsResponse(experiment, comparison = ExperimentComparisonDto("a", "b", 3.0, .001, true, "b"))

    /** A late thirty-day response cannot replace the newer seven-day result or authorize shipping. */
    @Test
    fun delayedPreviousRangeCannotCommit() {
        val sevenDays = DashboardState(currentProjectId = "p", selectedExperimentKey = "cta", range = TimeRange.Day7)
        val rejected = sevenDays.acceptExperimentResults(result, "p", "cta", TimeRange.Day30)
        assertSame(sevenDays, rejected)
        assertNull(currentExperimentResults(rejected, experiment))
        assertNull(selectedVariantFor(ExperimentAction.Ship, experiment, currentExperimentResults(rejected, experiment)?.comparison))
        assertEquals(ExperimentAction.KeepBaseline, detailActions(experiment, currentExperimentResults(rejected, experiment)?.comparison, true).first())
        val accepted = sevenDays.acceptExperimentResults(result, "p", "cta", TimeRange.Day7)
        assertEquals(TimeRange.Day7, accepted.experimentResultsRange)
        assertEquals("b", selectedVariantFor(ExperimentAction.Ship, experiment, currentExperimentResults(accepted, experiment)?.comparison))
        assertSame(accepted, accepted.acceptExperimentResults(result, "other-project", "cta", TimeRange.Day7))
        assertSame(accepted, accepted.acceptExperimentResults(result, "p", "other-experiment", TimeRange.Day7))
    }
}
