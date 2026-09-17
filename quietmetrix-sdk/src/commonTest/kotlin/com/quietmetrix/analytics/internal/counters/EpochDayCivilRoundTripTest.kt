package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertEquals

class EpochDayCivilRoundTripTest {

    @Test
    fun `epochDayFromCivil is the exact inverse of civilDateFromEpochDay for known dates`() {
        for (day in listOf(0L, 1L, -1L, 20700L, 11017L, 365L, 366L, 700_000L, -700_000L)) {
            val (y, m, d) = civilDateFromEpochDay(day)
            assertEquals(day, epochDayFromCivil(y, m, d), "round trip failed for epoch day $day")
        }
    }

    @Test
    fun `epochDayFromCivil matches a known vector`() {
        assertEquals(20700L, epochDayFromCivil(2026, 9, 4))
    }

    @Test
    fun `epochDayFromIso parses the ISO string form`() {
        assertEquals(20700L, epochDayFromIso("2026-09-04"))
    }
}
