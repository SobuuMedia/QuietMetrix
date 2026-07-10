package com.quietmetrix.analytics.internal

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.transport.EventQueue
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ScreenTrackerTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000_000)

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
        // Reset the tracker BEFORE stop(): stop() triggers the fire-and-forget
        // ScreenTracker.closeOutAsync(), which would otherwise flush a still-active screen on a
        // background dispatcher and land a stray screen_view in the shared EventQueue after cleanup,
        // polluting whichever test runs next.
        ScreenTracker.reset()
        QuietMetrix.stop()
        EventQueue.clear()
        ConfigHolder.reset()
        InMemoryStore.clear()
    }

    @Test
    fun `entering a new screen emits the previous screen view with its duration`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        ScreenTracker.enter("Cart", now = t0.plus(5.seconds))

        val events = EventQueue.drain(10)
        assertEquals(1, events.size)
        val e = events[0]
        assertEquals("screen_view", e.event)
        assertEquals("Home", e.screen)
        assertEquals(5000L, e.props["duration_ms"])
    }

    @Test
    fun `flush emits the current screen then clears it`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        ScreenTracker.flush(now = t0.plus(3.seconds))

        val first = EventQueue.drain(10)
        assertEquals(1, first.size)
        assertEquals("Home", first[0].screen)
        assertEquals(3000L, first[0].props["duration_ms"])

        // A second flush with no active screen emits nothing.
        ScreenTracker.flush(now = t0.plus(9.seconds))
        assertTrue(EventQueue.drain(10).isEmpty())
    }

    @Test
    fun `first enter with no prior screen emits nothing`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        assertTrue(EventQueue.drain(10).isEmpty())
    }

    @Test
    fun `props supplied on enter are attached to that screen's event`() = runTest {
        ScreenTracker.enter("Home", props = mapOf("tab" to "feed"), now = t0)
        ScreenTracker.enter("Cart", now = t0.plus(2.seconds))

        val e = EventQueue.drain(10).single()
        assertEquals("feed", e.props["tab"])
        assertEquals(2000L, e.props["duration_ms"])
    }

    @Test
    fun `zero or negative duration is not emitted`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        ScreenTracker.enter("Cart", now = t0)
        assertTrue(EventQueue.drain(10).isEmpty())
    }

    // --- enrichWithDwell: the client path that stamps time-on-screen onto events. -------------
    // This guards the intact capture path behind the dashboard's Duration column. The column is
    // only populated when an app calls trackScreen() (which sets an active screen here).

    @Test
    fun `enrichWithDwell stamps duration_ms while a screen is active`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        val enriched = ScreenTracker.enrichWithDwell(mapOf("btn" to "buy"), now = t0.plus(5.seconds))
        assertEquals(5000L, enriched["duration_ms"])
        assertEquals("buy", enriched["btn"])
    }

    @Test
    fun `enrichWithDwell leaves props untouched when no screen is active`() = runTest {
        // No enter() -> no active screen, as on an app that never calls trackScreen().
        val props = mapOf("btn" to "buy")
        val enriched = ScreenTracker.enrichWithDwell(props, now = t0.plus(5.seconds))
        assertEquals(props, enriched)
        assertTrue("duration_ms" !in enriched)
    }

    @Test
    fun `enrichWithDwell preserves an existing duration_ms`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        val enriched = ScreenTracker.enrichWithDwell(
            mapOf("duration_ms" to 999L),
            now = t0.plus(5.seconds),
        )
        assertEquals(999L, enriched["duration_ms"])
    }
}
