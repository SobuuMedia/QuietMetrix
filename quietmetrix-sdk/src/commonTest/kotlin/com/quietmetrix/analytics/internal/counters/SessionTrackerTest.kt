package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SessionTrackerTest {

    @AfterTest
    fun tearDown() {
        SessionTracker.reset()
        MetricGateway.reset()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `stop records a session counter bucketed from the elapsed time`() = runTest {
        val start = Instant.fromEpochMilliseconds(20700L * 86_400_000L)
        val end = start.plus(kotlin.time.Duration.parse("PT45S"))

        SessionTracker.start(start)
        SessionTracker.stop(end)

        val pending = MetricGateway.drain()
        assertEquals(1, pending.size)
        assertEquals("session", pending[0].metric)
        assertEquals(mapOf("bucket" to "30_60s"), pending[0].dims)
    }

    @Test
    fun `stop without a prior start records nothing`() = runTest {
        SessionTracker.stop()
        assertTrue(MetricGateway.drain().isEmpty())
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `stop is a no-op the second time until start is called again`() = runTest {
        val start = Instant.fromEpochMilliseconds(20700L * 86_400_000L)
        SessionTracker.start(start)
        SessionTracker.stop(start)
        MetricGateway.drain()

        SessionTracker.stop(start)
        assertTrue(MetricGateway.drain().isEmpty())
    }
}
