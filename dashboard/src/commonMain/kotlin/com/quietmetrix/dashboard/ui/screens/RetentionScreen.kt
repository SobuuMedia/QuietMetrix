package com.quietmetrix.dashboard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.quietmetrix.dashboard.viewmodel.isLoading
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.ui.components.SectionLoading
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.format.formatWilsonRange
import com.quietmetrix.dashboard.format.wilsonInterval
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.*
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.cohort_heatmap_checkbox_a
import com.quietmetrix.dashboard.resources.cohort_heatmap_checkbox_b
import com.quietmetrix.dashboard.resources.col_activation
import com.quietmetrix.dashboard.resources.col_cohort
import com.quietmetrix.dashboard.resources.col_cohort_size
import com.quietmetrix.dashboard.resources.col_day1
import com.quietmetrix.dashboard.resources.col_day14
import com.quietmetrix.dashboard.resources.col_day3
import com.quietmetrix.dashboard.resources.col_day30
import com.quietmetrix.dashboard.resources.col_day7
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_retention
import com.quietmetrix.dashboard.resources.nav_retention
import com.quietmetrix.dashboard.resources.retention_card_caption_day1
import com.quietmetrix.dashboard.resources.retention_card_caption_day30
import com.quietmetrix.dashboard.resources.retention_card_caption_day7
import com.quietmetrix.dashboard.resources.retention_card_day1_label
import com.quietmetrix.dashboard.resources.retention_card_day30_label
import com.quietmetrix.dashboard.resources.retention_card_day7_label
import com.quietmetrix.dashboard.resources.retention_card_too_early
import com.quietmetrix.dashboard.resources.retention_comparison_chip_a
import com.quietmetrix.dashboard.resources.retention_comparison_chip_b
import com.quietmetrix.dashboard.resources.retention_comparison_column_unknown
import com.quietmetrix.dashboard.resources.retention_comparison_column_values
import com.quietmetrix.dashboard.resources.retention_comparison_eyebrow_emoji
import com.quietmetrix.dashboard.resources.retention_comparison_heading
import com.quietmetrix.dashboard.resources.retention_comparison_higher
import com.quietmetrix.dashboard.resources.retention_comparison_lower
import com.quietmetrix.dashboard.resources.retention_comparison_not_enough_data
import com.quietmetrix.dashboard.resources.retention_comparison_same
import com.quietmetrix.dashboard.resources.retention_comparison_vs
import com.quietmetrix.dashboard.resources.retention_dot_empty_content_description
import com.quietmetrix.dashboard.resources.retention_dot_filled_content_description
import com.quietmetrix.dashboard.resources.retention_dot_filled_emoji
import com.quietmetrix.dashboard.resources.retention_eyebrow_emoji
import com.quietmetrix.dashboard.resources.retention_h1_day1_highlight
import com.quietmetrix.dashboard.resources.retention_h1_day1_only_tail
import com.quietmetrix.dashboard.resources.retention_h1_day7_highlight
import com.quietmetrix.dashboard.resources.retention_h1_day7_only_lead
import com.quietmetrix.dashboard.resources.retention_h1_lead
import com.quietmetrix.dashboard.resources.retention_h1_mid
import com.quietmetrix.dashboard.resources.retention_h1_not_enough_data
import com.quietmetrix.dashboard.resources.retention_h1_tail
import com.quietmetrix.dashboard.resources.retention_insight_ask_why
import com.quietmetrix.dashboard.resources.retention_insight_eyebrow_emoji
import com.quietmetrix.dashboard.resources.retention_insight_not_enough_data
import com.quietmetrix.dashboard.resources.retention_insight_scope_note
import com.quietmetrix.dashboard.resources.state_no_retention
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.CohortGrouping
import com.quietmetrix.dashboard.ui.components.CohortHeatmap
import com.quietmetrix.dashboard.ui.components.CohortHeatmapRow
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.charts.BarChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.BarData
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.table.Column
import com.quietmetrix.dashboard.ui.components.table.DataTable
import com.quietmetrix.dashboard.ui.components.table.SortColumn
import com.quietmetrix.dashboard.ui.components.ComparisonNote
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.nav.NavDestination as AppTab

// ---------------------------------------------------------------------------
// Retention screen (Task 24: extracted from DashboardScreen.kt's old inline
// RetentionTab, plus a new H1 headline + 3 vital cards with a 10-dot grid —
// see RetentionLogic.kt for the pure day1/day7/day30 selection and dot-count
// math this screen renders. Task 25 added the CohortHeatmap below the vital
// cards — see ui/components/CohortHeatmapLogic.kt for its pure grid-building
// logic).
// ---------------------------------------------------------------------------

/**
 * The Retention screen: header (project picker + info), the plain-language D1/D7 headline, the
 * 3 vital cards (Next day / After a week / After a month), the [CohortHeatmap] (Task 25), and the
 * existing per-cohort data table (kept alongside the heatmap for power users/Expert-style detail,
 * per the design handoff's own "a real cohort table for power users" framing — the heatmap is a
 * friendlier summary view, not a replacement for this table's full precision/Wilson-interval detail).
 */
@Composable
internal fun RetentionScreen(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
                IconButton(
                    onClick = { viewModel.showTabInfo(AppTab.Retention) },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = stringResource(Res.string.tab_info_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            state.sectionErrors[DataSection.Retention]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }

            if (state.retention?.cohorts?.isEmpty() == true && !state.isLoading(DataSection.Retention) && state.sectionErrors[DataSection.Retention] == null) SetupInstructionsSection()
            val retentionQuestion = stringResource(Res.string.ask_retention_why_question)
    val cohorts = state.retention?.cohorts ?: emptyList()
            if (state.isLoading(DataSection.Retention) && cohorts.isEmpty()) {
                SectionLoading()
            } else if (cohorts.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(Res.drawable.ic_retention, Res.string.state_no_retention, Modifier.padding(16.dp))
                }
            } else {
                val vitals = computeRetentionVitals(state.retention)
                var comparisonRows by remember { mutableStateOf(emptyList<CohortHeatmapRow>()) }
                RetentionHero(vitals)
                RetentionVitalCardsRow(vitals, sizeClass, onDrillDown = { kind, key -> viewModel.openDrillDown(kind, key) },
                    compare = state.compare, previous = computeRetentionVitals(state.comparison.retention))
                CohortHeatmap(cohorts, onSelectedRowsChange = { comparisonRows = it }, onInspect = viewModel::openDrillDown,
                    compare = state.compare, previousCohorts = state.comparison.retention?.cohorts.orEmpty(),
                    periodDays = ((state.range.seconds + 86399) / 86400).toInt())
                if (comparisonRows.size == 2) {
                RetentionComparisonPanel(comparisonRows[0], comparisonRows[1])
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DataTable(
                            columns = listOf(
                                Column<RetentionCohort>(
                                    title = stringResource(Res.string.col_cohort),
                                    weight = 1f,
                                    cell = { c -> Text(c.cohortDate, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { viewModel.openDrillDown("cohort", c.cohortDate) }.handCursor()) },
                                    sortSelector = { it.cohortDate },
                                ),
                                Column<RetentionCohort>(
                                    title = stringResource(Res.string.col_cohort_size),
                                    weight = 0.6f,
                                    cell = { c -> Text(formatCount(c.size.toLong()), style = MaterialTheme.typography.bodyMedium) },
                                    sortSelector = { it.size },
                                ),
                                retentionDayColumn(stringResource(Res.string.col_day1)) { it.day1 },
                                retentionDayColumn(stringResource(Res.string.col_day3)) { it.day3 },
                                retentionDayColumn(stringResource(Res.string.col_day7)) { it.day7 },
                                retentionDayColumn(stringResource(Res.string.col_day14)) { it.day14 },
                                retentionDayColumn(stringResource(Res.string.col_day30)) { it.day30 },
                                retentionDayColumn(stringResource(Res.string.col_activation)) { it.activationRate },
                            ),
                            rows = cohorts,
                            sizeClass = sizeClass,
                            initialSort = SortColumn(0, ascending = false),
                            rowKey = { it.cohortDate },
                            modifier = Modifier.heightIn(max = 500.dp).fillMaxWidth(),
                        )
                    }
                }
                RetentionClosingInsightCard(cohorts, onAskWhy = { viewModel.openAskDrawer(retentionQuestion) })
            }
        }
    }
}

/**
 * One day-N retention column: the observed percentage, with its Wilson score interval shown
 * as a smaller range underneath — the honest-error-bars default every rate in this dashboard
 * is shown with (see [wilsonInterval]'s doc comment). Null (not yet observable at this cohort's
 * age) renders as [value_none]; a zero-size cohort has no interval to show either.
 */
@Composable
private fun retentionDayColumn(title: String, dayValue: (RetentionCohort) -> Double?): Column<RetentionCohort> {
    val none = stringResource(Res.string.value_none)
    return Column(
        title = title,
        weight = 0.9f,
        cell = { c ->
            val fraction = dayValue(c)
            if (fraction == null || c.size <= 0) {
                Text(none, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val successes = kotlin.math.round(fraction * c.size).toLong()
                val interval = wilsonInterval(successes, c.size.toLong())
                Column {
                    Text(formatPercent(fraction), style = MaterialTheme.typography.bodyMedium)
                    if (interval != null) {
                        Text(
                            formatWilsonRange(interval),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        sortSelector = { dayValue(it) },
    )
}

// ---------------------------------------------------------------------------
// Headline (H1) — Task 24
// ---------------------------------------------------------------------------

/**
 * The "🔁 Retention" eyebrow + the plain-language D1/D7 headline sentence, with the two live
 * numbers lime-highlighted (design handoff: "H1 states the D1/D7 headline in plain words with a
 * lime highlight"; `QuietMetrix Prototype.dc.html:243`'s literal markup: "Out of 10 new people,
 * **4 come back** the next day and 2 are still around a week later."). Both numbers are the same
 * out-of-10 dot count [RetentionVitalCardsRow]'s cards render below — reusing [dotCountForFraction]
 * rather than a second, independent rounding keeps the headline and the cards from ever disagreeing
 * about what "4 come back" means.
 *
 * Falls back honestly ([retentionHeadlineKind]) when Day-1 and/or Day-7 haven't been observed by
 * any cohort yet, rather than fabricating a number — see [RetentionVitals]'s KDoc for why that's a
 * genuinely possible state (a very young project) rather than a product-config gap.
 */
@Composable
internal fun RetentionHero(vitals: RetentionVitals, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val lime = SpanStyle(background = extended.lime, color = extended.onLime)

    val lead = stringResource(Res.string.retention_h1_lead)
    val mid = stringResource(Res.string.retention_h1_mid)
    val tail = stringResource(Res.string.retention_h1_tail)
    val day1OnlyTail = stringResource(Res.string.retention_h1_day1_only_tail)
    val day7OnlyLead = stringResource(Res.string.retention_h1_day7_only_lead)
    val notEnoughData = stringResource(Res.string.retention_h1_not_enough_data)
    val day1Highlight = vitals.day1?.let { stringResource(Res.string.retention_h1_day1_highlight, dotCountForFraction(it)) }
    val day7Highlight = vitals.day7?.let { stringResource(Res.string.retention_h1_day7_highlight, dotCountForFraction(it)) }

    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.retention_eyebrow_emoji) + " " + stringResource(Res.string.nav_retention),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val sentence = buildAnnotatedString {
            when (retentionHeadlineKind(vitals.day1, vitals.day7)) {
                RetentionHeadlineKind.Both -> {
                    append(lead)
                    withStyle(lime) { append(day1Highlight!!) }
                    append(" ")
                    append(mid)
                    withStyle(lime) { append(day7Highlight!!) }
                    append(tail)
                }
                RetentionHeadlineKind.Day1Only -> {
                    append(lead)
                    withStyle(lime) { append(day1Highlight!!) }
                    append(day1OnlyTail)
                }
                RetentionHeadlineKind.Day7Only -> {
                    append(day7OnlyLead)
                    withStyle(lime) { append(day7Highlight!!) }
                    append(tail)
                }
                RetentionHeadlineKind.NotEnoughData -> append(notEnoughData)
            }
        }
        Text(sentence, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ---------------------------------------------------------------------------
// 3 vital cards — Task 24
// ---------------------------------------------------------------------------

/**
 * The "Next day / After a week / After a month" 3-card row (design handoff: "3 cards ... a 10-dot
 * 'people' grid (🙂 filled vs dashed empty), big %, one-line caption. Clickable → drill drawer"),
 * one card per [RetentionVitals] field.
 */
@Composable
internal fun RetentionVitalCardsRow(vitals: RetentionVitals, sizeClass: WindowSizeClass, onDrillDown: (String, String) -> Unit = { _, _ -> }, modifier: Modifier = Modifier, compare: Boolean = false, previous: RetentionVitals = RetentionVitals(null, null, null)) {
    ResponsiveRowOrColumn(sizeClass, modifier = modifier) {
        RetentionVitalCard(
            label = stringResource(Res.string.retention_card_day1_label),
            fraction = vitals.day1,
            compare = compare,
            previous = previous.day1,
            captionRes = Res.string.retention_card_caption_day1,
            onClick = { onDrillDown("retention", "day1") },
            modifier = Modifier.fillSlot(),
        )
        RetentionVitalCard(
            label = stringResource(Res.string.retention_card_day7_label),
            fraction = vitals.day7,
            compare = compare,
            previous = previous.day7,
            captionRes = Res.string.retention_card_caption_day7,
            onClick = { onDrillDown("retention", "day7") },
            modifier = Modifier.fillSlot(),
        )
        RetentionVitalCard(
            label = stringResource(Res.string.retention_card_day30_label),
            fraction = vitals.day30,
            compare = compare,
            previous = previous.day30,
            captionRes = Res.string.retention_card_caption_day30,
            onClick = { onDrillDown("retention", "day30") },
            modifier = Modifier.fillSlot(),
        )
    }
}

/**
 * Fraction [RetentionVitalCard]'s container color is blended towards white while hovered — same
 * value and rationale as [KpiCard]'s own `HOVER_BRIGHTEN_FRACTION`, duplicated here (rather than
 * exposed across files) so this file stays self-contained, matching this codebase's standing
 * per-file-duplication convention for small shared constants (see `OverviewVitalsLogic.kt`'s
 * `sessionMomentumFrom` KDoc for the same reasoning applied to logic instead of styling).
 */
private const val HOVER_BRIGHTEN_FRACTION = 0.12f

/** Scale applied to a [RetentionVitalCard] while pressed — same value as [KpiCard]'s `PRESS_SCALE`. */
private const val PRESS_SCALE = 0.97f

/** Elevation a [RetentionVitalCard] gains while hovered — same value as [KpiCard]'s `HOVER_ELEVATION`. */
private val HOVER_ELEVATION = 12.dp

/**
 * One vital card: label, big percentage, a 10-dot "people" grid, and a one-line caption — or, when
 * [fraction] is null, an honest "too early to tell" placeholder instead of a fabricated 0%
 * (see [RetentionVitals]'s KDoc: null here means no cohort has reached this age yet, not that the
 * metric is unconfigured).
 *
 * Clickable per the design handoff ("Clickable → drill drawer"), reusing [KpiCard]'s hover/press
 * idiom (brighten-on-hover, scale-on-press, Material3-owned elevation — see that function's KDoc
 * for the full rationale, including why elevation is left to `Card` itself rather than a second
 * manual animation) rather than inventing a new one. The click handler itself is a stub — same
 * "stub now, wire for real later" convention [OverviewHeroNormal]'s "Tell me more"/"Copy" actions
 * already established — since the real drill-down drawer is Task 41's scope, not this one's.
 */
@Composable
private fun RetentionVitalCard(
    label: String,
    fraction: Double?,
    captionRes: StringResource,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    compare: Boolean = false,
    previous: Double? = null,
) {
    val extended = LocalExtendedColors.current
    val shape = MaterialTheme.shapes.extraLarge
    val border = BorderStroke(1.dp, extended.hairline)
    val unavailable = stringResource(Res.string.retention_card_too_early)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, label = "retentionCardScale")
    val baseContainer = MaterialTheme.colorScheme.surface
    val containerColor by animateColorAsState(
        if (hovered) lerp(baseContainer, Color.White, HOVER_BRIGHTEN_FRACTION) else baseContainer,
        label = "retentionCardBrighten",
    )

    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale).handCursor(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, hoveredElevation = HOVER_ELEVATION, pressedElevation = 0.dp),
        border = border,
        interactionSource = interactionSource,
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compare) ComparisonNote(previous?.let(::formatPercent))
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (fraction != null) {
                Text(formatPercent(fraction), style = MaterialTheme.typography.displaySmall)
                val filled = dotCountForFraction(fraction)
                RetentionDotGrid(filled)
                Text(
                    stringResource(captionRes, filled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(Res.string.value_none), style = MaterialTheme.typography.displaySmall)
                Text(unavailable, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A row of [totalDots] "person" dots, [filledCount] of them lime-filled (🙂), the rest a dashed
 * outline — the design handoff's literal per-dot styling (`QuietMetrix Prototype.dc.html:1092`'s
 * `dots(n)` helper: filled = lime circle + 🙂, empty = transparent + dashed border). Each dot
 * carries its own accessible content description rather than relying on sighted-only color/face.
 */
@Composable
private fun RetentionDotGrid(filledCount: Int, totalDots: Int = 10, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val filledFace = stringResource(Res.string.retention_dot_filled_emoji)
    val filledCd = stringResource(Res.string.retention_dot_filled_content_description)
    val emptyCd = stringResource(Res.string.retention_dot_empty_content_description)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(totalDots) { i ->
            val isFilled = i < filledCount
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .let {
                        if (isFilled) it.background(extended.lime, CircleShape)
                        else it.border(2.dp, extended.hairline, CircleShape)
                    }
                    .semantics { contentDescription = if (isFilled) filledCd else emptyCd },
                contentAlignment = Alignment.Center,
            ) {
                if (isFilled) Text(filledFace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// "⚖️ Comparing A vs B" panel + closing insight card — Task 26
// ---------------------------------------------------------------------------

/**
 * The design handoff's "⚖️ Comparing A vs B" panel (README.md > Retention: "Ticking two rows
 * shows a '⚖️ Comparing A vs B' panel below with a one-line takeaway and a small paired bar chart
 * per day-column"), rendered by [RetentionScreen] only while [CohortHeatmap]'s selection holds
 * exactly 2 rows — this composable receives those 2 rows directly (lifted via
 * `onSelectedRowsChange`), it doesn't re-derive the selection itself. [rowA]/[rowB] are in tick
 * order (A first, B second), not chronological order — see [computeRetentionComparisonTakeaway]'s
 * KDoc.
 */
@Composable
internal fun RetentionComparisonPanel(rowA: CohortHeatmapRow, rowB: CohortHeatmapRow, modifier: Modifier = Modifier) {
    val takeaway = computeRetentionComparisonTakeaway(rowA, rowB)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.retention_comparison_eyebrow_emoji) + " " + stringResource(Res.string.retention_comparison_heading),
                    style = MaterialTheme.typography.titleLarge,
                )
                Pill(text = stringResource(Res.string.retention_comparison_chip_a, rowA.label), variant = PillVariant.Lime)
                Text(stringResource(Res.string.retention_comparison_vs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Pill(text = stringResource(Res.string.retention_comparison_chip_b, rowB.label), variant = PillVariant.Lavender)
            }
            Text(
                retentionComparisonSentence(takeaway, stringResource(Res.string.retention_comparison_not_enough_data)),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (takeaway is RetentionComparisonTakeaway.Comparison) {
                RetentionComparisonColumnsRow(rowA, rowB)
            }
        }
    }
}

/**
 * Renders a [RetentionComparisonTakeaway] as the plain one-line sentence [RetentionComparisonPanel]
 * and [RetentionClosingInsightCard] both show (same shape, different surrounding chrome — see
 * each composable's KDoc): the real comparison sentence when [takeaway] found a day-column both
 * sides had actually observed, or [notEnoughData] when it didn't — see [RetentionComparisonTakeaway]'s
 * KDoc for why that's a genuinely possible state, not a fallback for a bug. Day labels reuse the
 * existing `col_day1`/`col_day3`/.../`col_day30` strings ([RetentionScreen]'s own cohort table and
 * [CohortHeatmap]'s header row already use these exact 5) rather than minting a 3rd copy.
 */
@Composable
private fun retentionComparisonSentence(takeaway: RetentionComparisonTakeaway, notEnoughData: String): String = when (takeaway) {
    is RetentionComparisonTakeaway.NotEnoughData -> notEnoughData
    is RetentionComparisonTakeaway.Comparison -> {
        val dayLabel = comparisonDayLabel(takeaway.dayNumber)
        val delta = takeaway.deltaPoints
        when {
            delta == 0 -> stringResource(Res.string.retention_comparison_same, dayLabel, formatPercent(takeaway.aFraction))
            delta > 0 -> stringResource(Res.string.retention_comparison_higher, dayLabel, takeaway.bLabel, delta, takeaway.aLabel)
            else -> stringResource(Res.string.retention_comparison_lower, dayLabel, takeaway.bLabel, -delta, takeaway.aLabel)
        }
    }
}

/** The existing per-day-column header strings, looked up by raw day number — see
 *  [retentionComparisonSentence]'s KDoc for why these are reused rather than re-minted. */
@Composable
private fun comparisonDayLabel(dayNumber: Int): String = when (dayNumber) {
    1 -> stringResource(Res.string.col_day1)
    3 -> stringResource(Res.string.col_day3)
    7 -> stringResource(Res.string.col_day7)
    14 -> stringResource(Res.string.col_day14)
    30 -> stringResource(Res.string.col_day30)
    else -> ""
}

/**
 * One small [BarChartCanvas] pair per day-column (design handoff: "a small paired bar chart per
 * day-column") — A in lime, B in lavender, reusing [BarChartCanvas] rather than a new chart
 * primitive (per the Task 26 brief), fed by [computeRetentionComparisonDayColumns]. A day-column
 * where EITHER side is still too young ([RetentionComparisonDayColumn.bothKnown] false) renders an
 * honest "not yet" placeholder instead of a bar chart that would otherwise have nothing real to
 * plot for the missing side — see that class's KDoc for why a null is never treated as a
 * fabricated 0% here.
 */
@Composable
private fun RetentionComparisonColumnsRow(rowA: CohortHeatmapRow, rowB: CohortHeatmapRow, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val columns = computeRetentionComparisonDayColumns(rowA, rowB)
    val labelA = stringResource(Res.string.cohort_heatmap_checkbox_a)
    val labelB = stringResource(Res.string.cohort_heatmap_checkbox_b)

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        columns.forEach { column ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (column.bothKnown) {
                    BarChartCanvas(
                        bars = listOf(
                            BarData(labelA, (column.aFraction!! * 100).toFloat()),
                            BarData(labelB, (column.bFraction!! * 100).toFloat()),
                        ),
                        barColor = extended.lime,
                        highlightIndex = 1,
                        highlightColor = extended.lavender,
                        chartHeight = 100.dp,
                    )
                    Text(
                        stringResource(Res.string.retention_comparison_column_values, formatPercent(column.aFraction), formatPercent(column.bFraction)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    Box(modifier = Modifier.height(100.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.retention_comparison_column_unknown),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Text(
                    comparisonDayLabel(column.dayNumber),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The design handoff's closing insight card ("✨, lime-adjacent insight sentence, 'Ask why' CTA
 * into the AI drawer" — README.md > Retention).
 *
 * **Data-honesty investigation (Task 26)**: see [computeRetentionClosingInsightTakeaway]'s KDoc
 * for the full writeup — the design handoff's own prototype hardcodes a fabricated feature-level
 * causal claim here with no backing field anywhere in the API, so this renders the SAME real,
 * mechanical "did retention move cohort-over-cohort, and by how much" sentence
 * [RetentionComparisonPanel] already computes for a user-selected pair — just applied
 * automatically to the latest-vs-previous cohort — rather than any invented AI-sounding insight.
 *
 * The Ask action opens the global drawer with a scoped retention question.
 *
 * **Scope note (post-review fix)**: this card ALWAYS compares the 2 most recent individual
 * [RetentionCohort]s (see [computeRetentionClosingInsightTakeaway]'s KDoc) regardless of whatever
 * [CohortGrouping] the [CohortHeatmap] above it happens to be toggled to — a user comparing two
 * `ByWeek` rows up there (size-weighted-averaged, possibly trending a different direction) would
 * otherwise see this card's cohort-over-cohort sentence with no cue that it's a DIFFERENT
 * comparison scope, not a contradiction. [retention_insight_scope_note] makes that explicit in the
 * rendered text rather than silently leaving two differently-scoped numbers side by side on the
 * same screen.
 */
@Composable
internal fun RetentionClosingInsightCard(cohorts: List<RetentionCohort>, onAskWhy: () -> Unit = {}, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val takeaway = computeRetentionClosingInsightTakeaway(cohorts)
    val sentence = retentionComparisonSentence(takeaway, stringResource(Res.string.retention_insight_not_enough_data))

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, extended.hairline),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(Res.string.retention_insight_eyebrow_emoji), style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sentence, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.retention_insight_scope_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill(
                text = stringResource(Res.string.retention_insight_ask_why),
                variant = PillVariant.Neutral,
                onClick = onAskWhy,
            )
        }
    }
}
