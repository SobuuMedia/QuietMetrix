package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCounterBridgeTest {

    @AfterTest
    fun tearDown() {
        MetricGateway.reset()
    }

    @Test
    fun `records an event counter keyed by name only`() = runTest {
        recordEventCounter("button_click", screen = null, props = mapOf("button_id" to "save"), debug = false)

        val pending = MetricGateway.drain()
        assertEquals(1, pending.size)
        assertEquals("event", pending[0].metric)
        assertEquals(mapOf("name" to "button_click"), pending[0].dims)
    }

    @Test
    fun `props are never present in the recorded cell, debug or not`() = runTest {
        recordEventCounter("button_click", screen = null, props = mapOf("button_id" to "save"), debug = true)

        val dims = MetricGateway.drain().single().dims
        assertEquals(setOf("name"), dims.keys)
    }

    @Test
    fun `repeated calls for the same event name accumulate into one cell`() = runTest {
        recordEventCounter("page_view", screen = null, props = emptyMap(), debug = false)
        recordEventCounter("page_view", screen = null, props = emptyMap(), debug = false)

        val pending = MetricGateway.drain()
        assertEquals(1, pending.size)
        assertEquals(2L, pending[0].n)
    }
}
