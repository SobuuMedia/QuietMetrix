package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class EpochDayTest {

    @Test
    fun `epoch day zero is the unix epoch`() {
        assertEquals(Triple(1970, 1, 1), civilDateFromEpochDay(0))
    }

    @Test
    fun `a known date converts correctly`() {
        assertEquals(Triple(2026, 9, 4), civilDateFromEpochDay(20700))
    }

    @Test
    fun `a leap-year boundary converts correctly`() {
        assertEquals(Triple(2000, 3, 1), civilDateFromEpochDay(11017))
    }

    @Test
    fun `a negative epoch day (before 1970) converts correctly`() {
        assertEquals(Triple(1969, 12, 31), civilDateFromEpochDay(-1))
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `utcTodayIso formats a known instant as ISO yyyy-MM-dd`() {
        val instant = Instant.fromEpochMilliseconds(20700L * 86_400_000L + 12 * 3_600_000L)
        assertEquals("2026-09-04", utcTodayIso(instant))
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `utcTodayIso pads single-digit month and day`() {
        val instant = Instant.fromEpochMilliseconds(11017L * 86_400_000L)
        assertEquals("2000-03-01", utcTodayIso(instant))
    }
}
