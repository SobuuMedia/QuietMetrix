package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.FunnelDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunnelsLogicTest {

    private fun funnel(key: String) = FunnelDto(funnelKey = key, name = key)

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
}
