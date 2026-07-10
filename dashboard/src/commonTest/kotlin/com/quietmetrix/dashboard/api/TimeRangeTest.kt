package com.quietmetrix.dashboard.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangeTest {

    @Test
    fun tokensAndDurationsAreCorrect() {
        assertEquals("1h", TimeRange.Hour1.token)
        assertEquals(3_600L, TimeRange.Hour1.seconds)
        assertEquals("1d", TimeRange.Day1.token)
        assertEquals(86_400L, TimeRange.Day1.seconds)
        assertEquals("7d", TimeRange.Day7.token)
        assertEquals(604_800L, TimeRange.Day7.seconds)
        assertEquals("30d", TimeRange.Day30.token)
        assertEquals("90d", TimeRange.Day90.token)
        assertEquals(7_776_000L, TimeRange.Day90.seconds)
    }

    @Test
    fun defaultIsSevenDays() {
        assertEquals(TimeRange.Day7, TimeRange.Default)
    }

    @Test
    fun fromTokenRoundTrips() {
        TimeRange.entries.forEach { r ->
            assertEquals(r, TimeRange.fromToken(r.token))
        }
    }

    @Test
    fun fromTokenFallsBackToDefault() {
        assertEquals(TimeRange.Default, TimeRange.fromToken(null))
        assertEquals(TimeRange.Default, TimeRange.fromToken("bogus"))
    }

    @Test
    fun subDayRangesAreHourly() {
        assertTrue(TimeRange.Hour1.hourly)
        assertTrue(TimeRange.Day1.hourly)
        assertFalse(TimeRange.Day7.hourly)
        assertFalse(TimeRange.Day30.hourly)
        assertFalse(TimeRange.Day90.hourly)
    }
}
