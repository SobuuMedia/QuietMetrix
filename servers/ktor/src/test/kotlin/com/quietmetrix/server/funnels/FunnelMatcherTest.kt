package com.quietmetrix.server.funnels

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunnelMatcherTest {

    private fun t(seconds: Long) = Instant.fromEpochSeconds(seconds)

    private fun event(name: String, seconds: Long, screen: String? = null, props: Map<String, String> = emptyMap()) =
        FunnelEvent(eventName = name, ts = t(seconds), screen = screen, props = props)

    private val twoSteps = listOf(
        FunnelStepDefinition(key = "view", event = "screen_view", screen = "signup"),
        FunnelStepDefinition(key = "submit", event = "signup_submitted"),
    )

    @Test
    fun `happy path matches all steps in order`() {
        val events = listOf(
            event("screen_view", 0, screen = "signup"),
            event("signup_submitted", 10),
        )
        val result = FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events)!!

        assertTrue(result.completed)
        assertEquals(listOf("view", "submit"), result.reached.map { it.stepKey })
        assertEquals(t(0), result.reached[0].ts)
        assertEquals(t(10), result.reached[1].ts)
    }

    @Test
    fun `input order does not matter — matching is by timestamp`() {
        val events = listOf(
            event("signup_submitted", 10),
            event("screen_view", 0, screen = "signup"),
        )
        val result = FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events)!!

        assertTrue(result.completed)
        assertEquals(t(0), result.reached[0].ts)
        assertEquals(t(10), result.reached[1].ts)
    }

    @Test
    fun `window expiry by exactly one second drops the actor at the previous step`() {
        val events = listOf(
            event("screen_view", 0, screen = "signup"),
            event("signup_submitted", 3601),
        )
        val result = FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events)!!

        assertEquals(false, result.completed)
        assertEquals(listOf("view"), result.reached.map { it.stepKey })
    }

    @Test
    fun `a match exactly at the window deadline is inclusive`() {
        val events = listOf(
            event("screen_view", 0, screen = "signup"),
            event("signup_submitted", 3600),
        )
        val result = FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events)!!

        assertTrue(result.completed)
    }

    @Test
    fun `steps at the same timestamp do not create a causal conversion`() {
        val events = listOf(
            event("screen_view", 0, screen = "signup"),
            event("signup_submitted", 0),
        )

        val result = FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events)!!

        assertEquals(false, result.completed)
        assertEquals(listOf("view"), result.reached.map { it.stepKey })
    }

    @Test
    fun `repeated step events do not confuse the cursor`() {
        val events = listOf(
            event("screen_view", 0, screen = "signup"),
            event("screen_view", 1, screen = "signup"),
            event("screen_view", 2, screen = "signup"),
            event("signup_submitted", 10),
        )
        val result = FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events)!!

        assertTrue(result.completed)
        assertEquals(t(0), result.reached[0].ts, "entry is the FIRST matching event, not a later duplicate")
        assertEquals(t(10), result.reached[1].ts)
    }

    @Test
    fun `an actor who restarts the funnel is still bound to their first entry`() {
        // First attempt: entry at t=0, window is 100s, but step 2 doesn't happen until t=500 —
        // well outside the window from the FIRST entry. A second attempt at t=1000 completes
        // within ITS OWN window, but the actor's entry is fixed at the first occurrence of
        // step 1, so the second attempt must not resurrect them into a completion.
        val events = listOf(
            event("screen_view", 0, screen = "signup"),
            event("signup_submitted", 500),
            event("screen_view", 1000, screen = "signup"),
            event("signup_submitted", 1010),
        )
        val result = FunnelMatcher.match(twoSteps, windowSeconds = 100, events = events)!!

        assertEquals(false, result.completed)
        assertEquals(listOf("view"), result.reached.map { it.stepKey })
        assertEquals(t(0), result.reached[0].ts)
    }

    @Test
    fun `a missing middle step blocks matching a later step`() {
        val threeSteps = twoSteps + FunnelStepDefinition(key = "paid", event = "purchase_completed")
        val events = listOf(
            event("screen_view", 0, screen = "signup"),
            // signup_submitted never happens
            event("purchase_completed", 20),
        )
        val result = FunnelMatcher.match(threeSteps, windowSeconds = 3600, events = events)!!

        assertEquals(listOf("view"), result.reached.map { it.stepKey })
        assertEquals(false, result.completed)
    }

    @Test
    fun `a single-step funnel completes on entry alone`() {
        val oneStep = listOf(FunnelStepDefinition(key = "view", event = "screen_view"))
        val events = listOf(event("screen_view", 0))
        val result = FunnelMatcher.match(oneStep, windowSeconds = 60, events = events)!!

        assertTrue(result.completed)
        assertEquals(listOf("view"), result.reached.map { it.stepKey })
    }

    @Test
    fun `no matching entry event returns null — the actor is not in the funnel at all`() {
        val events = listOf(event("some_other_event", 0))
        assertNull(FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events))
    }

    @Test
    fun `a screen filter excludes an event on the wrong screen`() {
        val events = listOf(event("screen_view", 0, screen = "settings"))
        assertNull(FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events))
    }

    @Test
    fun `a screen filter accepts an event on the right screen after skipping a wrong one`() {
        val events = listOf(
            event("screen_view", 0, screen = "settings"),
            event("screen_view", 1, screen = "signup"),
            event("signup_submitted", 2),
        )
        val result = FunnelMatcher.match(twoSteps, windowSeconds = 3600, events = events)!!

        assertTrue(result.completed)
        assertEquals(t(1), result.reached[0].ts)
    }

    @Test
    fun `a prop filter requires an exact match`() {
        val stepsWithProps = listOf(
            FunnelStepDefinition(key = "view", event = "screen_view", props = mapOf("variant" to "b")),
            FunnelStepDefinition(key = "submit", event = "signup_submitted"),
        )
        val events = listOf(
            event("screen_view", 0, props = mapOf("variant" to "a")),
            event("screen_view", 1, props = mapOf("variant" to "b")),
            event("signup_submitted", 2),
        )
        val result = FunnelMatcher.match(stepsWithProps, windowSeconds = 3600, events = events)!!

        assertEquals(t(1), result.reached[0].ts, "the wrong-variant event at t=0 must not match")
    }

    @Test
    fun `an empty step list matches nothing`() {
        assertNull(FunnelMatcher.match(emptyList(), windowSeconds = 3600, events = listOf(event("x", 0))))
    }
}
