package com.quietmetrix.server.funnels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PercentileTest {

    @Test
    fun `empty input returns null`() {
        assertNull(Percentile.of(emptyList(), 50.0))
    }

    @Test
    fun `a single value is returned for any percentile`() {
        assertEquals(42L, Percentile.of(listOf(42L), 50.0))
        assertEquals(42L, Percentile.of(listOf(42L), 0.0))
        assertEquals(42L, Percentile.of(listOf(42L), 100.0))
    }

    @Test
    fun `median of two values is the lower one under nearest-rank`() {
        assertEquals(10L, Percentile.of(listOf(10L, 20L), 50.0))
    }

    @Test
    fun `p90 of two values is the higher one`() {
        assertEquals(20L, Percentile.of(listOf(10L, 20L), 90.0))
    }

    @Test
    fun `input order does not matter`() {
        assertEquals(10L, Percentile.of(listOf(20L, 10L), 50.0))
    }

    @Test
    fun `median of an odd-sized list is the middle value`() {
        assertEquals(30L, Percentile.of(listOf(10L, 20L, 30L, 40L, 50L), 50.0))
    }

    @Test
    fun `p90 of ten values is the ninth smallest`() {
        val values = (1..10).map { it.toLong() * 10 }
        assertEquals(90L, Percentile.of(values, 90.0))
    }
}
