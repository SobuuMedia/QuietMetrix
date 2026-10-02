package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.FunnelStepResultDto
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.format.formatWilsonRange
import com.quietmetrix.dashboard.format.wilsonInterval
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.col_conversion_from_entry
import com.quietmetrix.dashboard.resources.col_conversion_from_previous
import com.quietmetrix.dashboard.resources.col_count
import com.quietmetrix.dashboard.resources.col_dropped
import com.quietmetrix.dashboard.resources.funnel_no_entrants
import com.quietmetrix.dashboard.resources.col_step
import com.quietmetrix.dashboard.resources.funnel_badge_sdk
import com.quietmetrix.dashboard.resources.funnel_card_steps
import com.quietmetrix.dashboard.resources.funnel_delete
import com.quietmetrix.dashboard.resources.funnel_delete_confirm_body
import com.quietmetrix.dashboard.resources.funnel_delete_confirm_title
import com.quietmetrix.dashboard.resources.funnel_editor_cancel
import com.quietmetrix.dashboard.resources.funnel_empty
import com.quietmetrix.dashboard.resources.funnel_eyebrow_emoji
import com.quietmetrix.dashboard.resources.funnel_headline_highlight
import com.quietmetrix.dashboard.resources.funnel_headline_lead
import com.quietmetrix.dashboard.resources.funnel_headline_tail
import com.quietmetrix.dashboard.resources.funnel_kpi_completed
import com.quietmetrix.dashboard.resources.funnel_kpi_started
import com.quietmetrix.dashboard.resources.funnel_kpi_success_rate
import com.quietmetrix.dashboard.resources.funnel_new_action
import com.quietmetrix.dashboard.resources.funnel_new_unavailable
import com.quietmetrix.dashboard.resources.funnel_none_selected
import com.quietmetrix.dashboard.resources.funnel_tutorial_intro
import com.quietmetrix.dashboard.resources.funnel_tutorial_step1
import com.quietmetrix.dashboard.resources.funnel_tutorial_step2
import com.quietmetrix.dashboard.resources.funnel_tutorial_title
import com.quietmetrix.dashboard.resources.funnels_title
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_funnel
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.nav_funnels
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.KpiCard
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.charts.FunnelChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.FunnelStepData
import com.quietmetrix.dashboard.ui.components.contentStateFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.table.DataTable
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.nav.NavDestination as AppTab
import com.quietmetrix.dashboard.ui.components.table.Column as TableColumn

/**
 * Funnels are declared entirely in app code (the SDK's `FunnelManifest`) and evaluated
 * on-device — the dashboard shows results and lets an operator delete a definition, but it is
 * not a source of truth for funnel steps any more, so there is no create/edit UI here. See
 * [NewFunnelButton]'s KDoc for how this collides with the design handoff's "＋ New funnel" button.
 */
@Composable
fun FunnelsTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    var pendingDelete by remember { mutableStateOf<FunnelDto?>(null) }

    val funnel = currentFunnel(state.funnels, state.selectedFunnelKey)

    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.funnels_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
                WindowPicker(state.range) { viewModel.selectRange(it) }
                IconButton(onClick = { viewModel.showTabInfo(AppTab.Funnels) }, modifier = Modifier.handCursor()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = stringResource(Res.string.tab_info_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            state.sectionErrors[DataSection.Funnels]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }
            state.sectionErrors[DataSection.FunnelResults]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }

            when (contentStateFor(state.loading, state.funnels.isEmpty())) {
                ContentState.Loading -> {
                    ResponsiveRowOrColumn(sizeClass) {
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                    }
                }
                ContentState.Empty -> FunnelsGettingStarted()
                ContentState.Data -> {
                    if (funnel == null) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            EmptyState(Res.drawable.ic_funnel, Res.string.funnel_none_selected, Modifier.padding(16.dp))
                        }
                    } else {
                        FunnelDetail(
                            state = state,
                            funnels = state.funnels,
                            funnel = funnel,
                            sizeClass = sizeClass,
                            onSelectFunnel = { viewModel.selectFunnel(it) },
                            onDelete = { pendingDelete = funnel },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.funnel_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.funnel_delete_confirm_body, f.name)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteFunnel(f.funnelKey); pendingDelete = null },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.funnel_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.funnel_editor_cancel))
                }
            },
        )
    }
}

/** Shown instead of the plain empty state when a project has no funnels yet — explains what a
 *  funnel is and that it's declared in app code, not here. */
@Composable
private fun FunnelsGettingStarted() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painter = painterResource(Res.drawable.ic_funnel),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Text(stringResource(Res.string.funnel_tutorial_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(Res.string.funnel_tutorial_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FunnelTutorialStep(1, stringResource(Res.string.funnel_tutorial_step1))
                FunnelTutorialStep(2, stringResource(Res.string.funnel_tutorial_step2))
            }
        }
    }
}

@Composable
private fun FunnelTutorialStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FunnelDetail(
    state: DashboardState,
    funnels: List<FunnelDto>,
    funnel: FunnelDto,
    sizeClass: WindowSizeClass,
    onSelectFunnel: (String) -> Unit,
    onDelete: () -> Unit,
) {
    FunnelHero(funnel, state.funnelResults, modifier = Modifier.fillMaxWidth())

    FunnelSwitcherRow(funnels = funnels, currentKey = funnel.funnelKey, onSelect = onSelectFunnel)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (funnel.source == "sdk") {
            AssistChip(onClick = {}, label = { Text(stringResource(Res.string.funnel_badge_sdk)) })
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDelete, enabled = !state.isReadOnly, modifier = Modifier.handCursor()) {
            Text(stringResource(Res.string.funnel_delete), color = MaterialTheme.colorScheme.error)
        }
    }

    val results = state.funnelResults
    FunnelStatCardsRow(results, sizeClass, state.loading)

    if (results != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(Res.string.funnel_card_steps),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (results.entered == 0) {
                    Text(stringResource(Res.string.funnel_no_entrants), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FunnelChartCanvas(results.steps.map { FunnelStepData(it.name ?: it.key, it.count) })
                }
                Spacer(Modifier.height(8.dp))
                DataTable(
                    columns = funnelStepColumns(results.entered, results.steps.firstOrNull()?.key),
                    rows = results.steps,
                    sizeClass = sizeClass,
                    modifier = Modifier.fillMaxWidth().height((results.steps.size * 56 + 56).dp),
                )
            }
        }
    }
}

/**
 * [entered] is the funnel's entry count — the denominator every step's Wilson interval is
 * computed against, since [FunnelStepResultDto.conversionFromEntry] is itself `count / entered`.
 * [firstStepKey] identifies the entry step, whose "conversion from previous" is deliberately
 * `0.0` server-side (there is no previous step) — shown as [value_none] rather than a
 * misleading "0%".
 */
@Composable
private fun funnelStepColumns(entered: Int, firstStepKey: String?): List<TableColumn<FunnelStepResultDto>> = listOf(
    TableColumn(stringResource(Res.string.col_step), 1.4f, { Text(it.name ?: it.key) }),
    TableColumn(stringResource(Res.string.col_count), 0.8f, { Text(formatCount(it.count.toLong())) }),
    TableColumn(
        stringResource(Res.string.col_conversion_from_entry), 1.2f,
        { step ->
            val interval = wilsonInterval(step.count.toLong(), entered.toLong())
            Column {
                Text(formatPercent(step.conversionFromEntry), style = MaterialTheme.typography.bodyMedium)
                if (interval != null) {
                    Text(
                        formatWilsonRange(interval),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        hideOnCompact = true,
    ),
    TableColumn(
        stringResource(Res.string.col_conversion_from_previous), 1f,
        { step ->
            Text(if (step.key == firstStepKey) stringResource(Res.string.value_none) else formatPercent(step.conversionFromPrevious))
        },
        hideOnCompact = true,
    ),
    TableColumn(stringResource(Res.string.col_dropped), 0.8f, { Text(formatCount(it.dropped.toLong())) }),
)

// ---------------------------------------------------------------------------
// H1 headline, funnel-switcher pill row + "＋ New funnel" button, stat cards
// — Task 27
// ---------------------------------------------------------------------------

/**
 * The "🪜 Funnels" eyebrow + the plain-language H1 (design handoff: "H1 is a plain-language
 * headline generated from the funnel's numbers (e.g. 'Out of 10 people who open Shelves, 4 end up
 * adding a book')"), with the live out-of-10 number lime-highlighted — same visual idiom as
 * [RetentionHero]'s D1/D7 numbers.
 *
 * **Wording deviation from the design handoff, found by inspecting its own mockup** (same category
 * of finding as `OverviewHeroLogic.kt`'s "Relabeling" KDoc): `_source/07-funnels-normal.png`'s H1
 * region (fully visible, unobscured by the Ask-drawer overlay) literally renders "Out of 10 people
 * who open Shelves, 4 end up adding a book." — turning the funnel's first/last step into an English
 * verb phrase ("open Shelves", "adding a book"). [FunnelStepResultDto]/`FunnelStepDto` do carry an
 * optional display `name: String?` (wired through server-side by
 * `FunnelCounterAnalyzer.kt`: `name = step.name`), which — if an app developer happens to name a
 * step something verb-shaped like "Open Shelves" in their `FunnelManifest` — could support closer-
 * to-mockup phrasing for that specific funnel. But it's optional, developer-supplied free text with
 * no guaranteed grammatical shape across funnels in general (a step just as plausibly gets named
 * "screen_view" or left `null`, falling back to its raw `key`), so building a universal "who
 * open X"/"end up Y-ing" sentence out of it would mean guessing at semantics this dashboard can't
 * rely on for every funnel — the same category of fabrication risk the Overview hero's
 * "people"/"18% more" wording was corrected for. This headline instead names the funnel by its own
 * always-present, human-authored [FunnelDto.name] ("Out of 10 people who started “Shelves”, 4 make
 * it all the way through."), which is real, already-labeled data every funnel has, while keeping
 * the exact same "out of 10, highlighted number" shape and real [funnelHeadlineOutOfTen] math the
 * mockup's example demonstrates.
 *
 * Falls back to [Res.string.funnel_no_entrants] when [funnelHeadlineOutOfTen] returns null (no
 * entrants yet this range) rather than fabricating a number — the same string the steps card below
 * already shows for the identical condition.
 */
@Composable
internal fun FunnelHero(funnel: FunnelDto, results: FunnelResultsResponse?, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val lime = SpanStyle(background = extended.lime, color = extended.onLime)
    val outOfTen = funnelHeadlineOutOfTen(results)

    val lead = outOfTen?.let { stringResource(Res.string.funnel_headline_lead, funnel.name) }
    val highlight = outOfTen?.let { stringResource(Res.string.funnel_headline_highlight, it) }
    val tail = stringResource(Res.string.funnel_headline_tail)
    val noEntrants = stringResource(Res.string.funnel_no_entrants)

    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.funnel_eyebrow_emoji) + " " + stringResource(Res.string.nav_funnels),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val sentence = buildAnnotatedString {
            if (outOfTen == null) {
                append(noEntrants)
            } else {
                append(lead!!)
                withStyle(lime) { append(highlight!!) }
                append(tail)
            }
        }
        Text(sentence, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * The funnel-switcher pill row (design handoff: "Funnel-switcher pill row + '＋ New funnel' button
 * (opens builder modal)") — real functionality, mirroring [StartFromRow]'s established pattern
 * (`FlowScreen.kt`): one [Pill] per real [FunnelDto], the current selection highlighted
 * [PillVariant.Lime], clicking another calls [onSelect] (already wired to
 * [DashboardViewModel.selectFunnel] by the caller). See [NewFunnelButton]'s KDoc for why the
 * trailing button is a disabled stub rather than a real builder modal.
 */
@Composable
internal fun FunnelSwitcherRow(
    funnels: List<FunnelDto>,
    currentKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        funnels.forEach { f ->
            Pill(
                text = f.name,
                variant = if (f.funnelKey == currentKey) PillVariant.Lime else PillVariant.Neutral,
                onClick = { onSelect(f.funnelKey) },
            )
        }
        NewFunnelButton()
    }
}

/**
 * The design handoff's "＋ New funnel" button (opens a builder modal to create a funnel from
 * scratch). **This dashboard has no such capability any more, by deliberate design, not oversight**
 * — see [FunnelsTab]'s class KDoc: a prior change removed the dashboard-side create/edit funnel UI
 * entirely (`DashboardViewModel` has no `createFunnel`/`updateFunnel` any more, only
 * [DashboardViewModel.deleteFunnel]) once funnels became SDK-`FunnelManifest`-declared and
 * evaluated on-device (`FunnelCounterAnalyzer`'s server-side KDoc: "now that evaluation is always
 * device-local"). A dashboard-authored funnel would have no on-device evaluator backing it, so
 * resurrecting a create flow here would not just be "not built yet" (the honest category the
 * drill-down-drawer stubs elsewhere in this plan are in) — it would reintroduce a capability the
 * architecture migration specifically retired.
 *
 * Rendered disabled (not merely an inert `onClick = {}`) so the one user-facing signal is honest:
 * this action is unavailable, not silently broken. Flagged for a product decision before Task 28
 * (the "New funnel modal" task) attempts to build a real modal behind it — see this task's report.
 */
@Composable
private fun NewFunnelButton(modifier: Modifier = Modifier) {
    val unavailable = stringResource(Res.string.funnel_new_unavailable)
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier.semantics { contentDescription = unavailable },
    ) {
        Icon(painter = painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.funnel_new_action))
    }
}

/**
 * The 3 stat cards (design handoff: "3 stat cards (Started / Made it to the end / Success rate —
 * the last one lime-filled), each clickable to a funnel-wide drill drawer"). [results] is null
 * while still loading (handled by [loading]) or genuinely absent; its 0-valued defaults render
 * honestly as zeros rather than this row inventing its own empty state, matching every other
 * `KpiCard` row in this plan ([RetentionVitalCardsRow], the Overview vitals grid).
 *
 * The click handlers are stubs — same "stub now, wire for real later" convention
 * [RetentionVitalCard]'s own click handler already established — since the real funnel-wide drill
 * drawer is Task 41's scope, not this one's.
 */
@Composable
internal fun FunnelStatCardsRow(results: FunnelResultsResponse?, sizeClass: WindowSizeClass, loading: Boolean) {
    ResponsiveRowOrColumn(sizeClass) {
        when (contentStateFor(loading, results == null)) {
            ContentState.Loading -> {
                KpiSkeleton(Modifier.fillSlot())
                KpiSkeleton(Modifier.fillSlot())
                KpiSkeleton(Modifier.fillSlot())
            }
            else -> {
                KpiCard(
                    stringResource(Res.string.funnel_kpi_started),
                    formatCount((results?.entered ?: 0).toLong()),
                    Modifier.fillSlot(),
                    onClick = { /* Drill-down drawer wiring is Task 41's scope, not this one's. */ },
                )
                KpiCard(
                    stringResource(Res.string.funnel_kpi_completed),
                    formatCount((results?.converted ?: 0).toLong()),
                    Modifier.fillSlot(),
                    onClick = { /* Drill-down drawer wiring is Task 41's scope, not this one's. */ },
                )
                val overallInterval = results?.let { wilsonInterval(it.converted.toLong(), it.entered.toLong()) }
                KpiCard(
                    stringResource(Res.string.funnel_kpi_success_rate),
                    formatPercent(results?.overallConversion ?: 0.0),
                    Modifier.fillSlot(),
                    sub = overallInterval?.let { formatWilsonRange(it) },
                    highlighted = true,
                    onClick = { /* Drill-down drawer wiring is Task 41's scope, not this one's. */ },
                )
            }
        }
    }
}

