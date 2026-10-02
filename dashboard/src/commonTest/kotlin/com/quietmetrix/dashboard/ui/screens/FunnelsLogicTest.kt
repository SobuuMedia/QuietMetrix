package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.FunnelSummaryDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunnelsLogicTest {

    private fun funnel(key: String) = FunnelDto(funnelKey = key, name = key)

    private fun results(entered: Int, converted: Int, overallConversion: Double) = FunnelResultsResponse(
        funnel = FunnelSummaryDto(funnelKey = "f", name = "f"),
        entered = entered,
        converted = converted,
        overallConversion = overallConversion,
    )

    @Test
    fun currentFunnelPicksTheMatchingKey() {
        val funnels = listOf(funnel("a"), funnel("b"))
        assertEquals("b", currentFunnel(funnels, "b")?.funnelKey)
    }

    @Test
    fun currentFunnelFallsBackToFirstWhenSelectedKeyIsMissing() {
        val funnels = listOf(funnel("a"), funnel("b"))
        assertEquals("a", currentFunnel(funnels, "does-not-exist")?.funnelKey)
        assertEquals("a", currentFunnel(funnels, null)?.funnelKey)
    }

    @Test
    fun currentFunnelIsNullForAnEmptyList() {
        assertNull(currentFunnel(emptyList(), "a"))
    }

    @Test
    fun validFunnelKeysAreLowercaseSlugs() {
        assertTrue(isValidFunnelKey("signup"))
        assertTrue(isValidFunnelKey("signup-v2_beta"))
        assertFalse(isValidFunnelKey("Sign Up"))
        assertFalse(isValidFunnelKey(""))
        assertFalse(isValidFunnelKey("a".repeat(65)))
    }

    @Test
    fun formIsValidWithAWellFormedTwoStepFunnel() {
        assertTrue(isFunnelFormValid("signup", "Signup", listOf("screen_view", "signup_submitted")))
    }

    @Test
    fun formIsInvalidWithABadKey() {
        assertFalse(isFunnelFormValid("Bad Key", "Signup", listOf("a", "b")))
    }

    @Test
    fun formIsInvalidWithABlankName() {
        assertFalse(isFunnelFormValid("signup", "  ", listOf("a", "b")))
    }

    @Test
    fun formIsInvalidWithFewerThanTwoSteps() {
        assertFalse(isFunnelFormValid("signup", "Signup", listOf("a")))
    }

    @Test
    fun formIsInvalidWithABlankStepEvent() {
        assertFalse(isFunnelFormValid("signup", "Signup", listOf("a", "  ")))
    }

    // ---- funnelHeadlineOutOfTen (Task 27) ----

    @Test
    fun headlineOutOfTenIsNullWhenResultsHaveNotLoaded() {
        assertNull(funnelHeadlineOutOfTen(null))
    }

    @Test
    fun headlineOutOfTenIsNullWithZeroEntrants() {
        assertNull(funnelHeadlineOutOfTen(results(entered = 0, converted = 0, overallConversion = 0.0)))
    }

    @Test
    fun headlineOutOfTenScalesTheRealConversionRatio() {
        // 80/200 = 0.40 -> 4 out of 10, same example shape as the design handoff's
        // "Out of 10 people who open Shelves, 4 end up adding a book."
        assertEquals(4, funnelHeadlineOutOfTen(results(entered = 200, converted = 80, overallConversion = 0.4)))
    }

    @Test
    fun headlineOutOfTenRoundsToTheNearestWholeNumber() {
        // 1/3 = 0.333... -> 3.33 rounds down to 3.
        assertEquals(3, funnelHeadlineOutOfTen(results(entered = 3, converted = 1, overallConversion = 1.0 / 3.0)))
        // 2/3 = 0.666... -> 6.66 rounds up to 7.
        assertEquals(7, funnelHeadlineOutOfTen(results(entered = 3, converted = 2, overallConversion = 2.0 / 3.0)))
    }

    @Test
    fun headlineOutOfTenIsZeroWhenNobodyConverted() {
        assertEquals(0, funnelHeadlineOutOfTen(results(entered = 50, converted = 0, overallConversion = 0.0)))
    }

    @Test
    fun headlineOutOfTenIsTenWhenEveryoneConverted() {
        assertEquals(10, funnelHeadlineOutOfTen(results(entered = 50, converted = 50, overallConversion = 1.0)))
    }

    @Test
    fun headlineOutOfTenClampsAnOutOfRangeConversionRatio() {
        // Defensive: overallConversion should never exceed 1.0, but a clamp keeps a stray server
        // value (e.g. a mid-window step count anomaly) from rendering as an impossible "11 out of 10".
        assertEquals(10, funnelHeadlineOutOfTen(results(entered = 10, converted = 11, overallConversion = 1.1)))
    }
}
