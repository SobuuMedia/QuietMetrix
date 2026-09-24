package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.ExperimentComparisonDto
import com.quietmetrix.dashboard.api.ExperimentDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentsLogicTest {

    private fun experiment(key: String, status: String = "draft") = ExperimentDto(key = key, name = key, status = status)

    @Test
    fun currentExperimentPicksTheMatchingKey() {
        val experiments = listOf(experiment("a"), experiment("b"))
        assertEquals("b", currentExperiment(experiments, "b")?.key)
    }

    @Test
    fun currentExperimentFallsBackToFirstWhenSelectedKeyIsMissing() {
        val experiments = listOf(experiment("a"), experiment("b"))
        assertEquals("a", currentExperiment(experiments, "does-not-exist")?.key)
        assertEquals("a", currentExperiment(experiments, null)?.key)
    }

    @Test
    fun currentExperimentIsNullForAnEmptyList() {
        assertNull(currentExperiment(emptyList(), "a"))
    }

    @Test
    fun validExperimentKeysAreLowercaseSlugs() {
        assertTrue(isValidExperimentKey("checkout_cta"))
        assertTrue(isValidExperimentKey("a"))
        assertFalse(isValidExperimentKey("Checkout CTA"))
        assertFalse(isValidExperimentKey(""))
        assertFalse(isValidExperimentKey("a".repeat(33)))
    }

    @Test
    fun validVariantNamesRejectNoneAndBadCharacters() {
        assertTrue(isValidVariantName("a"))
        assertTrue(isValidVariantName("variant_b"))
        assertFalse(isValidVariantName("none"))
        assertFalse(isValidVariantName("Variant A"))
        assertFalse(isValidVariantName(""))
    }

    @Test
    fun parseCountryListAcceptsCommaAndSpaceSeparated() {
        val result = parseCountryList("es, FR  it")
        assertEquals(listOf("ES", "FR", "IT"), result.normalized)
        assertTrue(result.invalidTokens.isEmpty())
    }

    @Test
    fun parseCountryListDedupes() {
        val result = parseCountryList("ES, es, Es")
        assertEquals(listOf("ES"), result.normalized)
    }

    @Test
    fun parseCountryListBlankIsEmptyAndValid() {
        val result = parseCountryList("")
        assertTrue(result.normalized.isEmpty())
        assertTrue(result.invalidTokens.isEmpty())
    }

    @Test
    fun parseCountryListFlagsInvalidTokens() {
        val result = parseCountryList("ES, USA, xx1")
        assertEquals(listOf("ES"), result.normalized)
        assertEquals(listOf("USA", "xx1"), result.invalidTokens)
    }

    @Test
    fun formIsValidWithWellFormedInputs() {
        assertTrue(isExperimentFormValid("checkout_cta", "Checkout button colour", "a", "b", 50, 100, ""))
        assertTrue(isExperimentFormValid("checkout_cta", "Checkout button colour", "a", "b", 50, 100, "ES, FR"))
    }

    @Test
    fun formIsInvalidWithABadKey() {
        assertFalse(isExperimentFormValid("Bad Key", "Name", "a", "b", 50, 100, ""))
    }

    @Test
    fun formIsInvalidWithABlankName() {
        assertFalse(isExperimentFormValid("checkout_cta", "  ", "a", "b", 50, 100, ""))
    }

    @Test
    fun formIsInvalidWithIdenticalVariantNames() {
        assertFalse(isExperimentFormValid("checkout_cta", "Name", "a", "a", 50, 100, ""))
    }

    @Test
    fun formIsInvalidWithAVariantNamedNone() {
        assertFalse(isExperimentFormValid("checkout_cta", "Name", "none", "b", 50, 100, ""))
    }

    @Test
    fun formIsInvalidWithSplitOutOfRange() {
        assertFalse(isExperimentFormValid("checkout_cta", "Name", "a", "b", 0, 100, ""))
        assertFalse(isExperimentFormValid("checkout_cta", "Name", "a", "b", 100, 100, ""))
    }

    @Test
    fun formIsInvalidWithTrafficOutOfRange() {
        assertFalse(isExperimentFormValid("checkout_cta", "Name", "a", "b", 50, 0, ""))
        assertFalse(isExperimentFormValid("checkout_cta", "Name", "a", "b", 50, 101, ""))
    }

    @Test
    fun formIsInvalidWithUnparseableCountries() {
        assertFalse(isExperimentFormValid("checkout_cta", "Name", "a", "b", 50, 100, "not-a-country"))
    }

    @Test
    fun availableActionsForDraft() {
        assertEquals(listOf(ExperimentAction.Start, ExperimentAction.Delete), availableActions("draft", canWrite = true))
    }

    @Test
    fun availableActionsForActive() {
        assertEquals(listOf(ExperimentAction.Pause, ExperimentAction.End), availableActions("active", canWrite = true))
    }

    @Test
    fun availableActionsForPaused() {
        assertEquals(listOf(ExperimentAction.Resume, ExperimentAction.End), availableActions("paused", canWrite = true))
    }

    @Test
    fun availableActionsForEndedIsEmpty() {
        assertTrue(availableActions("ended", canWrite = true).isEmpty())
    }

    @Test
    fun availableActionsIsEmptyWhenCannotWrite() {
        assertTrue(availableActions("draft", canWrite = false).isEmpty())
        assertTrue(availableActions("active", canWrite = false).isEmpty())
    }

    @Test
    fun usageLabelArgsIsNullWhenUnlimited() {
        assertNull(usageLabelArgs(1, null))
    }

    @Test
    fun usageLabelArgsPairsCountAndLimit() {
        assertEquals(1 to 2, usageLabelArgs(1, 2))
    }

    @Test
    fun shouldAutoOpenGuideOnlyWhenNotSeen() {
        assertTrue(shouldAutoOpenGuide(seen = false))
        assertFalse(shouldAutoOpenGuide(seen = true))
    }

    @Test
    fun guideStepAfterClampsAtTheLastStep() {
        assertEquals(1, guideStepAfter(0, EXPERIMENTS_GUIDE_STEP_COUNT))
        assertEquals(EXPERIMENTS_GUIDE_STEP_COUNT - 1, guideStepAfter(EXPERIMENTS_GUIDE_STEP_COUNT - 1, EXPERIMENTS_GUIDE_STEP_COUNT))
    }

    @Test
    fun guideStepBeforeClampsAtTheFirstStep() {
        assertEquals(0, guideStepBefore(1))
        assertEquals(0, guideStepBefore(0))
    }

    @Test
    fun isLastGuideStepDetectsTheFinalStep() {
        assertTrue(isLastGuideStep(EXPERIMENTS_GUIDE_STEP_COUNT - 1, EXPERIMENTS_GUIDE_STEP_COUNT))
        assertFalse(isLastGuideStep(0, EXPERIMENTS_GUIDE_STEP_COUNT))
    }

    @Test
    fun verdictForNullComparisonIsNotEnoughData() {
        assertEquals(ExperimentVerdict.NotEnoughData, verdictFor(null))
    }

    @Test
    fun verdictForNotSignificantIsNoDifference() {
        val comparison = ExperimentComparisonDto("a", "b", z = 0.2, pValue = 0.83, significant = false)
        val verdict = verdictFor(comparison)
        assertIs<ExperimentVerdict.NoDifference>(verdict)
        assertEquals(0.83, verdict.pValue)
    }

    @Test
    fun verdictForSignificantIsWinner() {
        val comparison = ExperimentComparisonDto("a", "b", z = 2.1, pValue = 0.035, significant = true, winner = "b")
        val verdict = verdictFor(comparison)
        assertIs<ExperimentVerdict.Winner>(verdict)
        assertEquals("b", verdict.variantName)
        assertEquals(0.035, verdict.pValue)
    }

    @Test
    fun formatPValueRoundsToThreeDecimals() {
        assertEquals("0.036", formatPValue(0.0355))
    }

    @Test
    fun formatPValueBelowThresholdShowsLessThan() {
        assertEquals("< 0.001", formatPValue(0.0001))
        assertEquals("< 0.001", formatPValue(0.0))
    }

    @Test
    fun formatPValueAtTheThresholdIsNotLessThan() {
        assertEquals("0.001", formatPValue(0.001))
    }
}
