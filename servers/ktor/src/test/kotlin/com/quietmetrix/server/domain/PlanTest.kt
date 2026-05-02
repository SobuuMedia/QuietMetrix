package com.quietmetrix.server.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PlanTest {

    @Test
    fun `free plan limits are correct`() {
        assertEquals(10_000L, Plan.FREE.eventsPerMonth)
        assertEquals(10, Plan.FREE.requestsPerSecond)
    }

    @Test
    fun `hobby plan limits are correct`() {
        assertEquals(100_000L, Plan.HOBBY.eventsPerMonth)
        assertEquals(50, Plan.HOBBY.requestsPerSecond)
    }

    @Test
    fun `startup plan limits are correct`() {
        assertEquals(1_000_000L, Plan.STARTUP.eventsPerMonth)
        assertEquals(200, Plan.STARTUP.requestsPerSecond)
    }

    @Test
    fun `business plan limits are correct`() {
        assertEquals(10_000_000L, Plan.BUSINESS.eventsPerMonth)
        assertEquals(1000, Plan.BUSINESS.requestsPerSecond)
    }

    @Test
    fun `plan id strings match expected values`() {
        assertEquals("free", Plan.FREE.id)
        assertEquals("hobby", Plan.HOBBY.id)
        assertEquals("startup", Plan.STARTUP.id)
        assertEquals("business", Plan.BUSINESS.id)
    }
}