package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    @Test
    fun `counterV2Endpoint derives the additive sibling route`() {
        assertEquals("https://example.com/api/v2/counters", CounterFlusher.counterV2Endpoint("https://example.com/api/v1/track"))
        assertNull(CounterFlusher.counterV2Endpoint(null))
    }

    @Test
    fun `v2 transport routes only registered v2 metric names and preserves v1 compatible counters`() {
        assertEquals(true, CounterFlusher.isV2Metric("screen_view_v2"))
        assertEquals(false, CounterFlusher.isV2Metric("arbitrary_v2"))
        assertEquals(true, CounterFlusher.isV2OnlyMetric("screen_view_v2"))
        assertEquals(false, CounterFlusher.isV2OnlyMetric("unique_window_v2"))
        assertEquals(false, CounterFlusher.isV2OnlyMetric("experiment_goal_v2"))
    }

    @Test
    fun `v2 request preserves the original queued UTC hour and anonymous contributor bit`() {
        val counters = listOf(
            MetricRecorder.PendingCounter("event_v2", mapOf("name" to "open_book"), "2026-10-04", 3, true, hour = 14),
            MetricRecorder.PendingCounter("event_v2", mapOf("name" to "search"), "2026-10-04", 2, false, hour = 14),
        )
        val batch = MetricRecorder.QueuedBatch("0123456789abcdef", "2026-10-04", counters, "android", "1.0", null, "sdk", 1L, hour = 14)
        val request = CounterFlusher.buildV2Batch(batch, counters)
        assertEquals(14, request.hour)
        assertEquals("2026-10-04", request.day)
        assertEquals(listOf(1, 0), request.counters.map { it.contributors })
    }

    @Test
    fun `schema two emits day hour metric segment dimensions and count per item`() {
        val counters = listOf(
            MetricRecorder.PendingCounter("event_v2", mapOf("name" to "open_book"), "2026-10-04", 3, true, hour = 14),
            MetricRecorder.PendingCounter("event_v2", mapOf("name" to "search"), "2026-10-05", 2, false, hour = null),
        )
        val batch = MetricRecorder.QueuedBatch("0123456789abcdef", "2026-10-04", counters, "android", "1.0", null, "sdk", 1L)
        val envelope = CounterFlusher.buildV2Envelope(batch, counters)
        val wire = Json { encodeDefaults = true }.encodeToString(CounterV2EnvelopeRequestDto.serializer(), envelope)
        assertTrue("\"schema\":2" in wire)
        assertTrue("\"day\":\"2026-10-04\"" in wire && "\"hour\":14" in wire)
        assertTrue("\"day\":\"2026-10-05\"" in wire && "\"hour\":null" in wire)
        assertTrue("\"metric\":\"event_v2\"" in wire)
        assertTrue("\"segment\":{\"kind\":\"global\",\"value\":\"all\"}" in wire)
        assertTrue("\"kind\":\"platform\",\"value\":\"android\"" in wire)
        assertTrue("\"kind\":\"country\",\"value\":\"unknown\"" in wire)
        assertTrue("\"kind\":\"device_class\",\"value\":\"unknown\"" in wire)
        assertEquals(8, envelope.items.size)
        assertTrue("\"dimensions\":{\"name\":\"search\"}" in wire)
        assertFalse("contributors" in wire)
    }

    @Test
    fun `dashboard counter wire payload contains no installation seed personal data or attempt correlation`() {
        val counter = MetricRecorder.PendingCounter(
            "funnel_step_v2",
            mapOf("namespace" to "dashboard", "f" to "signup", "revision" to "3", "step" to "1", "entry_day" to "2026-10-04"),
            "2026-10-04", 1, true, hour = -1,
        )
        val batch = MetricRecorder.QueuedBatch("0123456789abcdef", "2026-10-04", listOf(counter), "ios", "1.0", null, "sdk", 1L)
        val wire = Json.encodeToString(CounterV2EnvelopeRequestDto.serializer(), CounterFlusher.buildV2Envelope(batch, listOf(counter)))
        listOf("install-seed-secret", "user@example.test", "51.5074", "-0.1278", "iPhone 17 Pro", "checkout_attempt_491").forEach {
            assertFalse(it in wire, "sensitive value leaked into aggregate request: $it")
        }
        assertFalse("anonymous_id" in wire)
        assertFalse("correlation" in wire)
        assertEquals(setOf("global", "platform"), CounterFlusher.buildV2Envelope(batch, listOf(counter)).items.map { it.segment.kind }.toSet())
    }

    @Test
    fun `project unique metrics receive coarse facets while screen unique metrics stay projective`() {
        val project = MetricRecorder.PendingCounter("unique_window_v2", mapOf("scope_kind" to "project", "scope_key" to "all", "days" to "1", "end_day" to "2026-10-04"), "2026-10-04", 1, true)
        val screen = MetricRecorder.PendingCounter("unique_window_v2", mapOf("scope_kind" to "screen", "scope_key" to "Home", "days" to "1", "end_day" to "2026-10-04"), "2026-10-04", 1, true)
        val batch = MetricRecorder.QueuedBatch("0123456789abcdef", "2026-10-04", listOf(project, screen), "ios", "1.0", "FR", "sdk", 1L, deviceClass = "tablet")
        val items = CounterFlusher.buildV2Envelope(batch, listOf(project, screen)).items
        assertEquals(setOf("global", "platform", "country", "device_class"), items.filter { it.dimensions["scope_kind"] == "project" }.map { it.segment.kind }.toSet())
        assertEquals(setOf("global", "platform"), items.filter { it.dimensions["scope_kind"] == "screen" }.map { it.segment.kind }.toSet())
    }

    @Test
    fun `country is normalized only from explicit two-letter app input`() {
        assertEquals("FR", CounterFlusher.normalizedCountry(" fr "))
        assertNull(CounterFlusher.normalizedCountry(null))
        assertNull(CounterFlusher.normalizedCountry("French"))
    }

    @Test
    fun `legacy server fallback excludes only the unsupported unique-window metric`() {
        val ordinary = pending("2026-09-04", metric = "session")
        val unique = MetricRecorder.PendingCounter(
            "unique_window_v2",
            mapOf("scope_kind" to "project", "scope_key" to "all", "days" to "7", "end_day" to "2026-09-04"),
            "2026-09-04", 1, true,
        )
        assertEquals(listOf(ordinary), CounterFlusher.countersForCapabilities(listOf(ordinary, unique), false))
        assertEquals(listOf(ordinary, unique), CounterFlusher.countersForCapabilities(listOf(ordinary, unique), true))
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
    fun `buildBatches carries sdk platform and version and and item fields through`() {
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
