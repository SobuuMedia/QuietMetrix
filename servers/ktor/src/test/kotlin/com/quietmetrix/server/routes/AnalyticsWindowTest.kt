package com.quietmetrix.server.routes

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Proves equal UTC day windows never overlap, including sub-day counter queries. */
class AnalyticsWindowTest {
    private val now = Instant.parse("2026-10-03T12:30:00Z")

    @Test
    fun sevenDayPeriodsHaveEqualLengthAndNoSharedDate() {
        val current = analyticsWindow(604800, null, "2026-10-03", now)
        val previous = analyticsWindow(604800, "previous", "2026-10-03", now)
        assertEquals(LocalDate.parse("2026-09-27"), current.fromDay)
        assertEquals(LocalDate.parse("2026-10-03"), current.toDay)
        assertEquals(LocalDate.parse("2026-09-20"), previous.fromDay)
        assertEquals(current.fromDay.minusDays(1), previous.toDay)
        assertEquals(7L, previous.days)
    }

    @Test
    fun dailyCounterResolutionIsExplicitForShortRanges() {
        assertEquals(LocalDate.parse("2026-10-03"), analyticsWindow(3600, null, null, now).fromDay)
        assertEquals(LocalDate.parse("2026-10-02"), analyticsWindow(3600, "previous", null, now).toDay)
    }

    @Test
    fun invalidOrUnboundedWindowInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { analyticsWindow(604800, "other", null, now) }
        assertFailsWith<IllegalArgumentException> { analyticsWindow(604800, null, "2026-02-30", now) }
        assertFailsWith<IllegalArgumentException> { analyticsWindow(604800, null, "2026-10-04", now) }
        assertFailsWith<IllegalArgumentException> { analyticsWindow(604800, null, "2020-01-01", now) }
    }
}
