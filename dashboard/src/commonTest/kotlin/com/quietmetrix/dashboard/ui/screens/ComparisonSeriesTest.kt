package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.DailyPoint
import kotlin.test.Test
import kotlin.test.assertEquals

/** Sparse dates align by calendar distance; missing previous buckets remain unavailable. */
class ComparisonSeriesTest {
    @Test
    fun sparsePreviousDatesAreNotAlignedByListPosition() {
        val current = listOf(DailyPoint("2026-10-01", 8), DailyPoint("2026-10-03", 20))
        val previous = listOf(DailyPoint("2026-09-24", 3), DailyPoint("2026-09-25", 12))
        assertEquals(listOf(3f, null), comparisonDailyValues(current, previous, 7))
    }
    @Test
    fun longWindowUsesEqualWeeklyBlocksWithoutInventingMissingDays() {
        val current = analyticsChartPoints(listOf(DailyPoint("2026-07-06", 2), DailyPoint("2026-07-08", 3), DailyPoint("2026-07-13", 4)), com.quietmetrix.dashboard.api.TimeRange.Day90, "2026-10-03")
        val previous = analyticsChartPoints(listOf(DailyPoint("2026-04-07", 9), DailyPoint("2026-04-09", 1)), com.quietmetrix.dashboard.api.TimeRange.Day90, "2026-10-03", previous = true)
        assertEquals(listOf(5L, 4L), current.map { it.total })
        assertEquals(listOf(10f, null), comparisonDailyValues(current, previous, 90))
    }
}
