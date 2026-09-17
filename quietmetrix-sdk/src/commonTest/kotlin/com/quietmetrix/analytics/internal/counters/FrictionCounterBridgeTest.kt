package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.ScreenTracker
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FrictionCounterBridgeTest {

    @AfterTest
    fun tearDown() = runTest {
        FrictionCounterBridge.reset()
        ScreenTracker.reset()
        MetricGateway.reset()
    }

    @Test
    fun `a rage-tap burst records a friction counter tagged with the current screen`() = runTest {
        ScreenTracker.enter("Checkout", now = Instant.fromEpochMilliseconds(0))

        FrictionCounterBridge.onTap(0f, 0f, 0L)
        FrictionCounterBridge.onTap(0f, 0f, 100L)
        FrictionCounterBridge.onTap(0f, 0f, 200L)

        val pending = MetricGateway.drain().filter { it.metric == "friction" }
        assertEquals(1, pending.size)
        assertEquals(mapOf("screen" to "Checkout", "kind" to "rage_tap"), pending[0].dims)
    }

    @Test
    fun `no screen active tags the cell as unknown`() = runTest {
        FrictionCounterBridge.onTap(0f, 0f, 0L)
        FrictionCounterBridge.onTap(0f, 0f, 100L)
        FrictionCounterBridge.onTap(0f, 0f, 200L)

        val cell = MetricGateway.drain().single { it.metric == "friction" }
        assertEquals("unknown", cell.dims["screen"])
    }

    @Test
    fun `fewer than the threshold taps record nothing`() = runTest {
        FrictionCounterBridge.onTap(0f, 0f, 0L)
        FrictionCounterBridge.onTap(0f, 0f, 100L)

        assertTrue(MetricGateway.drain().none { it.metric == "friction" })
    }
}
