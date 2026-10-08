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

        val pending = MetricGateway.drain().filter { it.metric == "session" }
        assertEquals(1, pending.size)
        assertEquals("2026-09-04", pending[0].day)
        assertEquals(1L, pending[0].n)
        assertTrue(pending[0].isNewDevice)
    }

    @Test
    fun `drain clears state and matching MetricRecorder`() = runTest {
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
    @OptIn(ExperimentalTime::class)
    @Test
    fun `fresh screen envelopes are bounded and retry receipt IDs stay stable`() = runTest {
        MetricGateway.reset()
        val now = Instant.parse("2026-10-07T12:00:00Z")
        MetricGateway.record("screen_view_v2", mapOf("screen" to "home"), now = now)
        val batches = MetricGateway.prepareBatches("android", "1", "FR", "0.6.0", now.toEpochMilliseconds(), "phone")
        val envelopes = batches.flatMap { batch -> CounterFlusher.buildV2Envelopes(batch, batch.counters.filter { CounterFlusher.isV2Metric(it.metric) }) }
        assertTrue(envelopes.size > 2)
        assertTrue(envelopes.all { it.items.size in 1..512 })
        assertEquals(envelopes.size, envelopes.map { it.batchId }.toSet().size)
        val retried = batches.flatMap { batch -> CounterFlusher.buildV2Envelopes(batch, batch.counters.filter { CounterFlusher.isV2Metric(it.metric) }) }
        assertEquals(envelopes, retried)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `repeated screen views count one hourly installation and aliases have witnesses`() = runTest {
        MetricGateway.reset()
        val now = Instant.parse("2026-10-07T12:00:00Z")
        repeat(2) { MetricGateway.record("screen_view_v2", mapOf("screen" to "home"), now = now) }
        MetricGateway.record("event", mapOf("name" to "open"), now = now)
        val cells = MetricGateway.drain()
        assertTrue(cells.filter { it.metric == "unique_hour_v2" }.all { it.n == 1L })
        assertTrue(cells.any { it.metric == "witness_window_v2" && it.dims["target_metric"] == "event_v2" })
        assertEquals(2, cells.count { it.metric == "event_v2" })
    }

}
