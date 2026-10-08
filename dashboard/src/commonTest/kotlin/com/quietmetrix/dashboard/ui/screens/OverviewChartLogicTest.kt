package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.DailyPoint
import com.quietmetrix.dashboard.api.ReportBinDto
import com.quietmetrix.dashboard.api.ReportMeasureDto
import com.quietmetrix.dashboard.api.SessionsResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverviewChartLogicTest {

    // -- dailyVolumeBars -------------------------------------------------------

    @Test
    fun peopleMetricReadsPrivacyGatedDailyPeopleBins() {
        val agg = AggregatesResponse(dailyPeople = listOf(
            DailyPoint(day = "2026-06-28", total = 12),
            DailyPoint(day = "2026-06-29", total = 18),
        ))
        assertEquals(
            listOf(DailyVolumeBar("2026-06-28", 12), DailyVolumeBar("2026-06-29", 18)),
            dailyVolumeBars(agg, null, DailyVolumeMetric.People, hourly = false),
        )
    }

    @Test
    fun reportPeoplePointsKeepHourlyBucketsAndDropSuppressedValues() {
        val points = listOf(
            ReportBinDto("2026-06-28", "2026-06-28", hour = 9, measures = mapOf(
                "people" to ReportMeasureDto(value = 12, state = "reported", unit = "installations", source = "v2"),
            )),
            ReportBinDto("2026-06-28", "2026-06-28", hour = 10, measures = mapOf(
                "people" to ReportMeasureDto(state = "suppressed", unit = "installations", source = "v2"),
            )),
        ).chartMeasurePoints("people")
        assertEquals(listOf(DailyPoint("2026-06-28T09", 12)), points)
    }

    @Test
    fun visitsMetricReadsDailySessions() {
        val sess = SessionsResponse(
            totalSessions = 100,
            dailySessions = listOf(
                DailyPoint(day = "2026-06-28", total = 10),
                DailyPoint(day = "2026-06-29", total = 20),
            ),
        )
        val bars = dailyVolumeBars(agg = null, sess = sess, metric = DailyVolumeMetric.Visits, hourly = false)
        assertEquals(listOf(DailyVolumeBar("2026-06-28", 10), DailyVolumeBar("2026-06-29", 20)), bars)
    }

    @Test
    fun actionsMetricReadsAggregatesDaily() {
        val agg = AggregatesResponse(
            daily = listOf(
                DailyPoint(day = "2026-06-28", total = 50),
                DailyPoint(day = "2026-06-29", total = 75),
            ),
        )
        val bars = dailyVolumeBars(agg = agg, sess = null, metric = DailyVolumeMetric.Actions, hourly = false)
        assertEquals(listOf(DailyVolumeBar("2026-06-28", 50), DailyVolumeBar("2026-06-29", 75)), bars)
    }

    @Test
    fun missingResponseYieldsEmptyBars() {
        assertEquals(emptyList(), dailyVolumeBars(null, null, DailyVolumeMetric.Visits, hourly = false))
        assertEquals(emptyList(), dailyVolumeBars(null, null, DailyVolumeMetric.Actions, hourly = false))
    }

    @Test
    fun hourlyFlagFormatsBucketLabelsAsHours() {
        val agg = AggregatesResponse(daily = listOf(DailyPoint(day = "2026-06-28T14", total = 5)))
        val bars = dailyVolumeBars(agg, null, DailyVolumeMetric.Actions, hourly = true)
        assertEquals("14:00", bars.single().label)
    }

    // -- recordDayIndex ----------------------------------------------------------

    @Test
    fun recordDayIndexEmptyIsNull() {
        assertNull(recordDayIndex(emptyList()))
    }

    @Test
    fun recordDayIndexAllZeroIsNull() {
        val bars = listOf(DailyVolumeBar("a", 0), DailyVolumeBar("b", 0))
        assertNull(recordDayIndex(bars))
    }

    @Test
    fun recordDayIndexAllTiedNonzeroIsNull() {
        val bars = listOf(DailyVolumeBar("a", 10), DailyVolumeBar("b", 10))
        assertNull(recordDayIndex(bars))
    }

    @Test
    fun recordDayIndexPicksTheUniqueMax() {
        val bars = listOf(DailyVolumeBar("a", 10), DailyVolumeBar("b", 99), DailyVolumeBar("c", 30))
        assertEquals(1, recordDayIndex(bars))
    }

    @Test
    fun recordDayIndexPicksFirstOccurrenceWhenOthersTieBelowMax() {
        val bars = listOf(
            DailyVolumeBar("a", 5),
            DailyVolumeBar("b", 99),
            DailyVolumeBar("c", 5),
            DailyVolumeBar("d", 99),
        )
        // "b" and "d" both hit the max (99) but aren't ALL equal, so this is a real standout day —
        // the first occurrence is reported.
        assertEquals(1, recordDayIndex(bars))
    }

    @Test
    fun recordDayIndexSingleBarAboveZeroIsItself() {
        val bars = listOf(DailyVolumeBar("a", 7))
        // A single bar ties "all equal" with itself, so there's no OTHER day to stand out against —
        // still honestly reported as the record, since it is in fact the max (and only) value.
        assertEquals(0, recordDayIndex(bars))
    }
}
