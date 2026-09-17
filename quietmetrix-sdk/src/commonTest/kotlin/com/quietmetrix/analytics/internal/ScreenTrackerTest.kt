package com.quietmetrix.analytics.internal

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.counters.MetricRecorder
import com.quietmetrix.analytics.internal.counters.SessionTracker
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        MetricGateway.reset()
    }

    @AfterTest
    fun tearDown() = runTest {
        // Reset the trackers BEFORE stop(): stop() triggers the fire-and-forget
        // ScreenTracker.closeOutAsync() and SessionTracker.stop(), either of which would
        // otherwise record into the shared MetricGateway on a background dispatcher after
        // cleanup here, polluting whichever test runs next.
        ScreenTracker.reset()
        SessionTracker.reset()
        QuietMetrix.stop()
        ConfigHolder.reset()
        InMemoryStore.clear()
        MetricGateway.reset()
    }

    private fun List<MetricRecorder.PendingCounter>.one(metric: String) = single { it.metric == metric }
    private fun List<MetricRecorder.PendingCounter>.find(metric: String) = firstOrNull { it.metric == metric }

    @Test
    fun `entering a new screen records the previous screen's dwell bucket and the transition`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        ScreenTracker.enter("Cart", now = t0.plus(5.seconds))

        val pending = MetricGateway.drain()
        val dwell = pending.one("screen_dwell")
        assertEquals(mapOf("screen" to "Home", "bucket" to "5_10s"), dwell.dims)

        val transition = pending.one("screen_transition")
        assertEquals(mapOf("from" to "Home", "to" to "Cart"), transition.dims)
    }

    @Test
    fun `flush records the current screen's dwell then clears it`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        val flushedScreen = ScreenTracker.flush(now = t0.plus(3.seconds))
        assertEquals("Home", flushedScreen)

        val dwell = MetricGateway.drain().one("screen_dwell")
        assertEquals(mapOf("screen" to "Home", "bucket" to "0_5s"), dwell.dims)

        // A second flush with no active screen records nothing and returns null.
        assertNull(ScreenTracker.flush(now = t0.plus(9.seconds)))
        assertTrue(MetricGateway.drain().none { it.metric == "screen_dwell" || it.metric == "screen_transition" })
    }

    @Test
    fun `first enter with no prior screen records nothing`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        assertTrue(MetricGateway.drain().none { it.metric == "screen_dwell" || it.metric == "screen_transition" })
    }

    @Test
    fun `props supplied on enter are dropped, not attached to any counter`() = runTest {
        ScreenTracker.enter("Home", props = mapOf("tab" to "feed"), now = t0)
        ScreenTracker.enter("Cart", now = t0.plus(2.seconds))

        val pending = MetricGateway.drain()
        // Dwell and transition still fire; the dropped prop appears in neither cell's dims.
        assertEquals(mapOf("screen" to "Home", "bucket" to "0_5s"), pending.one("screen_dwell").dims)
        assertEquals(mapOf("from" to "Home", "to" to "Cart"), pending.one("screen_transition").dims)
        assertTrue(pending.none { "tab" in it.dims })
    }

    @Test
    fun `zero duration records the transition but not a dwell bucket`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        ScreenTracker.enter("Cart", now = t0)

        val pending = MetricGateway.drain()
        assertNull(pending.find("screen_dwell"))
        assertEquals(mapOf("from" to "Home", "to" to "Cart"), pending.one("screen_transition").dims)
    }

    @Test
    fun `re-entering the same screen name is not recorded as a transition`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        ScreenTracker.enter("Home", now = t0.plus(5.seconds))

        assertNull(MetricGateway.drain().find("screen_transition"))
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
