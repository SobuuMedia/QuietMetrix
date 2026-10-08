package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.PersistentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricRecorderTest {
    @Test
    fun `shared sha256 implementation matches standard vector`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }

    @Test
    fun `v2 activity keeps independent daily and hourly projections`() = runTest {
        val recorder = MetricRecorder()
        val dims = mapOf("name" to "open")
        recorder.recordWithWindows(
            "event_v2", dims, "2026-10-04", 2,
            windows = emptyList(), hour = 9,
        )
        val cells = recorder.drain().filter { it.metric == "event_v2" }
        assertEquals(setOf(null, 9), cells.map { it.hour }.toSet())
        assertEquals(setOf(2L), cells.map { it.n }.toSet())
        assertTrue(cells.all { it.isNewDevice })
    }


    private class MemoryStore : PersistentStore {
        val values = mutableMapOf<String, String>()
        var failWrites = false
        override fun get(key: String): String? = values[key]
        override fun set(key: String, value: String) {
            if (failWrites) error("disk full")
            values[key] = value
        }
        override fun remove(key: String) { values.remove(key) }
    }

    @Test
    fun `a fresh cell drains as one delta marked new-device`() = runTest {
        val recorder = MetricRecorder()
        recorder.record("screen_transition", mapOf("from" to "Library", "to" to "BookDetail"), day = "2026-09-04")

        val pending = recorder.drain()
        assertEquals(1, pending.size)
        assertEquals("screen_transition", pending[0].metric)
        assertEquals(mapOf("from" to "Library", "to" to "BookDetail"), pending[0].dims)
        assertEquals("2026-09-04", pending[0].day)
        assertEquals(1L, pending[0].n)
        assertTrue(pending[0].isNewDevice)
    }

    @Test
    fun `repeated records of the same cell before a drain accumulate into one delta`() = runTest {
        val recorder = MetricRecorder()
        recorder.record("screen_transition", mapOf("from" to "A", "to" to "B"), n = 2, day = "2026-09-04")
        recorder.record("screen_transition", mapOf("from" to "A", "to" to "B"), n = 3, day = "2026-09-04")

        val pending = recorder.drain()
        assertEquals(1, pending.size)
        assertEquals(5L, pending[0].n)
        assertTrue(pending[0].isNewDevice)
    }

    @Test
    fun `a cell touched again after a drain and same day and is no longer new-device`() = runTest {
        val recorder = MetricRecorder()
        recorder.record("session", mapOf("bucket" to "1_3"), day = "2026-09-04")
        recorder.drain()

        recorder.record("session", mapOf("bucket" to "1_3"), day = "2026-09-04")
        val pending = recorder.drain()

        assertEquals(1, pending.size)
        assertEquals(false, pending[0].isNewDevice)
    }

    @Test
    fun `the same metric and dims on a new day is new-device again`() = runTest {
        val recorder = MetricRecorder()
        recorder.record("session", mapOf("bucket" to "1_3"), day = "2026-09-04")
        recorder.drain()

        recorder.record("session", mapOf("bucket" to "1_3"), day = "2026-09-05")
        val pending = recorder.drain()

        assertEquals(1, pending.size)
        assertEquals("2026-09-05", pending[0].day)
        assertTrue(pending[0].isNewDevice)
    }

    @Test
    fun `hourly cells stay distinct and retain per-hour contributor flags`() = runTest {
        val recorder = MetricRecorder()
        recorder.record("event_v2", mapOf("name" to "open_book"), day = "2026-10-04", hour = 8)
        recorder.record("event_v2", mapOf("name" to "open_book"), day = "2026-10-04", hour = 8)
        recorder.record("event_v2", mapOf("name" to "open_book"), day = "2026-10-04", hour = 9)
        val rows = recorder.drain().associateBy { it.hour }
        assertEquals(setOf(8, 9), rows.keys)
        assertEquals(2L, rows.getValue(8).n)
        assertEquals(1L, rows.getValue(9).n)
        assertTrue(rows.getValue(8).isNewDevice)
        assertTrue(rows.getValue(9).isNewDevice)
    }

    @Test
    fun `outbox keeps separate original hours across restart`() = runTest {
        val store = MemoryStore()
        val first = MetricRecorder(store)
        first.record("event_v2", mapOf("name" to "open_book"), day = "2026-10-04", hour = 8)
        first.record("event_v2", mapOf("name" to "open_book"), day = "2026-10-04", hour = 9)
        val originals = first.prepareBatches("android", "1.0", null, "sdk", 1_000).sortedBy { it.hour }
        assertEquals(listOf(8, 9), originals.map { it.hour })
        val retry = MetricRecorder(store).prepareBatches("ios", null, null, "changed", 2_000).sortedBy { it.hour }
        assertEquals(originals.map { it.id to it.hour }, retry.map { it.id to it.hour })
        assertEquals(originals.map { it.counters }, retry.map { it.counters })
    }

    @Test
    fun `different dims for the same metric accumulate independently`() = runTest {
        val recorder = MetricRecorder()
        recorder.record("screen_transition", mapOf("from" to "A", "to" to "B"), day = "2026-09-04")
        recorder.record("screen_transition", mapOf("from" to "B", "to" to "C"), day = "2026-09-04")
        recorder.record("screen_transition", mapOf("from" to "A", "to" to "B"), day = "2026-09-04")

        val pending = recorder.drain().associateBy { it.dims }
        assertEquals(2, pending.size)
        assertEquals(2L, pending.getValue(mapOf("from" to "A", "to" to "B")).n)
        assertEquals(1L, pending.getValue(mapOf("from" to "B", "to" to "C")).n)
    }

    @Test
    fun `dims key order does not create a distinct cell`() = runTest {
        val recorder = MetricRecorder()
        recorder.record("screen_transition", mapOf("from" to "A", "to" to "B"), day = "2026-09-04")
        recorder.record("screen_transition", mapOf("to" to "B", "from" to "A"), day = "2026-09-04")

        val pending = recorder.drain()
        assertEquals(1, pending.size)
        assertEquals(2L, pending[0].n)
    }

    @Test
    fun `swapping which value goes with which key is a distinct cell`() = runTest {
        // Guards against a naive no-separator join, where {from:"AB",to:"C"} and
        // {from:"A",to:"BC"} would canonicalize to the same key.
        val recorder = MetricRecorder()
        recorder.record("screen_transition", mapOf("from" to "AB", "to" to "C"), day = "2026-09-04")
        recorder.record("screen_transition", mapOf("from" to "A", "to" to "BC"), day = "2026-09-04")

        val pending = recorder.drain()
        assertEquals(2, pending.size)
    }

    @Test
    fun `draining an empty recorder returns an empty list`() = runTest {
        val recorder = MetricRecorder()
        assertTrue(recorder.drain().isEmpty())
    }

    @Test
    fun `drain clears pending state`() = runTest {
        val recorder = MetricRecorder()
        recorder.record("session", mapOf("bucket" to "1_3"), day = "2026-09-04")
        recorder.drain()
        assertTrue(recorder.drain().isEmpty())
    }

    @Test
    fun `pending counter survives recorder recreation without counting a device twice`() = runTest {
        val store = MemoryStore()
        val first = MetricRecorder(store)
        first.record("event", mapOf("name" to "open_book"), day = "2026-09-04")

        val restarted = MetricRecorder(store)
        val pending = restarted.drain().single()
        assertEquals(1L, pending.n)
        assertTrue(pending.isNewDevice)
        restarted.record("event", mapOf("name" to "open_book"), day = "2026-09-04")
        assertEquals(false, restarted.drain().single().isNewDevice)
    }

    @Test
    fun `retry outbox keeps the same batch across restart until acknowledged`() = runTest {
        val store = MemoryStore()
        val first = MetricRecorder(store)
        first.record("event", mapOf("name" to "open_book"), day = "2026-09-04", n = 4)
        val original = first.prepareBatches("android", "1.0", "FR", "sdk", 1_000, "tablet").single()

        val restarted = MetricRecorder(store)
        val retry = restarted.prepareBatches("ios", "2.0", "US", "new-sdk", 2_000).single()
        assertEquals(original.id, retry.id)
        assertEquals(original.counters, retry.counters)
        assertEquals("FR", retry.countryCode)
        assertEquals("tablet", retry.deviceClass)
        restarted.acknowledge(retry.id)
        assertTrue(MetricRecorder(store).prepareBatches("android", "1.0", null, "sdk", 3_000).isEmpty())
    }

    @Test
    fun `storage write failures are observable`() = runTest {
        val store = MemoryStore().apply { failWrites = true }
        val recorder = MetricRecorder(store)
        recorder.record("event", mapOf("name" to "open_book"), day = "2026-09-04")
        assertTrue(recorder.hasPersistenceFailure())
    }

    @Test
    fun `storage read failure disables the recorder without crashing initialization`() = runTest {
        val store = object : com.quietmetrix.analytics.internal.PersistentStore {
            override fun get(key: String): String? = error("storage unavailable")
            override fun set(key: String, value: String) = error("storage unavailable")
            override fun remove(key: String) = Unit
        }
        val recorder = MetricRecorder(store)
        assertTrue(recorder.hasPersistenceFailure())
        recorder.record("event", mapOf("name" to "open_book"), day = "2026-09-04")
        assertTrue(recorder.prepareBatches("web", null, null, "sdk", 1_000).isEmpty())
    }

    @Test
    fun `retry batches expire after seven days`() = runTest {
        val store = MemoryStore()
        val first = MetricRecorder(store)
        first.record("event", mapOf("name" to "open_book"), day = "2026-09-04")
        val queued = first.prepareBatches("android", null, null, "sdk", 1_000).single()

        val afterRetention = MetricRecorder(store).prepareBatches(
            "android", null, null, "sdk", 1_000 + 8L * 24 * 60 * 60 * 1000,
        )
        assertTrue(afterRetention.none { it.id == queued.id })
    }

    @Test
    fun `unique window contributions persist and emit only one new end date per active day`() = runTest {
        val store = MemoryStore()
        val first = MetricRecorder(store)
        val firstWindows = uniqueWindows("2026-10-04")
        first.recordWithWindows("event", mapOf("name" to "open"), "2026-10-04", 1, firstWindows)
        assertEquals(148, first.drain().count { it.metric == "unique_window_v2" })

        val restarted = MetricRecorder(store)
        val nextWindows = uniqueWindows("2026-10-05")
        restarted.recordWithWindows("event", mapOf("name" to "open"), "2026-10-05", 1, nextWindows)
        val nextDayContributions = restarted.drain().filter { it.metric == "unique_window_v2" }
        assertEquals(9, nextDayContributions.size)
        assertEquals(setOf("1", "2", "3", "4", "5", "6", "7", "30", "90"), nextDayContributions.map { it.dims.getValue("days") }.toSet())
    }

    @Test
    fun `ordinary metric writes one fixed-window witness per target group and window`() = runTest {
        val recorder = MetricRecorder()
        val windows = listOf(
            mapOf("scope_kind" to "project", "scope_key" to "all", "days" to "1", "end_day" to "2026-10-04") to "2026-10-04",
            mapOf("scope_kind" to "project", "scope_key" to "all", "days" to "1", "end_day" to "2026-10-05") to "2026-10-05",
        )
        val dims = mapOf("name" to "open")
        recorder.recordWithWindows("event_v2", dims, "2026-10-04", 2, windows)
        recorder.recordWithWindows("event_v2", dims, "2026-10-04", 3, windows)
        val witnesses = recorder.drain().filter { it.metric == "witness_window_v2" }
        assertEquals(2, witnesses.size)
        assertEquals(setOf(1L), witnesses.map { it.n }.toSet())
        assertEquals(sha256Hex("name\u001fopen"), witnesses.first().dims.getValue("target_hash"))
    }

    @Test
    fun `large day is split into protocol sized immutable batches`() = runTest {
        val recorder = MetricRecorder()
        repeat(513) { index ->
            recorder.record("event_v2", mapOf("name" to "event_$index"), day = "2026-10-04", hour = 9)
        }
        val batches = recorder.prepareBatches("android", null, null, "sdk", 1L)
        assertEquals(listOf(512, 1), batches.map { it.counters.size })
        assertEquals(2, batches.map { it.id }.toSet().size)
    }

    @Test
    fun `disabled restart discards outbox left by interrupted revocation`() = runTest {
        val store = MemoryStore()
        val original = MetricRecorder(store)
        original.record("event_v2", mapOf("name" to "before_refusal"), "2026-10-04")
        original.prepareBatches("android", null, null, "sdk", 1L)
        val restarted = MetricRecorder(store, restorePending = false)
        assertTrue(restarted.prepareBatches("android", null, null, "sdk", 2L).isEmpty())
        assertTrue(MetricRecorder(store).prepareBatches("android", null, null, "sdk", 3L).isEmpty())
    }

    private fun uniqueWindows(day: String): List<Pair<Map<String, String>, String>> {
        val origin = epochDayFromIso(day)
        return ((1..7) + listOf(30, 90)).flatMap { length ->
            (0 until length).map { offset ->
                val (year, month, date) = civilDateFromEpochDay(origin + offset.toLong())
                val endDay = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${date.toString().padStart(2, '0')}"
                mapOf("scope_kind" to "project", "scope_key" to "all", "days" to length.toString(), "end_day" to endDay) to endDay
            }
        }
    }
}
