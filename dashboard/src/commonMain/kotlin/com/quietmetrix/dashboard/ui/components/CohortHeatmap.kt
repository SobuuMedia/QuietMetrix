package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.cohort_heatmap_checkbox_a
import com.quietmetrix.dashboard.resources.cohort_heatmap_checkbox_b
import com.quietmetrix.dashboard.resources.cohort_heatmap_checkbox_content_description_selected
import com.quietmetrix.dashboard.resources.cohort_heatmap_checkbox_content_description_unselected
import com.quietmetrix.dashboard.resources.cohort_heatmap_col_installed
import com.quietmetrix.dashboard.resources.cohort_heatmap_col_people
import com.quietmetrix.dashboard.resources.cohort_heatmap_desc_by_day
import com.quietmetrix.dashboard.resources.cohort_heatmap_desc_by_week
import com.quietmetrix.dashboard.resources.cohort_heatmap_placeholder_glyph
import com.quietmetrix.dashboard.resources.cohort_heatmap_title_by_day
import com.quietmetrix.dashboard.resources.cohort_heatmap_title_by_week
import com.quietmetrix.dashboard.resources.cohort_heatmap_toggle_by_day
import com.quietmetrix.dashboard.resources.cohort_heatmap_toggle_by_week
import com.quietmetrix.dashboard.resources.cohort_heatmap_tooltip_known
import com.quietmetrix.dashboard.resources.cohort_heatmap_tooltip_unavailable
import com.quietmetrix.dashboard.resources.cohort_heatmap_tooltip_unknown
import com.quietmetrix.dashboard.resources.cohort_reported_note
import com.quietmetrix.dashboard.resources.cohort_reported_toggle
import com.quietmetrix.dashboard.resources.col_day1
import com.quietmetrix.dashboard.resources.col_day14
import com.quietmetrix.dashboard.resources.col_day3
import com.quietmetrix.dashboard.resources.col_day30
import com.quietmetrix.dashboard.resources.col_day7
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.ui.screens.epochDayFromIsoOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

// ---------------------------------------------------------------------------
// Cohort heatmap (Task 25) — design_handoff_dashboard_redesign/README.md >
// Retention: "Cohort heatmap: title + By week/By day segmented toggle; each
// row has a checkbox-like A/B compare selector, the install date, cohort
// size, and 5 percentage cells shaded by intensity (brighter lime = higher
// retention); future cells show a dashed '·' placeholder; hovering a cell
// explains it in a plain-language tooltip." Pure (cohort x day) grid-building,
// intensity-mapping and selection logic lives in CohortHeatmapLogic.kt so it's
// unit-testable without a Compose host; this file is rendering only.
// ---------------------------------------------------------------------------

/**
 * The design handoff's cohort retention heatmap: a title + By-week/By-day segmented toggle, a
 * header row of the 5 fixed day-columns, and one row per [buildCohortHeatmapRows] result — a
 * checkbox-like A/B selector, the install date, cohort size, and 5 intensity-shaded cells.
 *
 * Every cell is resolved through [com.quietmetrix.dashboard.ui.screens.effectiveDayValue] (via
 * [buildCohortHeatmapRows], not reimplemented here) so a genuinely-too-young cell renders as the
 * dashed "·" placeholder while a confirmed, legitimate 0% renders as a real — if the lightest —
 * shaded cell; see that function's KDoc for the full too-young-vs-confirmed-zero disambiguation
 * this depends on, and [CohortHeatmapCell]'s KDoc for what a cell's `null` does/doesn't mean.
 *
 * Ticking two rows' A/B selectors is tracked here ([toggleCohortHeatmapSelection]) and reported via
 * [onSelectionChange] (ids, in tick order) and [onSelectedRowsChange] (the same selection, already
 * resolved to the actual [CohortHeatmapRow]s via [selectedCohortHeatmapRows] — Task 26's "⚖️
 * Comparing A vs B" panel renders those directly rather than re-deriving them from ids), but the
 * panel itself is Task 26's scope, not this component's — this component owns only the up-to-2-row
 * selection state/UI, not what's done with it.
 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun CohortHeatmap(
    cohorts: List<RetentionCohort>,
    modifier: Modifier = Modifier,
    onSelectionChange: (List<String>) -> Unit = {},
    onSelectedRowsChange: (List<CohortHeatmapRow>) -> Unit = {},
    onInspect: (String, String) -> Unit = { _, _ -> },
    previousCohorts: List<RetentionCohort> = emptyList(),
    compare: Boolean = false,
    periodDays: Int = 7,
) {
    if (cohorts.isEmpty()) return

    var grouping by remember { mutableStateOf(CohortGrouping.ByWeek) }
    var selected by remember { mutableStateOf(emptyList<String>()) }
    val todayEpochDay = remember { Clock.System.now().toEpochMilliseconds().floorDiv(86_400_000L) }
    val rows = remember(cohorts, grouping, todayEpochDay) { buildCohortHeatmapRows(cohorts, grouping, todayEpochDay) }
    val previousRows = remember(previousCohorts, grouping, todayEpochDay) { buildCohortHeatmapRows(previousCohorts, grouping, todayEpochDay) }

    DashboardCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        if (grouping == CohortGrouping.ByWeek) Res.string.cohort_heatmap_title_by_week
                        else Res.string.cohort_heatmap_title_by_day,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                CohortGroupingToggle(
                    grouping = grouping,
                    reportedWeeks = cohorts.any { it.cohortDate.contains("-W") },
                    onSelect = { newGrouping ->
                        grouping = newGrouping
                        selected = emptyList()
                        onSelectionChange(emptyList())
                        onSelectedRowsChange(emptyList())
                    },
                )
            }
            Text(
                text = stringResource(
                    if (grouping == CohortGrouping.ByWeek) Res.string.cohort_heatmap_desc_by_week
                    else Res.string.cohort_heatmap_desc_by_day,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (cohorts.any { it.cohortDate.contains("-W") }) Text(stringResource(Res.string.cohort_reported_note), style = MaterialTheme.typography.labelSmall)
            CohortHeatmapHeaderRow()
            rows.forEach { row ->
                CohortHeatmapDataRow(
                    row = row,
                    selectionSlot = selected.indexOf(row.id).takeIf { it >= 0 },
                    onInspect = { onInspect(if (grouping == CohortGrouping.ByWeek) "cohort-week" else "cohort", row.id) },
                    compare = compare,
                    previous = previousRows.firstOrNull { prior ->
                        val currentDay = epochDayFromIsoOrNull(row.label)
                        currentDay != null && epochDayFromIsoOrNull(prior.label) == currentDay - periodDays
                    },
                    onToggleSelect = {
                        selected = toggleCohortHeatmapSelection(selected, row.id)
                        onSelectionChange(selected)
                        onSelectedRowsChange(selectedCohortHeatmapRows(rows, selected))
                    },
                )
            }
        }
    }
}

/** Width reserved for the leading A/B checkbox column, shared by [CohortHeatmapHeaderRow] (an
 *  empty spacer) and [CohortSelectorCheckbox] so the header's text columns line up with the rows. */
private val CHECKBOX_COLUMN_WIDTH = 32.dp

@Composable
private fun CohortHeatmapHeaderRow() {
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(CHECKBOX_COLUMN_WIDTH))
        Text(stringResource(Res.string.cohort_heatmap_col_installed), style = labelStyle, color = labelColor, modifier = Modifier.weight(1.7f))
        Text(stringResource(Res.string.cohort_heatmap_col_people), style = labelStyle, color = labelColor, modifier = Modifier.weight(0.9f))
        COHORT_HEATMAP_DAY_NUMBERS.forEach { dayNumber ->
            Text(
                dayColumnLabel(dayNumber),
                style = labelStyle,
                color = labelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The existing per-day-column headers ([com.quietmetrix.dashboard.ui.screens.RetentionScreen]'s
 *  own cohort table already uses these exact same 5 strings) — reused rather than minted twice. */
@Composable
private fun dayColumnLabel(dayNumber: Int): String = when (dayNumber) {
    1 -> stringResource(Res.string.col_day1)
    3 -> stringResource(Res.string.col_day3)
    7 -> stringResource(Res.string.col_day7)
    14 -> stringResource(Res.string.col_day14)
    30 -> stringResource(Res.string.col_day30)
    else -> ""
}

@Composable
private fun CohortHeatmapDataRow(row: CohortHeatmapRow, selectionSlot: Int?, onToggleSelect: () -> Unit,
    onInspect: () -> Unit = {}, compare: Boolean = false, previous: CohortHeatmapRow? = null) {
    Row(modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onInspect).handCursor(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        CohortSelectorCheckbox(selectionSlot = selectionSlot, cohortLabel = row.label, onClick = onToggleSelect)
        Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.7f))
        Text(
            formatCount(row.size.toLong()),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.9f),
        )
        row.cells.forEachIndexed { index, cell ->
            Column(Modifier.weight(1f)) {
                CohortHeatmapCellView(cell = cell, row = row, dayNumber = COHORT_HEATMAP_DAY_NUMBERS[index],
                    dayLabel = dayColumnLabel(COHORT_HEATMAP_DAY_NUMBERS[index]), modifier = Modifier.fillMaxWidth())
                if (compare) ComparisonNote(previous?.cells?.getOrNull(index)?.fraction?.let(::formatPercent))
            }
        }
    }
}

/**
 * The "checkbox-like A/B compare selector" (design handoff): a small square that's an empty dashed
 * outline when unselected, a filled lime square labeled "A" when it's the first tick, lavender
 * labeled "B" when it's the second — mirroring the prototype's own `si === 0 ? lime : si === 1 ?
 * lavender : transparent` + `'A'`/`'B'`/`''` rule verbatim (`QuietMetrix Prototype.dc.html:271`).
 */
@Composable
private fun CohortSelectorCheckbox(selectionSlot: Int?, cohortLabel: String, onClick: () -> Unit) {
    val extended = LocalExtendedColors.current
    val shape = RoundedCornerShape(9.dp)
    val fill = when (selectionSlot) {
        0 -> extended.lime
        1 -> extended.lavender
        else -> null
    }
    val letter = when (selectionSlot) {
        0 -> stringResource(Res.string.cohort_heatmap_checkbox_a)
        1 -> stringResource(Res.string.cohort_heatmap_checkbox_b)
        else -> null
    }
    val checkboxDescription = if (selectionSlot == null) {
        stringResource(Res.string.cohort_heatmap_checkbox_content_description_unselected, cohortLabel)
    } else {
        val slotLabel = if (selectionSlot == 0) stringResource(Res.string.cohort_heatmap_checkbox_a) else stringResource(Res.string.cohort_heatmap_checkbox_b)
        stringResource(Res.string.cohort_heatmap_checkbox_content_description_selected, cohortLabel, slotLabel)
    }

    Box(
        modifier = Modifier
            .size(CHECKBOX_COLUMN_WIDTH)
            .let { if (fill != null) it.background(fill, shape) else it.dashedOutline(extended.hairline, cornerRadius = 9.dp) }
            .handCursor()
            .clickable(onClick = onClick)
            .semantics { contentDescription = checkboxDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (letter != null) Text(letter, style = MaterialTheme.typography.labelLarge, color = extended.onLime)
    }
}

/**
 * One (row x day) cell: a dashed "·" placeholder when [CohortHeatmapCell.fraction] is null (too
 * young — see that class's KDoc), otherwise a real shaded cell whose alpha/text-contrast come
 * straight from [cohortHeatmapCellAlpha]/[cohortHeatmapCellTextIsDark] (never recomputed ad hoc
 * here). Hovering either state reveals a plain-language explanation via [Tooltip]'s own
 * [tooltipPlacement]/[TooltipPositionProvider] placement machinery, reused rather than
 * re-implemented — only the trigger (this cell, not an ⓘ icon) differs.
 */
@Composable
private fun CohortHeatmapCellView(cell: CohortHeatmapCell, row: CohortHeatmapRow, dayNumber: Int, dayLabel: String, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val fraction = cell.fraction
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val explanation = if (fraction == null) {
        if (cell.state == CohortHeatmapCellState.Future) stringResource(Res.string.cohort_heatmap_tooltip_unknown, dayLabel)
        else stringResource(Res.string.cohort_heatmap_tooltip_unavailable)
    } else {
        val returning = (row.size * fraction).roundToLong()
        stringResource(
            Res.string.cohort_heatmap_tooltip_known,
            formatCount(row.size.toLong()),
            row.label,
            formatCount(returning),
            formatPercent(fraction),
            dayLabel,
        )
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .let {
                if (fraction == null) it.dashedOutline(extended.hairline, cornerRadius = 10.dp)
                else it.background(extended.lime.copy(alpha = cohortHeatmapCellAlpha(fraction)), shape)
            }
            .hoverable(interactionSource)
            .semantics { contentDescription = explanation },
        contentAlignment = Alignment.Center,
    ) {
        if (fraction == null) {
            Text(
                stringResource(Res.string.cohort_heatmap_placeholder_glyph),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                formatPercent(fraction),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (cohortHeatmapCellTextIsDark(fraction)) extended.onLime else MaterialTheme.colorScheme.onBackground,
            )
        }
        if (hovered) {
            Popup(popupPositionProvider = TooltipPositionProvider(gap = 8)) {
                Surface(
                    shape = QmShapeTokens.chip,
                    color = extended.chipFill,
                    modifier = Modifier.widthIn(max = 240.dp),
                ) {
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** The "By week" / "By day" segmented toggle, reusing [Pill] the same way [OverviewScreen]'s own
 *  metric segmented control already does (Lavender = selected, Neutral = not). */
@Composable
private fun CohortGroupingToggle(grouping: CohortGrouping, reportedWeeks: Boolean = false, onSelect: (CohortGrouping) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(
            text = stringResource(Res.string.cohort_heatmap_toggle_by_week),
            variant = if (grouping == CohortGrouping.ByWeek) PillVariant.Lavender else PillVariant.Neutral,
            onClick = { onSelect(CohortGrouping.ByWeek) },
        )
        Pill(
            text = stringResource(if (reportedWeeks) Res.string.cohort_reported_toggle else Res.string.cohort_heatmap_toggle_by_day),
            variant = if (grouping == CohortGrouping.ByDay) PillVariant.Lavender else PillVariant.Neutral,
            onClick = { onSelect(CohortGrouping.ByDay) },
        )
    }
}

/**
 * Draws a dashed rounded-rect outline — the design handoff's literal "2px dashed" treatment for an
 * unselected A/B checkbox and a "too early" heatmap cell (`QuietMetrix Prototype.dc.html`'s
 * `border: '2px dashed var(--ln)'`). Compose's built-in `Modifier.border` only ever draws a solid
 * stroke, so this is a small [drawBehind] + [PathEffect.dashPathEffect] helper rather than pulling
 * in a 3rd-party dependency for one outline style.
 */
private fun Modifier.dashedOutline(color: Color, cornerRadius: Dp, strokeWidth: Dp = 2.dp): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke.width / 2f, stroke.width / 2f),
        size = Size((size.width - stroke.width).coerceAtLeast(0f), (size.height - stroke.width).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = stroke,
    )
}
