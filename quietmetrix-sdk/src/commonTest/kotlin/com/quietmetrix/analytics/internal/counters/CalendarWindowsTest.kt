package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarWindowsTest {
    @Test fun `rolling calendar projections preserve every supported window across a year boundary`() {
        val windows = MetricGateway.projectWindows("2026-12-31")
        assertEquals(148, windows.size)
        assertEquals(mapOf("1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5, "6" to 6, "7" to 7, "30" to 30, "90" to 90),
            windows.groupingBy { it.first.getValue("days") }.eachCount())
        assertEquals("2027-03-30", windows.last().second)
        assertTrue(windows.all { it.first["end_day"] == it.second && it.first["scope_kind"] == "project" })
    }

    @Test fun `cached calendar projections follow the requested UTC day including out of order events`() {
        val first = MetricGateway.projectWindows("2026-12-31")
        val next = MetricGateway.projectWindows("2027-01-01")
        assertEquals("2027-01-01", next.first().second)
        assertEquals(first, MetricGateway.projectWindows("2026-12-31"))
    }
}
