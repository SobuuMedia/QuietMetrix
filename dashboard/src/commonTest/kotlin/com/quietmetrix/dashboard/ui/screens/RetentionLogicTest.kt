package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.ui.components.CohortGrouping
import com.quietmetrix.dashboard.ui.components.buildCohortHeatmapRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Unit tests for [computeRetentionVitals], [dotCountForFraction] and [retentionHeadlineKind].
 *
 * Every [computeRetentionVitals] test below pins `todayEpochDay` explicitly (via
 * [epochDayFromIsoOrNull]) rather than relying on the real wall clock, so "how old is this cohort"
 * math is deterministic regardless of when the test suite actually runs.
 */
class RetentionLogicTest {

    /** A fixed "today" (2026-10-02) every age-dependent test below measures cohorts against. */
    private val today = epochDayFromIsoOrNull("2026-10-02")!!

    // ---- computeRetentionVitals ----

    @Test
    fun allNullWhenResponseIsNull() {
        val vitals = computeRetentionVitals(null, today)
        assertNull(vitals.day1)
        assertNull(vitals.day7)
        assertNull(vitals.day30)
    }

    @Test
    fun allNullWhenNoCohorts() {
        val vitals = computeRetentionVitals(RetentionResponse(cohorts = emptyList()), today)
        assertNull(vitals.day1)
        assertNull(vitals.day7)
        assertNull(vitals.day30)
    }

    @Test
    fun picksTheMostRecentCohortThatHasActuallyObservedEachDayIndependently() {
        // The newest cohort (09-28, 4 days old as of "today") only has day1 so far — it's too
        // young for day7/day30, which must fall back to the one older cohort old enough to have
        // reached those ages, not silently stay null.
        val response = RetentionResponse(
            cohorts = listOf(
                RetentionCohort(cohortDate = "2026-08-01", size = 100, day1 = 0.30, day7 = 0.18, day30 = 0.08),
                RetentionCohort(cohortDate = "2026-09-28", size = 50, day1 = 0.40, day7 = null, day30 = null),
            ),
        )
        val vitals = computeRetentionVitals(response, today)
        assertEquals(0.40, vitals.day1)
        assertEquals(0.18, vitals.day7)
        assertEquals(0.08, vitals.day30)
    }

    @Test
    fun zeroSizeCohortIsExcludedEvenWithANonNullRatio() {
        val response = RetentionResponse(
            cohorts = listOf(RetentionCohort(cohortDate = "2026-09-01", size = 0, day1 = 0.0)),
        )
        assertNull(computeRetentionVitals(response, today).day1)
    }

    // ---- effectiveDayValue disambiguation (post-review fix) ----
    //
    // The bug this section guards against: RetentionCounterAnalyzer.analyze produces the SAME
    // null for "no cohort is old enough yet" and "a cohort IS old enough, but nobody returned" —
    // the server never writes an explicit "0 returned" cell, only a count when someone actually
    // did return. An earlier version of computeRetentionVitals conflated these into one "too
    // early" meaning, which silently mislabeled a genuine 0% retention result as "unknown."

    @Test
    fun aCohortOldEnoughToHaveReachedDayNWithNoRawValueIsAConfirmedZero() {
        // 2026-09-22 is exactly 10 days before "today" (2026-10-02) — old enough for day7 (10 >= 7)
        // but not for day30 (10 < 30).
        val cohort = RetentionCohort(cohortDate = "2026-09-22", size = 40, day1 = 0.5, day7 = null, day30 = null)
        val response = RetentionResponse(cohorts = listOf(cohort))
        val vitals = computeRetentionVitals(response, today)
        assertEquals(0.5, vitals.day1) // real observed ratio, untouched
        assertEquals(0.0, vitals.day7) // aged out, nobody returned -> a real, confirmed 0%
        assertNull(vitals.day30) // not old enough yet for day30 -> honestly unknown, not 0%
    }

    @Test
    fun aCohortTooYoungForDayNWithNoRawValueStaysNullRatherThanAFabricatedZero() {
        // 2026-09-30 is only 2 days before "today" — too young for day7 or day30.
        val cohort = RetentionCohort(cohortDate = "2026-09-30", size = 40, day1 = 0.6, day7 = null, day30 = null)
        val response = RetentionResponse(cohorts = listOf(cohort))
        val vitals = computeRetentionVitals(response, today)
        assertEquals(0.6, vitals.day1)
        assertNull(vitals.day7)
        assertNull(vitals.day30)
    }

    @Test
    fun aMalformedCohortDateCannotBeAgedOutSoItStaysNullRatherThanAFabricatedZero() {
        val cohort = RetentionCohort(cohortDate = "not-a-date", size = 40, day1 = null, day7 = null, day30 = null)
        val response = RetentionResponse(cohorts = listOf(cohort))
        assertNull(computeRetentionVitals(response, today).day1)
    }

    @Test
    fun aMostRecentAgedOutConfirmedZeroCohortIsPreferredOverAnOlderRealNonZeroCohort() {
        // This is the scenario the bug actually broke: without the fix, the 09-22 cohort (the
        // more recent one) would have been wrongly excluded for day7 (its raw value is null), so
        // the result would fall back to the OLDER cohort's real-but-stale 18% — misreporting the
        // most recent cohort's genuine 0% as 18%.
        val response = RetentionResponse(
            cohorts = listOf(
                RetentionCohort(cohortDate = "2026-08-01", size = 100, day1 = 0.30, day7 = 0.18, day30 = 0.08),
                RetentionCohort(cohortDate = "2026-09-22", size = 50, day1 = 0.50, day7 = null, day30 = null),
            ),
        )
        val vitals = computeRetentionVitals(response, today)
        assertEquals(0.0, vitals.day7) // the more recent cohort's confirmed zero wins, not 0.18
        assertEquals(0.08, vitals.day30) // the 09-22 cohort is too young for day30 (10 < 30), so this still falls back
    }

    // ---- epochDayFromIsoOrNull ----

    @Test
    fun epochDayRoundTripsAKnownDate() {
        // 1970-01-01 is epoch day 0 by definition.
        assertEquals(0L, epochDayFromIsoOrNull("1970-01-01"))
        assertEquals(1L, epochDayFromIsoOrNull("1970-01-02"))
    }

    @Test
    fun epochDayDifferenceMatchesACalendarGapAcrossAMonthBoundary() {
        // 2026-09-22 -> 2026-10-02 is 10 days (8 remaining in September + 2 in October).
        assertEquals(10L, epochDayFromIsoOrNull("2026-10-02")!! - epochDayFromIsoOrNull("2026-09-22")!!)
    }

    @Test
    fun epochDayIsNullForAMalformedDate() {
        assertNull(epochDayFromIsoOrNull("not-a-date"))
        assertNull(epochDayFromIsoOrNull("2026-13"))
    }

    // ---- dotCountForFraction ----

    @Test
    fun zeroPercentIsZeroFilledDots() {
        assertEquals(0, dotCountForFraction(0.0))
    }

    @Test
    fun oneHundredPercentIsTenFilledDots() {
        assertEquals(10, dotCountForFraction(1.0))
    }

    @Test
    fun fortyTwoPercentRoundsDownToFourDots() {
        assertEquals(4, dotCountForFraction(0.42))
    }

    @Test
    fun fortyFivePercentRoundsUpToFiveDots() {
        // Exact tie (4.5) rounds up, matching kotlin.math.roundToInt's round-half-up behavior.
        assertEquals(5, dotCountForFraction(0.45))
    }

    @Test
    fun aFractionSlightlyAboveOneClampsToTenDots() {
        assertEquals(10, dotCountForFraction(1.004))
    }

    @Test
    fun aFractionSlightlyBelowZeroClampsToZeroDots() {
        assertEquals(0, dotCountForFraction(-0.001))
    }

    @Test
    fun aNonDefaultTotalDotsIsRespected() {
        assertEquals(3, dotCountForFraction(0.5, totalDots = 6))
    }

    // ---- retentionHeadlineKind ----

    @Test
    fun bothPresentWhenDay1AndDay7AreBothAvailable() {
        assertEquals(RetentionHeadlineKind.Both, retentionHeadlineKind(0.4, 0.2))
    }

    @Test
    fun day1OnlyWhenOnlyDay1IsAvailable() {
        assertEquals(RetentionHeadlineKind.Day1Only, retentionHeadlineKind(0.4, null))
    }

    @Test
    fun day7OnlyWhenOnlyDay7IsAvailable() {
        assertEquals(RetentionHeadlineKind.Day7Only, retentionHeadlineKind(null, 0.2))
    }

    @Test
    fun notEnoughDataWhenNeitherIsAvailable() {
        assertEquals(RetentionHeadlineKind.NotEnoughData, retentionHeadlineKind(null, null))
    }

    // ---- computeRetentionComparisonDayColumns (Task 26: the paired-bar-chart's per-column data) ----

    @Test
    fun dayColumnsMarkBothKnownOnlyWhenNeitherSideIsNull() {
        val rowA = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-08-01", size = 100, day1 = 0.5, day7 = 0.3)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val rowB = buildCohortHeatmapRows(
            // 2026-09-30 is too young for day7 as of "today" (2026-10-02) -> that column's B side is null.
            listOf(RetentionCohort(cohortDate = "2026-09-30", size = 50, day1 = 0.6, day7 = null)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val columns = computeRetentionComparisonDayColumns(rowA, rowB)
        val day1 = columns.single { it.dayNumber == 1 }
        val day7 = columns.single { it.dayNumber == 7 }
        assertEquals(true, day1.bothKnown)
        assertEquals(false, day7.bothKnown) // B's day7 is honestly unknown (too young), not a fabricated 0
        assertNull(day7.bFraction)
    }

    // ---- computeRetentionComparisonTakeaway (Task 26: "⚖️ Comparing A vs B" panel) ----

    @Test
    fun comparisonTakeawayPicksTheHighestDayNumberBothRowsHaveObserved() {
        // Day30 is too young for row B — day14 is the last ("most-advanced") column both rows share.
        val rowA = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-08-01", size = 100, day1 = 0.5, day3 = 0.4, day7 = 0.3, day14 = 0.2, day30 = 0.1)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val rowB = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-09-20", size = 50, day1 = 0.6, day3 = 0.5, day7 = 0.4, day14 = 0.35, day30 = null)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val takeaway = assertIs<RetentionComparisonTakeaway.Comparison>(computeRetentionComparisonTakeaway(rowA, rowB))
        assertEquals(14, takeaway.dayNumber)
        assertEquals(0.2, takeaway.aFraction)
        assertEquals(0.35, takeaway.bFraction)
        assertEquals(15, takeaway.deltaPoints) // (0.35 - 0.2) * 100, rounded
    }

    @Test
    fun comparisonTakeawayIsNotEnoughDataWhenNoDayColumnIsSharedByBothRows() {
        // Row B's cohort is dated "today" itself (0 days old) — too young for EVERY day column,
        // including day1, so it can never share a known value with row A at any column.
        val rowA = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-08-01", size = 100, day1 = 0.5)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val rowB = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-10-02", size = 50, day1 = null)),
            CohortGrouping.ByDay,
            today,
        ).single()
        assertEquals(RetentionComparisonTakeaway.NotEnoughData, computeRetentionComparisonTakeaway(rowA, rowB))
    }

    @Test
    fun comparisonTakeawayNeverTreatsAConfirmedZeroAsUnknown() {
        // Row B is aged-out (10 days old as of "today") with a genuine, confirmed 0% at day7 — this
        // must still be treated as a real, comparable value, not silently excluded as "too young".
        val rowA = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-08-01", size = 100, day7 = 0.3)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val rowB = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-09-22", size = 50, day7 = null)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val takeaway = assertIs<RetentionComparisonTakeaway.Comparison>(computeRetentionComparisonTakeaway(rowA, rowB))
        assertEquals(7, takeaway.dayNumber)
        assertEquals(0.3, takeaway.aFraction)
        assertEquals(0.0, takeaway.bFraction)
        assertEquals(-30, takeaway.deltaPoints)
    }

    @Test
    fun comparisonTakeawayDeltaIsZeroWhenBothRowsMatchExactly() {
        val rowA = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-08-01", size = 100, day1 = 0.42)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val rowB = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-08-15", size = 80, day1 = 0.42)),
            CohortGrouping.ByDay,
            today,
        ).single()
        val takeaway = assertIs<RetentionComparisonTakeaway.Comparison>(computeRetentionComparisonTakeaway(rowA, rowB))
        assertEquals(0, takeaway.deltaPoints)
    }

    // ---- computeRetentionClosingInsightTakeaway (Task 26: closing insight card) ----

    @Test
    fun closingInsightIsNotEnoughDataWithFewerThanTwoCohorts() {
        assertEquals(
            RetentionComparisonTakeaway.NotEnoughData,
            computeRetentionClosingInsightTakeaway(emptyList(), today),
        )
        assertEquals(
            RetentionComparisonTakeaway.NotEnoughData,
            computeRetentionClosingInsightTakeaway(
                listOf(RetentionCohort(cohortDate = "2026-08-01", size = 10, day1 = 0.5)),
                today,
            ),
        )
    }

    @Test
    fun closingInsightComparesTheLatestCohortAgainstThePreviousOne() {
        // Previous (11 days old) is also old enough to have aged out at day3/day7, which would
        // otherwise be a MORE-advanced mutually-known column than day1 (via effectiveDayValue's
        // confirmed-zero path) — the latest cohort here is only 1 day old, so day1 is deliberately
        // the only column both sides have actually reached.
        val cohorts = listOf(
            RetentionCohort(cohortDate = "2026-09-21", size = 100, day1 = 0.30),
            RetentionCohort(cohortDate = "2026-10-01", size = 50, day1 = 0.45),
        )
        val takeaway = assertIs<RetentionComparisonTakeaway.Comparison>(computeRetentionClosingInsightTakeaway(cohorts, today))
        assertEquals(1, takeaway.dayNumber)
        assertEquals("2026-09-21", takeaway.aLabel) // previous (older)
        assertEquals(0.30, takeaway.aFraction)
        assertEquals("2026-10-01", takeaway.bLabel) // latest (newer)
        assertEquals(0.45, takeaway.bFraction)
        assertEquals(15, takeaway.deltaPoints)
    }

    @Test
    fun closingInsightIsNotEnoughDataWhenTheLatestCohortHasNoComparableDayYet() {
        // The latest cohort is dated "today" itself (0 days old) — too young for EVERY day
        // column, including day1, regardless of how old (and how aged-out-confirmed-zero) the
        // previous cohort is.
        val cohorts = listOf(
            RetentionCohort(cohortDate = "2026-08-01", size = 100, day7 = 0.30),
            RetentionCohort(cohortDate = "2026-10-02", size = 50, day1 = null, day7 = null),
        )
        assertEquals(RetentionComparisonTakeaway.NotEnoughData, computeRetentionClosingInsightTakeaway(cohorts, today))
    }

    @Test
    fun closingInsightFallsBackToAnEarlierSharedDayWhenTheNewestDayIsntMutuallyObserved() {
        // Both cohorts share day1, but the latest cohort (09-30) is too young for day7 — the
        // takeaway must fall back to day1 rather than reporting "not enough data".
        val cohorts = listOf(
            RetentionCohort(cohortDate = "2026-08-01", size = 100, day1 = 0.30, day7 = 0.10),
            RetentionCohort(cohortDate = "2026-09-30", size = 50, day1 = 0.50, day7 = null),
        )
        val takeaway = assertIs<RetentionComparisonTakeaway.Comparison>(computeRetentionClosingInsightTakeaway(cohorts, today))
        assertEquals(1, takeaway.dayNumber)
        assertEquals(0.30, takeaway.aFraction)
        assertEquals(0.50, takeaway.bFraction)
    }
}
