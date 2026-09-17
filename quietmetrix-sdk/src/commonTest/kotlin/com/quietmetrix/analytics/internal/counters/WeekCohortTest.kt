package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertEquals

class WeekCohortTest {

    @Test
    fun `the first week of a year is W01`() {
        assertEquals("2026-W01", weekCohort(epochDayFromCivil(2026, 1, 1)))
        assertEquals("2026-W01", weekCohort(epochDayFromCivil(2026, 1, 7)))
    }

    @Test
    fun `the eighth day of the year starts week 2`() {
        assertEquals("2026-W02", weekCohort(epochDayFromCivil(2026, 1, 8)))
    }

    @Test
    fun `a known date lands in a stable week label`() {
        assertEquals("2026-W36", weekCohort(epochDayFromCivil(2026, 9, 4)))
    }

    @Test
    fun `week numbers are zero-padded below 10`() {
        assertEquals("2026-W03", weekCohort(epochDayFromCivil(2026, 1, 15)))
    }
}
