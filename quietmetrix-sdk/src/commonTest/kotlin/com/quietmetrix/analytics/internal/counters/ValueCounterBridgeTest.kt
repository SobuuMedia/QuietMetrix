package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValueCounterBridgeTest {

    @AfterTest
    fun tearDown() {
        MetricGateway.reset()
    }

    @Test
    fun `records a value counter keyed by name, with n set to the amount`() = runTest {
        recordValueCounter("purchase", amountMinorUnits = 499, debug = false)

        val pending = MetricGateway.drain()
        assertEquals(1, pending.size)
        assertEquals("value", pending[0].metric)
        assertEquals(mapOf("name" to "purchase"), pending[0].dims)
        assertEquals(499L, pending[0].n)
    }

    @Test
    fun `repeated calls for the same name sum their amounts into one cell`() = runTest {
        recordValueCounter("purchase", amountMinorUnits = 499, debug = false)
        recordValueCounter("purchase", amountMinorUnits = 999, debug = false)

        val pending = MetricGateway.drain()
        assertEquals(1, pending.size)
        assertEquals(1498L, pending[0].n)
    }

    @Test
    fun `a negative amount is accepted, for refunds or adjustments`() = runTest {
        recordValueCounter("purchase", amountMinorUnits = 499, debug = false)
        recordValueCounter("purchase", amountMinorUnits = -499, debug = false)

        val pending = MetricGateway.drain()
        assertEquals(1, pending.size)
        assertEquals(0L, pending[0].n)
    }

    @Test
    fun `a blank name is not recorded`() = runTest {
        recordValueCounter("", amountMinorUnits = 100, debug = false)

        assertTrue(MetricGateway.drain().isEmpty())
    }

    @Test
    fun `distinct names accumulate into distinct cells`() = runTest {
        recordValueCounter("purchase", amountMinorUnits = 499, debug = false)
        recordValueCounter("subscription", amountMinorUnits = 999, debug = false)

        val pending = MetricGateway.drain()
        assertEquals(2, pending.size)
        assertEquals(setOf(mapOf("name" to "purchase"), mapOf("name" to "subscription")), pending.map { it.dims }.toSet())
    }
}
