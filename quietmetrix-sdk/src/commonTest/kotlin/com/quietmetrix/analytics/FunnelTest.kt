package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.counters.MetricRecorder
import com.quietmetrix.analytics.internal.counters.SessionTracker
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [Funnel] declares steps that reference events an app already tracks. [Funnel.step] is a
 * typed convenience for emitting a step's event directly, via the same `trackEvent` pipeline
 * every other event goes through — so under aggregate-only ingest it records an `event{name}`
 * counter, the same as a direct `trackEvent(declared.event, ...)` call would (see
 * [com.quietmetrix.analytics.internal.counters.recordEventCounter]: `screen` and `props` are
 * accepted but never sent). On-device funnel step evaluation (matching this counter against a
 * funnel's declared steps) is a later migration step; `step()` itself only needs to prove it
 * fires the right named event.
 */
class FunnelTest {

    @BeforeTest
    fun setUp() = runTest {
        ConfigHolder.reset()
        InMemoryStore.clear()
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_", trackingAllowedByDefault = true))
        ScreenTracker.reset()
        MetricGateway.reset()
    }

    @AfterTest
    fun tearDown() = runTest {
        // Reset BEFORE stop(), same reasoning as ScreenTrackerTest: stop() fire-and-forgets
        // ScreenTracker.closeOutAsync() and SessionTracker.stop(), either of which would
        // otherwise record into the shared MetricGateway after cleanup here.
        ScreenTracker.reset()
        SessionTracker.reset()
        QuietMetrix.stop()
        ConfigHolder.reset()
        InMemoryStore.clear()
        MetricGateway.reset()
    }

    private val funnel = Funnel(
        key = "signup",
        name = "Signup",
        steps = listOf(
            FunnelStep(key = "view", event = "screen_view", screen = "signup"),
            FunnelStep(key = "submit", event = "signup_submitted", props = mapOf("variant" to "b")),
        ),
    )

    // MetricGateway is one process-wide recorder; QuietMetrix.init's fire-and-forget retention
    // check (see RetentionReporter) can land its own cell here on a background dispatcher
    // during a test, same as ScreenTrackerTest's `.one`/`.find` helpers exist to tolerate — so
    // these assertions filter to the `event` metric rather than assuming total isolation.
    private fun List<MetricRecorder.PendingCounter>.event() = single { it.metric == "event" }

    @Test
    fun `step records an event counter keyed by the step's declared event name`() = runTest {
        funnel.step("submit")

        val event = MetricGateway.drain().event()
        assertEquals("event", event.metric)
        assertEquals(mapOf("name" to "signup_submitted"), event.dims)
    }

    @Test
    fun `declared screen and props are accepted but never sent`() = runTest {
        funnel.step("submit", mapOf("amount" to 42))

        // Only {name: <event>} ever reaches a counter — see recordEventCounter.
        assertEquals(mapOf("name" to "signup_submitted"), MetricGateway.drain().event().dims)
    }

    @Test
    fun `step is a no-op for an undeclared step key`() = runTest {
        funnel.step("does-not-exist")

        assertTrue(MetricGateway.drain().none { it.metric == "event" })
    }

    @Test
    fun `steps are exposed in declaration order`() {
        assertEquals(listOf("view", "submit"), funnel.steps.map { it.key })
    }

    @Test
    fun `default window is seven days`() {
        assertEquals(7L * 24 * 3600, funnel.windowSeconds)
    }

    @Test
    fun `a custom window is honored`() {
        val custom = Funnel(key = "k", name = "n", steps = funnel.steps, windowSeconds = 3600L)
        assertEquals(3600L, custom.windowSeconds)
    }
}
