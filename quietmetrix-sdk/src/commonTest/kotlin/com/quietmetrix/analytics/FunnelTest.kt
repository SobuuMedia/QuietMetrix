package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.transport.EventQueue
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [Funnel] declares steps that reference events an app already tracks. [Funnel.step] is a
 * typed convenience for emitting a step's event directly — it must behave exactly like a
 * normal [trackEvent] call so the funnel matches it the same way retroactive analysis would.
 */
class FunnelTest {

    @BeforeTest
    fun setUp() = runTest {
        ConfigHolder.reset()
        InMemoryStore.clear()
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_", trackingAllowedByDefault = true))
        ScreenTracker.reset()
        EventQueue.clear()
    }

    @AfterTest
    fun tearDown() = runTest {
        ScreenTracker.reset()
        QuietMetrix.stop()
        EventQueue.clear()
        ConfigHolder.reset()
        InMemoryStore.clear()
    }

    private val funnel = Funnel(
        key = "signup",
        name = "Signup",
        steps = listOf(
            FunnelStep(key = "view", event = "screen_view", screen = "signup"),
            FunnelStep(key = "submit", event = "signup_submitted", props = mapOf("variant" to "b")),
        ),
    )

    @Test
    fun `step emits an event using the step's declared event name`() = runTest {
        funnel.step("submit")

        val events = EventQueue.drain(10)
        assertEquals(1, events.size)
        assertEquals("signup_submitted", events[0].event)
    }

    @Test
    fun `step carries the declared screen`() = runTest {
        funnel.step("view")

        val events = EventQueue.drain(10)
        assertEquals("signup", events[0].screen)
    }

    @Test
    fun `step merges declared props with call-site props`() = runTest {
        funnel.step("submit", mapOf("amount" to 42))

        val events = EventQueue.drain(10)
        assertEquals("b", events[0].props["variant"])
        assertEquals(42, events[0].props["amount"])
    }

    @Test
    fun `call-site props override declared props of the same key`() = runTest {
        funnel.step("submit", mapOf("variant" to "override"))

        val events = EventQueue.drain(10)
        assertEquals("override", events[0].props["variant"])
    }

    @Test
    fun `step is a no-op for an undeclared step key`() = runTest {
        funnel.step("does-not-exist")

        assertTrue(EventQueue.drain(10).isEmpty())
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
