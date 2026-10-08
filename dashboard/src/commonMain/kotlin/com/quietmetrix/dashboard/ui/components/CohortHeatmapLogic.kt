package com.quietmetrix.dashboard.ui.components

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.ui.screens.effectiveDayValue
import com.quietmetrix.dashboard.ui.screens.epochDayFromIsoOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The 5 cohort-age columns the heatmap always shows, regardless of [CohortGrouping] — [RetentionCohort]'s
 * own fixed field set (`day1`/`day3`/`day7`/`day14`/`day30`; see [rawDayValue]). The design handoff's HTML
 * prototype shows a DIFFERENT column set per grouping (`Day 1/2/3/5/7` for "By day" vs. `Day 1/3/7/14/30`
 * for "By week"), but that is demo-only fiction from its own synthetic `dayData`/`weekData` arrays — the
 * real [RetentionCohort] the server actually returns only ever has these 5 fixed fields, period, so both
 * groupings render the SAME 5 columns here. Documented judgment call, not a spec requirement either way.
 */
internal val COHORT_HEATMAP_DAY_NUMBERS: List<Int> = listOf(1, 3, 7, 14, 30)

/** Milliseconds in a day — mirrors [com.quietmetrix.dashboard.ui.screens.computeRetentionVitals]'s own
 *  private constant of the same value (kept file-local rather than exposed across files, matching this
 *  codebase's standing per-file-duplication convention for small shared constants). */
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The cohort heatmap's "By week" / "By day" segmented toggle (design handoff: "title + By week/By day
 * segmented toggle"; prototype's `retGroup: 'week' | 'day'` state field). [ByDay] shows one row per
 * [RetentionCohort] exactly as the server returns it; [ByWeek] aggregates those same cohorts into
 * 7-day buckets (see [buildCohortHeatmapRows]'s KDoc for the aggregation rule).
 */
internal enum class CohortGrouping { ByDay, ByWeek }

/** The meaning of a cell whose server value is absent or present. */
internal enum class CohortHeatmapCellState { Available, Future, Unavailable }

/** A cell's fraction and whether a missing value is future data or unavailable server data. */
internal data class CohortHeatmapCell(
    val fraction: Double?,
    val state: CohortHeatmapCellState = if (fraction == null) CohortHeatmapCellState.Unavailable else CohortHeatmapCellState.Available,
)

/**
 * One heatmap row: a stable [id] (used for [toggleCohortHeatmapSelection]'s A/B bookkeeping), the
 * install-date [label] shown in the "Installed" column, the row's cohort [size], and its
 * [COHORT_HEATMAP_DAY_NUMBERS]-ordered [cells].
 */
internal data class CohortHeatmapRow(
    val id: String,
    val label: String,
    val size: Int,
    val sizeState: String = "reported",
    val cells: List<CohortHeatmapCell>,
)

/** [RetentionCohort]'s raw (possibly ambiguous — see [effectiveDayValue]) field for [dayNumber], or
 *  null for any [dayNumber] outside [COHORT_HEATMAP_DAY_NUMBERS]. */
private fun rawDayValue(cohort: RetentionCohort, dayNumber: Int): Double? = when (dayNumber) {
    1 -> cohort.day1
    3 -> cohort.day3
    7 -> cohort.day7
    14 -> cohort.day14
    30 -> cohort.day30
    else -> null
}

/**
 * Builds the heatmap's rows from the raw [cohorts] the server returned, grouped per [grouping], each
 * cell resolved from the raw server value using [effectiveDayValue], at [todayEpochDay] (days since
 * the Unix epoch, UTC;
 * defaults to the real current day, overridable for deterministic tests).
 *
 * [CohortGrouping.ByDay] passes each cohort through untouched (one row per server-side cohort,
 * newest first). [CohortGrouping.ByWeek] buckets each actual cohort date into its ISO calendar week,
 * Monday through Sunday; historical `YYYY-WNN` cohorts stay in their original pre-aggregated buckets.
 * A week row's [CohortHeatmapRow.size] is the sum of its member cohorts' sizes; a week
 * row's per-day cell is [effectiveDayValue]-weighted-averaged across member cohorts — see
 * [weightedWeekCell]'s KDoc for exactly how. Cohorts whose `cohortDate` fails to parse
 * ([epochDayFromIsoOrNull] returns null) are dropped from [ByWeek] grouping entirely (there is no
 * sane week bucket for them); [ByDay] still shows them (their own row doesn't need a week bucket).
 */
@OptIn(ExperimentalTime::class)
internal fun buildCohortHeatmapRows(
    cohorts: List<RetentionCohort>,
    grouping: CohortGrouping,
    todayEpochDay: Long = Clock.System.now().toEpochMilliseconds().floorDiv(MILLIS_PER_DAY),
): List<CohortHeatmapRow> = when (grouping) {
    CohortGrouping.ByDay -> cohorts
        .map { cohort ->
            CohortHeatmapRow(
                id = cohort.cohortDate,
                label = cohort.cohortDate,
                size = cohort.size,
                sizeState = cohort.sizeState,
                cells = COHORT_HEATMAP_DAY_NUMBERS.map { dayNumber ->
                    resolvedCell(cohort, dayNumber, todayEpochDay)
                },
            )
        }
        .sortedByDescending { it.id }
    CohortGrouping.ByWeek -> buildByWeekRows(cohorts, todayEpochDay)
}

/** [buildCohortHeatmapRows]'s [CohortGrouping.ByWeek] branch, grouped by ISO Monday-start week. */
private fun buildByWeekRows(cohorts: List<RetentionCohort>, todayEpochDay: Long): List<CohortHeatmapRow> {
    data class WeekKey(val sortDay: Long, val id: String, val label: String)
    val byWeekBucket = cohorts
        .mapNotNull { cohort ->
            val legacyWeek = Regex("\\d{4}-W\\d{2}").matches(cohort.cohortDate)
            val day = epochDayFromIsoOrNull(cohort.cohortDate) ?: return@mapNotNull null
            val key = if (legacyWeek) {
                WeekKey(day, "legacy-${cohort.cohortDate}", cohort.cohortDate)
            } else {
                val offset = ((day + 3L) % 7L + 7L) % 7L
                val monday = day - offset
                WeekKey(monday, "week-$monday", isoDateFromEpochDay(monday))
            }
            key to cohort
        }
        .groupBy({ it.first }, { it.second })
    return byWeekBucket.entries
        .sortedByDescending { it.key.sortDay }
        .map { (week, members) ->
            CohortHeatmapRow(
                id = week.id,
                label = week.label,
                size = members.sumOf { it.size },
                sizeState = if (members.any { it.sizeState == "suppressed" }) "suppressed" else "reported",
                cells = COHORT_HEATMAP_DAY_NUMBERS.map { dayNumber -> weightedWeekCell(members, dayNumber, todayEpochDay) },
            )
        }
}

/** Inverse of [epochDayFromIsoOrNull], used to label a calendar-week row by its Monday. */
private fun isoDateFromEpochDay(epochDay: Long): String {
    val z = epochDay + 719468L
    val era = (if (z >= 0L) z else z - 146096L).floorDiv(146097L)
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    var year = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val day = doy - (153L * mp + 2L) / 5L + 1L
    val month = mp + if (mp < 10L) 3L else -9L
    if (month <= 2L) year += 1L
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

/**
 * A "By week" cell is size-weighted across known mature members. If a mature member is unavailable,
 * the aggregate remains unavailable rather than presenting a biased partial average. Future members
 * are excluded until they mature; if none contribute yet, the cell is marked Future.
 */
private fun weightedWeekCell(members: List<RetentionCohort>, dayNumber: Int, todayEpochDay: Long): CohortHeatmapCell {
    val unavailableMatureMember = members.any { cohort ->
        cohort.sizeState == "suppressed" || (rawDayValue(cohort, dayNumber) == null && cohort.size > 0 && !isFuture(cohort, dayNumber, todayEpochDay))
    }
    if (unavailableMatureMember) return CohortHeatmapCell(null, CohortHeatmapCellState.Unavailable)
    val contributions = members.mapNotNull { cohort ->
        effectiveDayValue(cohort, dayNumber, rawDayValue(cohort, dayNumber), todayEpochDay)?.let { value -> cohort.size to value }
    }
    val totalWeight = contributions.sumOf { it.first }
    if (totalWeight <= 0) return CohortHeatmapCell(null, CohortHeatmapCellState.Future)
    val weightedSum = contributions.sumOf { it.first * it.second }
    return CohortHeatmapCell(weightedSum / totalWeight)
}

private fun resolvedCell(cohort: RetentionCohort, dayNumber: Int, todayEpochDay: Long): CohortHeatmapCell {
    val value = effectiveDayValue(cohort, dayNumber, rawDayValue(cohort, dayNumber), todayEpochDay)
    if (value != null) return CohortHeatmapCell(value, CohortHeatmapCellState.Available)
    return CohortHeatmapCell(null, if (cohort.size > 0 && isFuture(cohort, dayNumber, todayEpochDay)) CohortHeatmapCellState.Future else CohortHeatmapCellState.Unavailable)
}

private fun isFuture(cohort: RetentionCohort, dayNumber: Int, todayEpochDay: Long): Boolean {
    val start = epochDayFromIsoOrNull(cohort.cohortDate) ?: return false
    return todayEpochDay - start < dayNumber
}

/**
 * Cell background alpha (`0.12f..1.0f`) for a resolved, non-null [fraction] (0..1) — "brighter lime =
 * higher retention" (design handoff). Mirrors the design prototype's own cell-shading formula verbatim
 * (`QuietMetrix Prototype.dc.html`'s `cohorts` mapper: `'rgba(200,242,90,' + (0.12 + v/50*0.88) + ')'`,
 * where `v` is a 0..100 percentage — here `v == fraction * 100`). The `0.12` floor means a legitimate,
 * confirmed `0.0` fraction (see [CohortHeatmapCell]'s KDoc) still renders as a visibly-shaded, lightest
 * cell — never indistinguishable from the dashed "too early" placeholder, which has no fill at all.
 * The ceiling of `1.0f` is reached at `fraction == 0.5` (50% retention) and clamped beyond that: this
 * metric only rarely exceeds 50%, so "cap the ramp at 50%, call anything higher equally brightest"
 * reads as "very high" without needing a hypothetical scale past what real data plausibly produces.
 */
internal fun cohortHeatmapCellAlpha(fraction: Double): Float {
    val percent = fraction.coerceIn(0.0, 1.0) * 100.0
    return (0.12 + (percent / 50.0) * 0.88).toFloat().coerceIn(0.12f, 1.0f)
}

/**
 * Whether a resolved cell's percentage text should use the dark-on-lime [com.quietmetrix.dashboard.theme.ExtendedColors.onLime]
 * color instead of the normal body-text color, once its [cohortHeatmapCellAlpha] shading is bright
 * enough that light text would lose contrast. Mirrors the prototype's own threshold verbatim
 * (`color: v >= 25 ? '#16131a' : 'var(--ink)'`, `v` a 0..100 percentage — here `fraction >= 0.25`).
 */
internal fun cohortHeatmapCellTextIsDark(fraction: Double): Boolean = fraction >= 0.25

/**
 * Applies the design handoff's "checkbox-like A/B compare selector" tick rule to the cohort heatmap's
 * row-selection state (design handoff: "Ticking two rows shows a '⚖️ Comparing A vs B' panel below";
 * prototype's `cmp` state field): ticking an already-selected [id] untick it; ticking a new [id] when
 * fewer than 2 rows are selected simply adds it; ticking a THIRD [id] replaces the OLDEST selection
 * (A) rather than refusing the click or silently doing nothing — same `.slice(-2)` "keep only the last
 * 2" semantics the prototype's own `toggleCmp` uses, so the newest two ticks always win. The actual
 * "⚖️ Comparing A vs B" panel this selection state feeds is Task 26's scope, not this component's —
 * this only owns the list of up to 2 selected row ids.
 */
internal fun toggleCohortHeatmapSelection(current: List<String>, id: String): List<String> =
    if (id in current) current - id else (current + id).takeLast(2)

/**
 * Resolves [selectedIds] (Task 25's up-to-2-id A/B selection, in tick order — A first, B second)
 * into the actual [CohortHeatmapRow] objects from [rows], in that same order, so Task 26's
 * "⚖️ Comparing A vs B" panel (`ui/screens/RetentionLogic.kt`'s `computeRetentionComparisonTakeaway`)
 * can render the 2 selected rows' real cell data without re-deriving the selection itself. An id
 * with no matching row (e.g. a selection carried over from a grouping that's since changed) is
 * silently dropped rather than crashing — [CohortHeatmap] already resets selection on a grouping
 * change, so this is a defensive fallback, not an expected path.
 */
internal fun selectedCohortHeatmapRows(rows: List<CohortHeatmapRow>, selectedIds: List<String>): List<CohortHeatmapRow> =
    selectedIds.mapNotNull { id -> rows.find { it.id == id } }
