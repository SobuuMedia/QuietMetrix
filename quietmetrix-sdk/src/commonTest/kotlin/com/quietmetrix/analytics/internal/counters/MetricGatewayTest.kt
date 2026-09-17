package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The single shared [MetricRecorder] instance every counter producer (trackEvent,
 * ScreenTracker, funnels) records into, and [CounterFlusher] drains from. Thin by design —
 * the interesting logic lives in [MetricRecorder] and [utcTodayIso]; this just wires them
 * together as one process-wide instance.
 */
class MetricGatewayTest {

    @AfterTest
    fun tearDown() {
        MetricGateway.reset()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `record computes day from the given instant and forwards to the shared recorder`() = runTest {
        val instant = Instant.fromEpochMilliseconds(20700L * 86_400_000L) // 2026-09-04 (see EpochDayTest)
        MetricGateway.record("session", mapOf("bucket" to "1_3"), now = instant)

        val pending = MetricGateway.drain()
        assertEquals(1, pending.size)
        assertEquals("2026-09-04", pending[0].day)
        assertEquals(1L, pending[0].n)
        assertTrue(pending[0].isNewDevice)
    }

    @Test
    fun `drain clears state, matching MetricRecorder`() = runTest {
        MetricGateway.record("session", mapOf("bucket" to "1_3"))
        MetricGateway.drain()
        assertTrue(MetricGateway.drain().isEmpty())
    }

    @Test
    fun `reset starts a fresh recorder for test isolation`() = runTest {
        MetricGateway.record("session", mapOf("bucket" to "1_3"))
        MetricGateway.reset()
        assertTrue(MetricGateway.drain().isEmpty())
    }
}
