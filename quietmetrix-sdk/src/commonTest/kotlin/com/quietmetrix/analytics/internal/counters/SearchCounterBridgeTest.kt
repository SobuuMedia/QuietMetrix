package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchCounterBridgeTest {

    @AfterTest
    fun tearDown() {
        MetricGateway.reset()
    }

    @Test
    fun `a search with results records only the search counter`() = runTest {
        recordSearchCounter("Library", resultCount = 5, debug = false)

        val pending = MetricGateway.drain()
        assertEquals(1, pending.size)
        assertEquals("search", pending[0].metric)
        assertEquals(mapOf("screen" to "Library"), pending[0].dims)
    }

    @Test
    fun `a zero-result search records both the search and zero-result counters`() = runTest {
        recordSearchCounter("Library", resultCount = 0, debug = false)

        val pending = MetricGateway.drain()
        assertEquals(2, pending.size)
        val byMetric = pending.associateBy { it.metric }
        assertEquals(mapOf("screen" to "Library"), byMetric.getValue("search").dims)
        assertEquals(mapOf("screen" to "Library"), byMetric.getValue("search_zero_result").dims)
    }

    @Test
    fun `a null screen falls back to a fixed placeholder dimension`() = runTest {
        recordSearchCounter(null, resultCount = 3, debug = false)

        assertEquals("unknown", MetricGateway.drain().single().dims["screen"])
    }

    @Test
    fun `a negative result count is not recorded`() = runTest {
        recordSearchCounter("Library", resultCount = -1, debug = false)

        assertTrue(MetricGateway.drain().isEmpty())
    }

    @Test
    fun `repeated zero-result searches on the same screen accumulate`() = runTest {
        recordSearchCounter("Library", resultCount = 0, debug = false)
        recordSearchCounter("Library", resultCount = 0, debug = false)

        val pending = MetricGateway.drain()
        val zeroResult = pending.single { it.metric == "search_zero_result" }
        assertEquals(2L, zeroResult.n)
    }
}
