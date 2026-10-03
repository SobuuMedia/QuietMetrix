package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.quietmetrix.dashboard.viewmodel.isLoading
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.FunnelStepResultDto
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.format.formatWilsonRange
import com.quietmetrix.dashboard.format.wilsonInterval
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.*
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.col_conversion_from_entry
import com.quietmetrix.dashboard.resources.col_conversion_from_previous
import com.quietmetrix.dashboard.resources.col_count
import com.quietmetrix.dashboard.resources.col_dropped
import com.quietmetrix.dashboard.resources.funnel_no_entrants
import com.quietmetrix.dashboard.resources.col_step
import com.quietmetrix.dashboard.resources.funnel_badge_sdk
import com.quietmetrix.dashboard.resources.funnel_card_steps
import com.quietmetrix.dashboard.resources.funnel_copy_csv
import com.quietmetrix.dashboard.resources.funnel_copy_csv_copied
import com.quietmetrix.dashboard.resources.funnel_delete
import com.quietmetrix.dashboard.resources.funnel_delete_confirm_body
import com.quietmetrix.dashboard.resources.funnel_delete_confirm_title
import com.quietmetrix.dashboard.resources.funnel_dropoff_message
import com.quietmetrix.dashboard.resources.funnel_dropoff_why
import com.quietmetrix.dashboard.resources.funnel_editor_cancel
import com.quietmetrix.dashboard.resources.funnel_expert_table_title
import com.quietmetrix.dashboard.resources.funnel_expert_toggle
import com.quietmetrix.dashboard.resources.funnel_eyebrow_emoji
import com.quietmetrix.dashboard.resources.funnel_headline_highlight
import com.quietmetrix.dashboard.resources.funnel_headline_lead
import com.quietmetrix.dashboard.resources.funnel_headline_tail
import com.quietmetrix.dashboard.resources.funnel_kpi_completed
import com.quietmetrix.dashboard.resources.funnel_kpi_started
import com.quietmetrix.dashboard.resources.funnel_kpi_success_rate
import com.quietmetrix.dashboard.resources.funnel_new_action
import com.quietmetrix.dashboard.resources.funnel_new_guide_code_js
import com.quietmetrix.dashboard.resources.funnel_new_guide_code_kotlin
import com.quietmetrix.dashboard.resources.funnel_new_guide_code_swift
import com.quietmetrix.dashboard.resources.funnel_new_guide_done
import com.quietmetrix.dashboard.resources.funnel_new_guide_intro
import com.quietmetrix.dashboard.resources.funnel_new_guide_step1_title
import com.quietmetrix.dashboard.resources.funnel_new_guide_step2_body
import com.quietmetrix.dashboard.resources.funnel_new_guide_step2_title
import com.quietmetrix.dashboard.resources.funnel_new_guide_title
import com.quietmetrix.dashboard.resources.funnel_none_selected
import com.quietmetrix.dashboard.resources.funnel_tutorial_intro
import com.quietmetrix.dashboard.resources.funnel_tutorial_step1
import com.quietmetrix.dashboard.resources.funnel_tutorial_step2
import com.quietmetrix.dashboard.resources.funnel_tutorial_title
import com.quietmetrix.dashboard.resources.funnels_title
import com.quietmetrix.dashboard.resources.ic_funnel
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.nav_funnels
import com.quietmetrix.dashboard.resources.overview_empty_setup_lang_js
import com.quietmetrix.dashboard.resources.overview_empty_setup_lang_kotlin
import com.quietmetrix.dashboard.resources.overview_empty_setup_lang_swift
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.KpiCard
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.ModalFooter
import com.quietmetrix.dashboard.ui.components.ModalFooterAction
import com.quietmetrix.dashboard.ui.components.ModalFooterEmphasis
import com.quietmetrix.dashboard.ui.components.ModalShell
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.charts.FunnelChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.FunnelStepData
import com.quietmetrix.dashboard.ui.components.contentStateFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.table.DataTable
import com.quietmetrix.dashboard.ui.components.ComparisonNote
import com.quietmetrix.dashboard.ui.components.comparisonNoteText
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
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
    // Local, not-yet-globally-wired toggle — same convention `DashboardShell.kt`'s own
    // `expertOn`/`compareOn` establish ("no Compare/Expert-mode state exists on DashboardState"):
    // the header's 🤓 Expert pill isn't plumbed to any screen yet, so this screen keeps its own
    // local flag for Task 28's "Expert mode adds a raw step table" requirement rather than
    // blocking on that larger, out-of-scope app-wide wiring.
    val whyQuestion = stringResource(Res.string.ask_funnel_question)
    val expertOn = state.expertMode
    var newFunnelGuideOpen by remember { mutableStateOf(false) }

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

            when (contentStateFor(state.isLoading(DataSection.Funnels), state.funnels.isEmpty())) {
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
                            onNewFunnel = { newFunnelGuideOpen = true },
                            expertOn = expertOn,
                            onExpertToggle = { viewModel.toggleExpertMode() },
                            onAskWhy = { step -> viewModel.openAskDrawer(whyQuestion.replace("%1\$s", step.name ?: step.key)) },
                            onDrillDown = { key -> viewModel.openDrillDown("funnel", key) },
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

    if (newFunnelGuideOpen) {
        NewFunnelGuideModal(onDismiss = { newFunnelGuideOpen = false })
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
    onNewFunnel: () -> Unit,
    expertOn: Boolean,
    onExpertToggle: () -> Unit,
    onAskWhy: (FunnelStepResultDto) -> Unit,
    onDrillDown: (String) -> Unit,
) {
    FunnelHero(funnel, state.funnelResults, modifier = Modifier.fillMaxWidth())

    FunnelSwitcherRow(funnels = funnels, currentKey = funnel.funnelKey, onSelect = onSelectFunnel, onNewFunnel = onNewFunnel)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (funnel.source == "sdk") {
            AssistChip(onClick = {}, label = { Text(stringResource(Res.string.funnel_badge_sdk)) })
        }
        Spacer(Modifier.weight(1f))
        Pill(
            text = stringResource(Res.string.funnel_expert_toggle),
            variant = if (expertOn) PillVariant.Lime else PillVariant.Neutral,
            onClick = onExpertToggle,
        )
        TextButton(onClick = onDelete, enabled = !state.isReadOnly, modifier = Modifier.handCursor()) {
            Text(stringResource(Res.string.funnel_delete), color = MaterialTheme.colorScheme.error)
        }
    }

    val results = state.funnelResults
    FunnelStatCardsRow(results, sizeClass, state.isLoading(DataSection.FunnelResults), onDrillDown, compare = state.compare, previous = state.comparison.funnel)

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
                    // The tapering overview chart (Task 27) above the Task 28 per-step list below
                    // it — see FunnelStepList's KDoc for why these are two distinct, complementary
                    // visualizations rather than one replacing the other.
                    FunnelChartCanvas(results.steps.map { FunnelStepData(it.name ?: it.key, it.count) },
                        previousCounts = if (state.compare) state.comparison.funnel?.steps?.map { it.count }.orEmpty() else emptyList(),
                        onStepClick = { index -> onDrillDown(results.steps[index].key) })
                    Spacer(Modifier.height(16.dp))
                    FunnelStepList(results.steps, onAskWhy = onAskWhy, onDrillDown = onDrillDown, compare = state.compare, previousSteps = state.comparison.funnel?.steps.orEmpty())
                }
            }
        }

        if (expertOn && results.steps.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.funnel_expert_table_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.weight(1f))
                        CsvCopyButton(csv = funnelStepsToCsv(results.steps))
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
}

/**
 * "⧉ Copy as CSV" button for the Expert-mode raw step table — same "copy, then show a transient
 * confirmation" idiom `CopyErrorButton` (`ErrorDisplay.kt`) already establishes, duplicated in
 * miniature here (a plain [TextButton] + [Pill]-less label swap, not an [IconButton]) since this
 * button carries its own visible text label rather than only an icon.
 */
@Composable
internal fun CsvCopyButton(csv: String) {
    val clipboard = LocalClipboardManager.current
    var justCopied by remember { mutableStateOf(false) }
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1500)
            justCopied = false
        }
    }
    TextButton(
        onClick = { clipboard.setText(AnnotatedString(csv)); justCopied = true },
        modifier = Modifier.handCursor(),
    ) {
        Text(stringResource(if (justCopied) Res.string.funnel_copy_csv_copied else Res.string.funnel_copy_csv))
    }
}

/**
 * The design handoff's per-step list (README.md > Funnels: "Step list: each step is an icon tile
 * + name/event-name row next to a horizontal bar (count + % of starters); between steps, a small
 * '😢 N people left here (X%)' line..."). Rendered as a plain [Column] of [FunnelStepRow]s with a
 * [FunnelDropoffCallout] spliced in wherever [shouldShowDropoffBefore] says a prior step lost
 * people — NOT built on top of [FunnelChartCanvas]/`funnelBars`: that component is a single
 * [androidx.compose.foundation.Canvas] drawing all bars (plus the shared-axis tapering silhouette
 * and its own adaptive inside/outside label placement) in one pass, which has no seam to splice a
 * Compose row (icon tile, drop-off text, a real clickable button) between two of its bars without
 * either reimplementing it row-by-row or threading foreign content through `DrawScope`. Reusing it
 * for *that* job turned out to be the wrong tool — see this task's report — so this list instead
 * reuses the same "track + proportional fill + overlaid label" bar idiom [FunnelChartCanvas]
 * itself and `OverviewScreen.kt`'s `UsageBar` already establish, just built from ordinary Compose
 * layout ([Box]/[Modifier.fillMaxWidth] with a fraction) instead of `Canvas`/`TextMeasurer`, which
 * is all a single, independently-addressable row needs. [FunnelChartCanvas] is NOT removed or
 * orphaned by this — it stays as the compact tapering-funnel overview chart directly above this
 * list ([FunnelDetail]), a coarser at-a-glance shape this detailed list complements rather than
 * duplicates.
 */
@Composable
internal fun FunnelStepList(steps: List<FunnelStepResultDto>, onAskWhy: (FunnelStepResultDto) -> Unit = {}, modifier: Modifier = Modifier,
    onDrillDown: (String) -> Unit = {}, compare: Boolean = false, previousSteps: List<FunnelStepResultDto> = emptyList()) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { index, step ->
            if (shouldShowDropoffBefore(step, isFirstStep = index == 0)) {
                FunnelDropoffCallout(step, onAskWhy = { onAskWhy(step) })
            }
            Column(Modifier.clickable { onDrillDown(step.key) }.handCursor()) {
                FunnelStepRow(index = index, step = step)
                if (compare) ComparisonNote(previousSteps.firstOrNull { it.key == step.key }?.count?.toLong()?.let(::formatCount))
            }
        }
    }
}

/** One step in [FunnelStepList]: a numbered icon tile + display name/event-key column, next to a
 *  [FunnelStepBar] sized by [FunnelStepResultDto.conversionFromEntry]. No per-step emoji exists in
 *  [FunnelStepResultDto] (unlike the design handoff's demo-data `st.emoji`), so the tile shows the
 *  step's 1-based ordinal instead of inventing icon semantics this dashboard can't know — the same
 *  honest-data stance [FunnelHero]'s KDoc documents for the headline above. */
@Composable
private fun FunnelStepRow(index: Int, step: FunnelStepResultDto, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(220.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text((index + 1).toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
            Column {
                Text(step.name ?: step.key, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (step.name != null) {
                    Text(
                        step.key,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        FunnelStepBar(
            fraction = step.conversionFromEntry.toFloat(),
            label = "${formatCount(step.count.toLong())} (${formatPercent(step.conversionFromEntry)})",
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A single horizontal bar: [LocalExtendedColors]'s `chipFill` track + a primary-colored fill sized
 * to [fraction] (0f-1f) — the same "track + proportional fill" idiom `OverviewScreen.kt`'s
 * `UsageBar` establishes, at [FunnelStepList]'s taller 52dp row height. [label] (count + percent)
 * is drawn inside the filled portion in [MaterialTheme.colorScheme.onPrimary] (guaranteed to
 * contrast with the `primary` fill) when that portion is wide enough to hold it, else just past
 * the fill's edge in the muted [MaterialTheme.colorScheme.onSurfaceVariant] — the same
 * inside-vs-outside idiom [FunnelChartCanvas]'s `drawStepLabels` already establishes for the
 * identical "light fill color would swallow light text" problem, reimplemented here with
 * [BoxWithConstraints] + a character-count width estimate instead of a [TextMeasurer] pass, since
 * this is a single Compose row rather than a `Canvas`.
 */
@Composable
private fun FunnelStepBar(fraction: Float, label: String, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val clamped = fraction.coerceIn(0f, 1f)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(52.dp).background(extended.chipFill, RoundedCornerShape(14.dp))) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
        )
        val fillWidth = maxWidth * clamped
        // Rough per-character width estimate (titleSmall is ~8dp/char) plus the 16dp horizontal
        // inset on each side — good enough to decide "does this short count+percent string fit",
        // without a real text measurement pass.
        val estimatedLabelWidth = (label.length * 8).dp + 32.dp
        if (fillWidth >= estimatedLabelWidth) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(start = fillWidth + 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The "😢 N people left here (X%)" line [FunnelStepList] renders before a step whose
 * [shouldShowDropoffBefore] is true, with a lavender "✨ Why did they leave?" [Pill] that would
 * open the Ask-your-data drawer with a synthesized answer per the design handoff — stubbed (no
 * `onClick` wiring) since that drawer is Task 40's scope, the same "stub now, wire later"
 * convention [FunnelStatCardsRow]'s own KPI-card click handlers already establish.
 */
@Composable
private fun FunnelDropoffCallout(step: FunnelStepResultDto, modifier: Modifier = Modifier, onAskWhy: () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp, start = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.funnel_dropoff_message, formatCount(step.dropped.toLong()), formatPercent(step.dropRate)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Pill(
            text = stringResource(Res.string.funnel_dropoff_why),
            variant = PillVariant.Lavender,
            onClick = onAskWhy,
        )
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
 * [DashboardViewModel.selectFunnel] by the caller). [onNewFunnel] opens [NewFunnelGuideModal] —
 * see [NewFunnelButton]'s KDoc for why that's an SDK-declaration guide rather than a builder modal.
 */
@Composable
internal fun FunnelSwitcherRow(
    funnels: List<FunnelDto>,
    currentKey: String?,
    onSelect: (String) -> Unit,
    onNewFunnel: () -> Unit = {},
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
        NewFunnelButton(onClick = onNewFunnel)
    }
}

/**
 * The design handoff's "＋ New funnel" button (opens a builder modal to create a funnel from
 * scratch in the original design handoff). **This dashboard has no dashboard-side funnel-creation
 * capability any more, by deliberate design, not oversight** — see [FunnelsTab]'s class KDoc: a
 * prior change removed the dashboard-side create/edit funnel UI entirely (`DashboardViewModel` has
 * no `createFunnel`/`updateFunnel` any more, only [DashboardViewModel.deleteFunnel]) once funnels
 * became SDK-`FunnelManifest`-declared and evaluated on-device (`FunnelCounterAnalyzer`'s
 * server-side KDoc: "now that evaluation is always device-local"). A dashboard-authored funnel
 * would have no on-device evaluator backing it, so resurrecting a create flow here would
 * reintroduce a capability the architecture migration specifically retired.
 *
 * **Re-scoped for Task 28 (user decision, overriding Task 27's own disabled-stub interim
 * measure):** rather than staying permanently disabled, this button is enabled and opens
 * [NewFunnelGuideModal] — a short instructional panel showing how to declare a `FunnelManifest` in
 * the SDK, the honest substitute for a capability that genuinely doesn't exist on this side any
 * more.
 */
@Composable
private fun NewFunnelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(painter = painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.funnel_new_action))
    }
}

/**
 * The instructional panel behind the re-enabled "＋ New funnel" button (Task 28's re-scoped
 * replacement for the nonexistent builder modal — see [NewFunnelButton]'s KDoc). Genuinely reuses
 * Task 21's [SetupInstructionsSection] pattern rather than a parallel reimplementation: the same
 * [SetupLanguage] Kotlin/Swift/JavaScript switch (now `internal` in `OverviewScreen.kt`, not
 * duplicated), the same numbered-step shape, and the same [SetupCodeBlock] composable for the code
 * snippets — just hosted in a [ModalShell] (Task 14) instead of an inline card, since this is
 * opened from a button click rather than shown in place of empty-state content. Code excerpts are
 * shortened real excerpts from `docs/sdk/android.md`/`docs/sdk/ios.md`/`docs/sdk/web.md`'s own
 * "Funnels" sections — see `funnel_new_guide_code_*` in `strings.xml`.
 */
@Composable
internal fun NewFunnelGuideModal(onDismiss: () -> Unit) {
    var language by remember { mutableStateOf(SetupLanguage.Kotlin) }

    ModalShell(
        title = stringResource(Res.string.funnel_new_guide_title),
        onDismiss = onDismiss,
        footer = {
            ModalFooter(
                primary = ModalFooterAction(
                    label = stringResource(Res.string.funnel_new_guide_done),
                    emphasis = ModalFooterEmphasis.Secondary,
                    onClick = onDismiss,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(Res.string.funnel_new_guide_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SetupLanguage.entries.forEach { lang ->
                    val label = when (lang) {
                        SetupLanguage.Kotlin -> stringResource(Res.string.overview_empty_setup_lang_kotlin)
                        SetupLanguage.Swift -> stringResource(Res.string.overview_empty_setup_lang_swift)
                        SetupLanguage.JavaScript -> stringResource(Res.string.overview_empty_setup_lang_js)
                    }
                    Pill(
                        text = label,
                        variant = if (lang == language) PillVariant.Lavender else PillVariant.Neutral,
                        onClick = { language = lang },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.funnel_new_guide_step1_title), style = MaterialTheme.typography.titleSmall)
                SetupCodeBlock(
                    when (language) {
                        SetupLanguage.Kotlin -> stringResource(Res.string.funnel_new_guide_code_kotlin)
                        SetupLanguage.Swift -> stringResource(Res.string.funnel_new_guide_code_swift)
                        SetupLanguage.JavaScript -> stringResource(Res.string.funnel_new_guide_code_js)
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.funnel_new_guide_step2_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.funnel_new_guide_step2_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
internal fun FunnelStatCardsRow(results: FunnelResultsResponse?, sizeClass: WindowSizeClass, loading: Boolean, onDrillDown: (String) -> Unit = {}, compare: Boolean = false, previous: FunnelResultsResponse? = null) {
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
                    results?.entered?.toLong()?.let(::formatCount) ?: stringResource(Res.string.value_none),
                    Modifier.fillSlot(),
                    onClick = { onDrillDown("started") },
                    compareNote = if (compare) comparisonNoteText(previous?.entered?.toLong()?.let(::formatCount)) else null,
                )
                KpiCard(
                    stringResource(Res.string.funnel_kpi_completed),
                    results?.converted?.toLong()?.let(::formatCount) ?: stringResource(Res.string.value_none),
                    Modifier.fillSlot(),
                    onClick = { onDrillDown("completed") },
                    compareNote = if (compare) comparisonNoteText(previous?.converted?.toLong()?.let(::formatCount)) else null,
                )
                val overallInterval = results?.let { wilsonInterval(it.converted.toLong(), it.entered.toLong()) }
                KpiCard(
                    stringResource(Res.string.funnel_kpi_success_rate),
                    results?.overallConversion?.let(::formatPercent) ?: stringResource(Res.string.value_none),
                    Modifier.fillSlot(),
                    sub = overallInterval?.let { formatWilsonRange(it) },
                    highlighted = true,
                    onClick = { onDrillDown("rate") },
                    compareNote = if (compare) comparisonNoteText(previous?.overallConversion?.let(::formatPercent)) else null,
                )
            }
        }
    }
}
