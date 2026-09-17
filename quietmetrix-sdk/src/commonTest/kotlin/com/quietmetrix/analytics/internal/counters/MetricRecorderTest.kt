package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricRecorderTest {

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
    fun `a cell touched again after a drain, same day, is no longer new-device`() = runTest {
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
}
