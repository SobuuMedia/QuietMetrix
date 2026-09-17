package com.quietmetrix.analytics.internal.funnels

import com.quietmetrix.analytics.FunnelStep
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [funnelStepMatches] mirrors servers/ktor's FunnelMatcher.matchesStep exactly (event name,
 * optional screen, exact-match string props) — the on-device evaluator must agree with the
 * historical server-side semantics devs already rely on.
 */
class FunnelStepMatchingTest {

    @Test
    fun `matches on event name alone when no screen or props are declared`() {
        val step = FunnelStep(key = "view", event = "screen_view")
        assertTrue(funnelStepMatches(step, "screen_view", "anything", mapOf("x" to 1)))
    }

    @Test
    fun `a different event name never matches`() {
        val step = FunnelStep(key = "view", event = "screen_view")
        assertFalse(funnelStepMatches(step, "button_click", null, emptyMap()))
    }

    @Test
    fun `a declared screen must match exactly`() {
        val step = FunnelStep(key = "view", event = "screen_view", screen = "signup")
        assertTrue(funnelStepMatches(step, "screen_view", "signup", emptyMap()))
        assertFalse(funnelStepMatches(step, "screen_view", "login", emptyMap()))
        assertFalse(funnelStepMatches(step, "screen_view", null, emptyMap()))
    }

    @Test
    fun `no declared screen accepts any screen, including null`() {
        val step = FunnelStep(key = "view", event = "screen_view")
        assertTrue(funnelStepMatches(step, "screen_view", null, emptyMap()))
        assertTrue(funnelStepMatches(step, "screen_view", "whatever", emptyMap()))
    }

    @Test
    fun `declared props are exact-match string filters against stringified values`() {
        val step = FunnelStep(key = "submit", event = "signup_submitted", props = mapOf("variant" to "b"))
        assertTrue(funnelStepMatches(step, "signup_submitted", null, mapOf("variant" to "b")))
        assertFalse(funnelStepMatches(step, "signup_submitted", null, mapOf("variant" to "a")))
        assertFalse(funnelStepMatches(step, "signup_submitted", null, emptyMap()))
    }

    @Test
    fun `a numeric prop value matches its string form`() {
        val step = FunnelStep(key = "submit", event = "purchase", props = mapOf("amount" to "42"))
        assertTrue(funnelStepMatches(step, "purchase", null, mapOf("amount" to 42)))
    }

    @Test
    fun `extra unmentioned props on the event do not prevent a match`() {
        val step = FunnelStep(key = "submit", event = "signup_submitted", props = mapOf("variant" to "b"))
        assertTrue(funnelStepMatches(step, "signup_submitted", null, mapOf("variant" to "b", "extra" to "x")))
    }

    @Test
    fun `all declared props must match, not just one of several`() {
        val step = FunnelStep(key = "submit", event = "purchase", props = mapOf("plan" to "pro", "cycle" to "annual"))
        assertTrue(funnelStepMatches(step, "purchase", null, mapOf("plan" to "pro", "cycle" to "annual")))
        assertFalse(funnelStepMatches(step, "purchase", null, mapOf("plan" to "pro", "cycle" to "monthly")))
    }
}
