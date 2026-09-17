package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [CounterFlusher]'s pure logic — endpoint derivation and DTO grouping — tested directly,
 * without touching the network. Mirrors how FunnelRegistrarTest exercises canonicalPayload
 * and FunnelRegistrar's registerEndpoint pattern.
 */
class CounterFlusherTest {

    @Test
    fun `counterEndpoint derives the sibling path from the tracking endpoint`() {
        assertEquals(
            "https://example.com/api/v1/counters",
            CounterFlusher.counterEndpoint("https://example.com/api/v1/track"),
        )
    }

    @Test
    fun `counterEndpoint handles a batch-style tracking endpoint the same way`() {
        assertEquals(
            "https://example.com/api/v1/counters",
            CounterFlusher.counterEndpoint("https://example.com/api/v1/track/batch"),
        )
    }

    @Test
    fun `counterEndpoint is null when there is no tracking endpoint`() {
        assertNull(CounterFlusher.counterEndpoint(null))
    }

    @Test
    fun `counterEndpoint is null when the endpoint has no api-v1 segment`() {
        assertNull(CounterFlusher.counterEndpoint("https://example.com/track"))
    }

    private fun pending(day: String, metric: String = "session", n: Long = 1L, isNewDevice: Boolean = true) =
        MetricRecorder.PendingCounter(metric, mapOf("bucket" to "1_3"), day, n, isNewDevice)

    @Test
    fun `buildBatches groups pending counters by day into one request per day`() {
        val batches = CounterFlusher.buildBatches(
            pending = listOf(pending("2026-09-04"), pending("2026-09-05"), pending("2026-09-04", metric = "event")),
            platform = "jvm",
            appVersion = "2.4.0",
            sdkVersion = "0.5.0",
        )
        assertEquals(2, batches.size)
        val byDay = batches.associateBy { it.day }
        assertEquals(2, byDay.getValue("2026-09-04").counters.size)
        assertEquals(1, byDay.getValue("2026-09-05").counters.size)
    }

    @Test
    fun `buildBatches carries sdk platform, version, and item fields through`() {
        val batches = CounterFlusher.buildBatches(
            pending = listOf(pending("2026-09-04", n = 3L, isNewDevice = true)),
            platform = "android",
            appVersion = "2.4.0",
            sdkVersion = "0.5.0",
        )
        val batch = batches.single()
        assertEquals("android", batch.sdk?.platform)
        assertEquals("0.5.0", batch.sdk?.version)
        assertEquals("2.4.0", batch.app?.version)
        val item = batch.counters.single()
        assertEquals("session", item.m)
        assertEquals(mapOf("bucket" to "1_3"), item.d)
        assertEquals(3L, item.n)
        assertEquals(1, item.u)
    }

    @Test
    fun `buildBatches sets u to 0 for a cell that was not newly seen`() {
        val batches = CounterFlusher.buildBatches(
            pending = listOf(pending("2026-09-04", isNewDevice = false)),
            platform = "jvm",
            appVersion = null,
            sdkVersion = "0.5.0",
        )
        assertEquals(0, batches.single().counters.single().u)
    }

    @Test
    fun `buildBatches on an empty pending list produces no batches`() {
        assertEquals(0, CounterFlusher.buildBatches(emptyList(), "jvm", null, "0.5.0").size)
    }
}
