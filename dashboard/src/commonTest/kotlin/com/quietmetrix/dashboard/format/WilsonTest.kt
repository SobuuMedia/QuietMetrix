package com.quietmetrix.dashboard.format

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WilsonTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.005) {
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, got $actual")
    }

    @Test
    fun `a zero-total sample has no defined interval`() {
        assertEquals(null, wilsonInterval(0, 0))
    }

    @Test
    fun `a small sample has a wide interval around the observed rate`() {
        // 1 of 4: a naive 25% reads confidently; Wilson shows how little that means at n=4.
        val interval = wilsonInterval(1, 4)!!
        assertTrue(interval.lower < 0.1)
        assertTrue(interval.upper > 0.6)
    }

    @Test
    fun `a large sample has a narrow interval close to the observed rate`() {
        // 400 of 1000 -> 40%, with n=1000 the interval should hug 0.40 tightly.
        val interval = wilsonInterval(400, 1000)!!
        assertClose(0.371, interval.lower)
        assertClose(0.430, interval.upper)
    }

    @Test
    fun `all successes still has an interval below 100 percent`() {
        // Wilson never claims certainty from a finite sample, unlike a naive n/n = 100%.
        val interval = wilsonInterval(10, 10)!!
        assertTrue(interval.upper <= 1.0)
        assertTrue(interval.upper > 0.9)
        assertTrue(interval.lower < 1.0)
    }

    @Test
    fun `zero successes still has an interval above 0 percent`() {
        val interval = wilsonInterval(0, 10)!!
        assertTrue(interval.lower >= 0.0)
        assertTrue(interval.upper > 0.0)
    }

    @Test
    fun `the interval always contains the observed rate`() {
        val interval = wilsonInterval(3, 20)!!
        val observed = 3.0 / 20.0
        assertTrue(observed in interval.lower..interval.upper)
    }

    @Test
    fun `a narrower confidence level produces a narrower interval`() {
        val wide = wilsonInterval(50, 100, confidence = 0.99)!!
        val narrow = wilsonInterval(50, 100, confidence = 0.80)!!
        assertTrue((narrow.upper - narrow.lower) < (wide.upper - wide.lower))
    }

    @Test
    fun `formatWilsonRange renders as two percentages`() {
        val interval = WilsonInterval(0.371, 0.430)
        assertEquals("37–43%", formatWilsonRange(interval))
    }
}
