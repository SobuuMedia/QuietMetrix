package com.quietmetrix.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RangeTokenTest {

    private val fixedNow = Instant.parse("2024-06-15T12:00:00Z")

    @Test
    fun sevenDayRangeIsTheDefault() {
        assertEquals("7d", RangeToken.DEFAULT)
    }

    @Test
    fun validTokensAreExactlyTheDocumentedFive() {
        assertEquals(setOf("1h", "1d", "7d", "30d", "90d"), RangeToken.VALID)
        assertTrue(RangeToken.isValid("7d"))
        assertFalse(RangeToken.isValid("1w"))
        assertFalse(RangeToken.isValid(""))
    }

    @Test
    fun sevenDayResolvesToATrailingWeekEndingNow() {
        val resolved = RangeToken.resolve("7d", fixedNow)
        assertEquals("7d", resolved.range)
        assertEquals("2024-06-08T12:00:00Z", resolved.fromIso)
        assertEquals("2024-06-15T12:00:00Z", resolved.toIso)
        assertEquals(7, resolved.days)
    }

    @Test
    fun oneHourStillReportsAtLeastOneDay() {
        // days is a coarser fallback param some routes read (?days=N); it must never be 0.
        val resolved = RangeToken.resolve("1h", fixedNow)
        assertEquals(1, resolved.days)
    }

    @Test
    fun ninetyDayResolvesCorrectly() {
        val resolved = RangeToken.resolve("90d", fixedNow)
        assertEquals(90, resolved.days)
        assertEquals("2024-03-17T12:00:00Z", resolved.fromIso)
    }

    @Test
    fun anUnknownTokenFallsBackToSevenDaysOfWindow() {
        // range echoes back whatever the caller passed (the literal query value); only the
        // computed window (from/to/days) actually falls back to the 7d default.
        val sevenDay = RangeToken.resolve("7d", fixedNow)
        val unknown = RangeToken.resolve("bogus", fixedNow)
        assertEquals(sevenDay.fromIso, unknown.fromIso)
        assertEquals(sevenDay.toIso, unknown.toIso)
        assertEquals(sevenDay.days, unknown.days)
    }

    @Test
    fun appendToBuildsRangeFromToAndDaysWithColonsEncoded() {
        val resolved = RangeToken.resolve("1d", fixedNow)
        val url = RangeToken.appendTo("https://qm.example.com/api/v1/projects/proj_1/aggregates", resolved)
        assertEquals(
            "https://qm.example.com/api/v1/projects/proj_1/aggregates" +
                "?range=1d&from=2024-06-14T12%3A00%3A00Z&to=2024-06-15T12%3A00%3A00Z&days=1",
            url,
        )
    }
}
