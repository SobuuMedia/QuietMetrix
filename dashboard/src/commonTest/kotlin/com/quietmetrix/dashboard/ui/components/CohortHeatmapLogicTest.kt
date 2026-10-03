package com.quietmetrix.dashboard.ui.components

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.ui.screens.epochDayFromIsoOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [CohortHeatmapLogic.kt]: the cohort heatmap's pure (cohort × day) grid-building,
 * intensity-mapping, and row-selection logic — no Compose host needed.
 *
 * As with `RetentionLogicTest`, every age-dependent test pins `todayEpochDay` explicitly so "is this
 * cohort old enough for day-N" math is deterministic regardless of wall-clock time.
 */
class CohortHeatmapLogicTest {

    private val today = epochDayFromIsoOrNull("2026-10-02")!!

    // ---- buildCohortHeatmapRows: ByDay — reuses effectiveDayValue's too-young vs. confirmed-zero split ----

    @Test
    fun byDayTooYoungCohortCellIsNullNotAFabricatedZero() {
        // 2026-09-30 is 2 days before "today" — too young for day7/day30.
        val cohorts = listOf(RetentionCohort(cohortDate = "2026-09-30", size = 40, day1 = 0.6, day7 = null, day30 = null))
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByDay, today)
        assertEquals(1, rows.size)
        val cells = rows[0].cells // [day1, day3, day7, day14, day30]
        assertEquals(0.6, cells[0].fraction) // day1: real observed ratio
        assertNull(cells[2].fraction) // day7: honestly unknown (too young)
        assertEquals(CohortHeatmapCellState.Future, cells[2].state)
        assertNull(cells[4].fraction) // day30: honestly unknown (too young)
    }

    @Test
    fun byDayAgedOutCohortWithNoRawValueIsUnavailableNotAZero() {
        // 2026-09-22 is 10 days before "today" — old enough for day7 (10>=7) but not day30 (10<30).
        val cohorts = listOf(RetentionCohort(cohortDate = "2026-09-22", size = 40, day1 = 0.5, day7 = null, day30 = null))
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByDay, today)
        val cells = rows[0].cells
        assertEquals(0.5, cells[0].fraction)
        assertNull(cells[2].fraction) // day7: may be privacy-suppressed; age cannot prove zero
        assertEquals(CohortHeatmapCellState.Unavailable, cells[2].state)
        assertNull(cells[4].fraction) // day30: still too young
    }

    @Test
    fun byDayRowsAreOrderedNewestFirst() {
        val cohorts = listOf(
            RetentionCohort(cohortDate = "2026-08-01", size = 10, day1 = 0.1),
            RetentionCohort(cohortDate = "2026-09-20", size = 10, day1 = 0.2),
        )
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByDay, today)
        assertEquals(listOf("2026-09-20", "2026-08-01"), rows.map { it.id })
    }

    @Test
    fun byDayPreservesAMalformedCohortDateAsItsOwnRow() {
        // ByDay needs no week bucket, so a date that fails to parse still gets a row.
        val cohorts = listOf(RetentionCohort(cohortDate = "not-a-date", size = 5, day1 = 0.1))
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByDay, today)
        assertEquals(1, rows.size)
    }

    // ---- buildCohortHeatmapRows: ByWeek — epoch-anchored 7-day bucketing + weighted aggregation ----

    @Test
    fun byWeekGroupsCohortsInTheSameSevenDayEpochBucketIntoOneRow() {
        // 2026-09-21 and 2026-09-22 are adjacent days; whether they land in the same epoch-anchored
        // 7-day bucket depends only on floorDiv(7), asserted here rather than assumed.
        val d1 = epochDayFromIsoOrNull("2026-09-21")!!
        val d2 = epochDayFromIsoOrNull("2026-09-22")!!
        assertTrue(d1.floorDiv(7) == d2.floorDiv(7), "test fixture assumes these two dates share a week bucket")
        val cohorts = listOf(
            RetentionCohort(cohortDate = "2026-09-21", size = 50, day1 = 0.40),
            RetentionCohort(cohortDate = "2026-09-22", size = 50, day1 = 0.20),
        )
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByWeek, today)
        assertEquals(1, rows.size)
        assertEquals(100, rows[0].size) // sizes summed
        assertEquals("2026-09-21", rows[0].label) // earliest date in the bucket
    }

    @Test
    fun byWeekCellIsUnavailableWhenAMatureMemberHasNoReportedCount() {
        val d1 = epochDayFromIsoOrNull("2026-09-21")!!
        val d2 = epochDayFromIsoOrNull("2026-09-22")!!
        assertTrue(d1.floorDiv(7) == d2.floorDiv(7))
        // One mature member's count is hidden; averaging only the visible cohort would misstate the week.
        val cohorts = listOf(
            RetentionCohort(cohortDate = "2026-09-21", size = 30, day1 = null, day7 = 0.40),
            RetentionCohort(cohortDate = "2026-09-22", size = 10, day1 = null, day7 = null),
        )
        // Both dates are old enough; the absent count must stay unavailable rather than be inferred as zero.
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByWeek, today)
        val day7Cell = rows[0].cells[2]
        assertNull(day7Cell.fraction)
        assertEquals(CohortHeatmapCellState.Unavailable, day7Cell.state)
    }

    @Test
    fun byWeekCellExcludesTooYoungMembersFromTheWeightedAverageRatherThanTreatingThemAsZero() {
        val d1 = epochDayFromIsoOrNull("2026-09-21")!!
        val d2 = epochDayFromIsoOrNull("2026-09-22")!!
        assertTrue(d1.floorDiv(7) == d2.floorDiv(7))
        // One real 40% cohort + one cohort too young to have reached day30 yet (only 10-11 days old).
        val cohorts = listOf(
            RetentionCohort(cohortDate = "2026-09-21", size = 30, day30 = 0.40),
            RetentionCohort(cohortDate = "2026-09-22", size = 10, day30 = null),
        )
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByWeek, today)
        val day30Cell = rows[0].cells[4]
        // If the too-young member were wrongly averaged in as 0, this would be (30*0.4+10*0)/40=0.3.
        // The honest weighted average, excluding it entirely, is just the one real contributor: 0.4.
        assertEquals(0.40, day30Cell.fraction!!, 1e-9)
    }

    @Test
    fun byWeekCellIsNullWhenEveryMemberIsStillTooYoung() {
        val cohorts = listOf(RetentionCohort(cohortDate = "2026-09-30", size = 20, day30 = null))
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByWeek, today)
        assertNull(rows[0].cells[4].fraction)
    }

    @Test
    fun byWeekDropsCohortsWithAnUnparseableDateEntirely() {
        val cohorts = listOf(
            RetentionCohort(cohortDate = "not-a-date", size = 999, day1 = 0.9),
            RetentionCohort(cohortDate = "2026-09-21", size = 10, day1 = 0.1),
        )
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByWeek, today)
        assertEquals(1, rows.size)
        assertEquals(10, rows[0].size) // the unparseable cohort's size (999) must NOT leak in
    }

    @Test
    fun byWeekRowsAreOrderedNewestBucketFirst() {
        val cohorts = listOf(
            RetentionCohort(cohortDate = "2026-08-01", size = 10, day1 = 0.1),
            RetentionCohort(cohortDate = "2026-09-20", size = 10, day1 = 0.2),
        )
        val rows = buildCohortHeatmapRows(cohorts, CohortGrouping.ByWeek, today)
        assertEquals(2, rows.size)
        assertEquals("2026-09-20", rows[0].label)
        assertEquals("2026-08-01", rows[1].label)
    }

    // ---- cohortHeatmapCellAlpha / cohortHeatmapCellTextIsDark: intensity mapping ----

    @Test
    fun zeroFractionIsStillTheLightestVisibleShadeNotFullyTransparent() {
        // A confirmed, legitimate 0% must be visibly shaded (never indistinguishable from "no data").
        assertEquals(0.12f, cohortHeatmapCellAlpha(0.0))
    }

    @Test
    fun fiftyPercentFractionReachesFullAlpha() {
        assertEquals(1.0f, cohortHeatmapCellAlpha(0.5))
    }

    @Test
    fun aboveFiftyPercentFractionClampsToFullAlphaRatherThanOverflowing() {
        assertEquals(1.0f, cohortHeatmapCellAlpha(0.9))
    }

    @Test
    fun twentyFivePercentFractionIsHalfwayBetweenFloorAndCeiling() {
        // 0.12 + (25/50)*0.88 = 0.12 + 0.44 = 0.56
        assertEquals(0.56f, cohortHeatmapCellAlpha(0.25), 1e-6f)
    }

    @Test
    fun textIsDarkOnceFractionReachesTheTwentyFivePercentThreshold() {
        assertTrue(cohortHeatmapCellTextIsDark(0.25))
        assertTrue(cohortHeatmapCellTextIsDark(0.5))
    }

    @Test
    fun textStaysLightBelowTheTwentyFivePercentThreshold() {
        assertEquals(false, cohortHeatmapCellTextIsDark(0.24))
        assertEquals(false, cohortHeatmapCellTextIsDark(0.0))
    }

    // ---- toggleCohortHeatmapSelection: max-2-row A/B selection ----

    @Test
    fun selectingAFirstRowAddsIt() {
        assertEquals(listOf("2026-09-21"), toggleCohortHeatmapSelection(emptyList(), "2026-09-21"))
    }

    @Test
    fun selectingASecondDifferentRowAddsItAsB() {
        assertEquals(listOf("A", "B"), toggleCohortHeatmapSelection(listOf("A"), "B"))
    }

    @Test
    fun selectingAnAlreadySelectedRowUnticksIt() {
        assertEquals(listOf("A"), toggleCohortHeatmapSelection(listOf("A", "B"), "B"))
        assertEquals(emptyList(), toggleCohortHeatmapSelection(listOf("A"), "A"))
    }

    @Test
    fun selectingAThirdRowDropsTheOldestSelectionKeepingTheNewestTwo() {
        assertEquals(listOf("B", "C"), toggleCohortHeatmapSelection(listOf("A", "B"), "C"))
    }

    // ---- selectedCohortHeatmapRows: Task 26's selection-id -> row lookup for the A-vs-B panel ----

    @Test
    fun selectedRowsAreResolvedInTickOrderNotRowOrder() {
        val rows = buildCohortHeatmapRows(
            listOf(
                RetentionCohort(cohortDate = "2026-08-01", size = 10, day1 = 0.1),
                RetentionCohort(cohortDate = "2026-09-20", size = 20, day1 = 0.2),
            ),
            CohortGrouping.ByDay,
            today,
        )
        // Rows come back newest-first ("2026-09-20", "2026-08-01"), but the user ticked the OLDER
        // row first (as A) — the resolved order must follow the tick order, not the row order.
        val selected = selectedCohortHeatmapRows(rows, listOf("2026-08-01", "2026-09-20"))
        assertEquals(listOf("2026-08-01", "2026-09-20"), selected.map { it.id })
    }

    @Test
    fun selectedRowsDropsAnIdWithNoMatchingRowRatherThanCrashing() {
        val rows = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-08-01", size = 10, day1 = 0.1)),
            CohortGrouping.ByDay,
            today,
        )
        assertEquals(listOf("2026-08-01"), selectedCohortHeatmapRows(rows, listOf("2026-08-01", "no-such-id")).map { it.id })
    }

    @Test
    fun selectedRowsIsEmptyWhenNothingIsSelected() {
        val rows = buildCohortHeatmapRows(
            listOf(RetentionCohort(cohortDate = "2026-08-01", size = 10, day1 = 0.1)),
            CohortGrouping.ByDay,
            today,
        )
        assertEquals(emptyList(), selectedCohortHeatmapRows(rows, emptyList()))
    }
}
