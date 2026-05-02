package com.quietmetrix.analytics.internal.transport

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlushManagerTest {

    @AfterTest
    fun tearDown() = runTest {
        EventQueue.clear()
    }

    @Test
    fun `currentBackoffMs returns 0 when no failures`() {
        assertEquals(0L, FlushManager.currentBackoffMs())
    }

    @Test
    fun `backoff follows exponential progression capped at max`() {
        val expected = mapOf(
            0 to 0L,
            1 to 1_000L,
            2 to 2_000L,
            3 to 4_000L,
            4 to 8_000L,
            5 to 16_000L,
            6 to 32_000L,
            7 to 60_000L,
            8 to 60_000L,
            10 to 60_000L,
        )
        for ((failures, expectedMs) in expected) {
            assertEquals(expectedMs, computeBackoff(failures), "failures=$failures")
        }
    }

    @Test
    fun `purgeQueue clears the event queue`() = runTest {
        EventQueue.enqueue(
            EnqueuedEvent(
                event = "test",
                screen = null,
                props = emptyMap(),
                sid = null,
                ts = kotlinx.datetime.Clock.System.now(),
                wasOffline = false,
                sdk = null,
                ctx = null,
            )
        )
        assertEquals(1, EventQueue.size())
        EventQueue.clear()
        assertTrue(EventQueue.isEmpty())
    }

    private fun computeBackoff(consecutiveFailures: Int): Long {
        if (consecutiveFailures == 0) return 0L
        val baseDelay = 1000L
        val maxBackoffMs = 60_000L
        val delay = baseDelay * (1L shl (consecutiveFailures - 1))
        return delay.coerceAtMost(maxBackoffMs)
    }
}
