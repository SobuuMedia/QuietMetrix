package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [DebugState] is a pure, Compose-free state holder — the `quietmetrix-sdk-debug` module's
 * overlay reads it via [pending]/[lastFlush], but nothing here depends on that module existing.
 * [recordFlush] is only ever called by [CounterFlusher] when `config.debug == true`; calling it
 * unconditionally would defeat the "zero overhead when debug is off" guarantee, so that gating
 * lives at the call site, not here.
 */
@OptIn(ExperimentalTime::class)
class DebugStateTest {

    @AfterTest
    fun tearDown() {
        DebugState.reset()
    }

    private fun counter(metric: String, n: Long = 1L) =
        MetricRecorder.PendingCounter(metric, mapOf("name" to "x"), "2026-09-04", n, true)

    @Test
    fun `starts with no pending counters and no flush outcome`() {
        assertTrue(DebugState.pending.value.isEmpty())
        assertNull(DebugState.lastFlush.value)
    }

    @Test
    fun `recordFlush publishes the flushed counters and outcome`() {
        val now = Instant.fromEpochMilliseconds(1_000L)
        DebugState.recordFlush(listOf(counter("event", 3L)), succeeded = true, now = now)

        val pending = DebugState.pending.value
        assertEquals(1, pending.size)
        assertEquals("event", pending[0].metric)
        assertEquals(3L, pending[0].n)

        val outcome = DebugState.lastFlush.value
        assertEquals(1, outcome?.counterCount)
        assertEquals(true, outcome?.succeeded)
        assertEquals(1_000L, outcome?.atEpochMs)
    }

    @Test
    fun `a later recordFlush replaces the previous snapshot`() = runTest {
        DebugState.recordFlush(listOf(counter("event")), succeeded = true, now = Instant.fromEpochMilliseconds(0L))
        DebugState.recordFlush(listOf(counter("value"), counter("session")), succeeded = false, now = Instant.fromEpochMilliseconds(2_000L))

        assertEquals(2, DebugState.pending.value.size)
        assertEquals(false, DebugState.lastFlush.value?.succeeded)
        assertEquals(2_000L, DebugState.lastFlush.value?.atEpochMs)
    }

    @Test
    fun `reset clears both pending and last-flush state`() {
        DebugState.recordFlush(listOf(counter("event")), succeeded = true, now = Instant.fromEpochMilliseconds(0L))
        DebugState.reset()

        assertTrue(DebugState.pending.value.isEmpty())
        assertNull(DebugState.lastFlush.value)
    }
}
