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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.CreateFunnelRequest
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.FunnelStepDto
import com.quietmetrix.dashboard.api.FunnelStepResultDto
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.format.formatWilsonRange
import com.quietmetrix.dashboard.format.wilsonInterval
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ask_funnel_question
import com.quietmetrix.dashboard.resources.col_conversion_from_entry
import com.quietmetrix.dashboard.resources.col_conversion_from_previous
import com.quietmetrix.dashboard.resources.col_count
import com.quietmetrix.dashboard.resources.col_dropped
import com.quietmetrix.dashboard.resources.col_step
import com.quietmetrix.dashboard.resources.funnel_badge_sdk
import com.quietmetrix.dashboard.resources.funnel_builder_add_step
import com.quietmetrix.dashboard.resources.funnel_builder_create
import com.quietmetrix.dashboard.resources.funnel_builder_description
import com.quietmetrix.dashboard.resources.funnel_builder_event
import com.quietmetrix.dashboard.resources.funnel_builder_intro
import com.quietmetrix.dashboard.resources.funnel_builder_key
import com.quietmetrix.dashboard.resources.funnel_builder_key_format
import com.quietmetrix.dashboard.resources.funnel_builder_move_down
import com.quietmetrix.dashboard.resources.funnel_builder_move_up
import com.quietmetrix.dashboard.resources.funnel_builder_name
import com.quietmetrix.dashboard.resources.funnel_builder_remove_step
import com.quietmetrix.dashboard.resources.funnel_builder_screen_optional
import com.quietmetrix.dashboard.resources.funnel_builder_step_key
import com.quietmetrix.dashboard.resources.funnel_builder_step_name
import com.quietmetrix.dashboard.resources.funnel_builder_step_number
import com.quietmetrix.dashboard.resources.funnel_builder_steps
import com.quietmetrix.dashboard.resources.funnel_builder_title
import com.quietmetrix.dashboard.resources.funnel_builder_window_days
import com.quietmetrix.dashboard.resources.funnel_card_steps
import com.quietmetrix.dashboard.resources.funnel_copy_csv
import com.quietmetrix.dashboard.resources.funnel_copy_csv_copied
import com.quietmetrix.dashboard.resources.funnel_delete
import com.quietmetrix.dashboard.resources.funnel_delete_confirm_body
import com.quietmetrix.dashboard.resources.funnel_delete_confirm_title
import com.quietmetrix.dashboard.resources.funnel_dropoff_message
import com.quietmetrix.dashboard.resources.funnel_dropoff_why
import com.quietmetrix.dashboard.resources.funnel_editor_cancel
import com.quietmetrix.dashboard.resources.funnel_empty_intro
import com.quietmetrix.dashboard.resources.funnel_empty_title
import com.quietmetrix.dashboard.resources.funnel_expert_table_title
import com.quietmetrix.dashboard.resources.funnel_expert_toggle
import com.quietmetrix.dashboard.resources.funnel_headline_highlight
import com.quietmetrix.dashboard.resources.funnel_headline_lead
import com.quietmetrix.dashboard.resources.funnel_headline_tail
import com.quietmetrix.dashboard.resources.funnel_kpi_completed
import com.quietmetrix.dashboard.resources.funnel_kpi_started
import com.quietmetrix.dashboard.resources.funnel_kpi_success_rate
import com.quietmetrix.dashboard.resources.funnel_new_action
import com.quietmetrix.dashboard.resources.funnel_no_entrants
import com.quietmetrix.dashboard.resources.funnel_none_selected
import com.quietmetrix.dashboard.resources.ic_check
import com.quietmetrix.dashboard.resources.ic_close
import com.quietmetrix.dashboard.resources.ic_copy
import com.quietmetrix.dashboard.resources.ic_funnel
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.nav_funnels
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.ComparisonNote
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.DashboardCard
import com.quietmetrix.dashboard.ui.components.EmojiVector
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
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
import com.quietmetrix.dashboard.ui.components.comparisonNoteText
import com.quietmetrix.dashboard.ui.components.contentStateFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.responsiveDisplayStyle
import com.quietmetrix.dashboard.ui.components.table.DataTable
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.isLoading
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.ui.components.SelectableText as Text
import com.quietmetrix.dashboard.ui.components.table.Column as TableColumn

/**
 * Funnels can be declared in app code or created from this dashboard. Dashboard definitions are
 * persisted through the existing project CRUD API; SDK delivery/evaluation of those definitions
 * is being completed in the separate remote-revision work.
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
    var newFunnelDialogOpen by remember { mutableStateOf(false) }

    val funnel = currentFunnel(state.funnels, state.selectedFunnelKey)

    MaxWidthContainer(sizeClass = sizeClass, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                ContentState.Empty -> FunnelsGettingStarted(
                    canWrite = state.canWriteExperiments,
                    onCreate = { newFunnelDialogOpen = true },
                )
                ContentState.Data -> {
                    if (funnel == null) {
                        DashboardCard(modifier = Modifier.fillMaxWidth()) {
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
                            onNewFunnel = { newFunnelDialogOpen = true },
                            canWrite = state.canWriteExperiments,
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
                ) { MaterialText(stringResource(Res.string.funnel_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, modifier = Modifier.handCursor()) {
                    MaterialText(stringResource(Res.string.funnel_editor_cancel))
                }
            },
        )
    }

    if (newFunnelDialogOpen) {
        NewFunnelBuilderDialog(
            onDismiss = { newFunnelDialogOpen = false },
            onCreate = { request -> viewModel.createFunnel(request); newFunnelDialogOpen = false },
        )
    }
}

/** First-funnel state gives project editors the same real builder action as the populated view. */
@Composable
private fun FunnelsGettingStarted(canWrite: Boolean, onCreate: () -> Unit) {
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
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
                Text(stringResource(Res.string.funnel_empty_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(Res.string.funnel_empty_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canWrite) NewFunnelButton(onClick = onCreate)
        }
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
    canWrite: Boolean,
    expertOn: Boolean,
    onExpertToggle: () -> Unit,
    onAskWhy: (FunnelStepResultDto) -> Unit,
    onDrillDown: (String) -> Unit,
) {
    FunnelHero(funnel, state.funnelResults, modifier = Modifier.fillMaxWidth(),
        onNewFunnel = onNewFunnel, canWrite = canWrite)

    FunnelSwitcherRow(funnels = funnels, currentKey = funnel.funnelKey, onSelect = onSelectFunnel)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (funnel.source == "sdk") {
            AssistChip(onClick = {}, label = { MaterialText(stringResource(Res.string.funnel_badge_sdk)) }, modifier = Modifier.handCursor())
        }
        Spacer(Modifier.weight(1f))
        Pill(
            text = stringResource(Res.string.funnel_expert_toggle),
            variant = if (expertOn) PillVariant.Lime else PillVariant.Neutral,
            onClick = onExpertToggle,
        )
        TextButton(onClick = onDelete, enabled = !state.isReadOnly, modifier = Modifier.handCursor()) {
            MaterialText(stringResource(Res.string.funnel_delete), color = MaterialTheme.colorScheme.error)
        }
    }

    val results = state.funnelResults
    FunnelStatCardsRow(results, sizeClass, state.isLoading(DataSection.FunnelResults), onDrillDown, compare = state.compare, previous = state.comparison.funnel)

    if (results != null) {
        DashboardCard(modifier = Modifier.fillMaxWidth()) {
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
            DashboardCard(modifier = Modifier.fillMaxWidth()) {
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
        Icon(
            painter = painterResource(if (justCopied) Res.drawable.ic_check else Res.drawable.ic_copy),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        MaterialText(stringResource(if (justCopied) Res.string.funnel_copy_csv_copied else Res.string.funnel_copy_csv))
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
    val aiAvailable = com.quietmetrix.dashboard.ui.components.LocalAiAccess.current.available
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp, start = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiVector("😢", Modifier.size(18.dp))
        Text(
            stringResource(Res.string.funnel_dropoff_message, formatCount(step.dropped.toLong()), formatPercent(step.dropRate)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (com.quietmetrix.dashboard.ui.components.LocalAiAccess.current.enabled) Pill(
            text = stringResource(Res.string.funnel_dropoff_why),
            variant = PillVariant.Lavender,
            onClick = onAskWhy, enabled = aiAvailable,
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
internal fun FunnelHero(
    funnel: FunnelDto,
    results: FunnelResultsResponse?,
    modifier: Modifier = Modifier,
    onNewFunnel: () -> Unit = {},
    canWrite: Boolean = false,
) {
    val extended = LocalExtendedColors.current
    val lime = SpanStyle(background = extended.lime, color = extended.onLime)
    val outOfTen = funnelHeadlineOutOfTen(results)

    val lead = outOfTen?.let { stringResource(Res.string.funnel_headline_lead, funnel.name) }
    val highlight = outOfTen?.let { stringResource(Res.string.funnel_headline_highlight, it) }
    val tail = stringResource(Res.string.funnel_headline_tail)
    val noEntrants = stringResource(Res.string.funnel_no_entrants)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EmojiVector("🪜", Modifier.size(22.dp))
            Text(stringResource(Res.string.nav_funnels), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sentence = buildAnnotatedString {
            if (outOfTen == null) {
                append(noEntrants)
            } else {
                append(lead!!)
                withStyle(lime) { append(highlight!!) }
                append(tail)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val heroStyle = responsiveDisplayStyle(maxWidth)
            if (maxWidth < 760.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(sentence, style = heroStyle, color = MaterialTheme.colorScheme.onBackground)
                    if (canWrite) NewFunnelButton(onClick = onNewFunnel)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Text(sentence, style = heroStyle, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f))
                    if (canWrite) NewFunnelButton(onClick = onNewFunnel)
                }
            }
        }
    }
}

/**
 * The funnel-switcher pill row — real definitions, mirroring [StartFromRow]'s established pattern
 * (`FlowScreen.kt`): one [Pill] per real [FunnelDto], the current selection highlighted
 * [PillVariant.Lime], clicking another calls [onSelect] (already wired to
 * [DashboardViewModel.selectFunnel] by the caller). Creation is placed beside the hero as in the
 * handoff, rather than inside this horizontally scrolling selector.
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
    }
}

/**
 * Opens the real dashboard funnel definition form. Creation is persisted through the existing
 * authenticated backend CRUD route. Dashboard-authored definitions still require remote SDK
 * configuration before they can be evaluated by installations; that connection is tracked
 * separately in the funnel revision/evaluator work.
 */
@Composable
private fun NewFunnelButton(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    Button(onClick = onClick, enabled = enabled, modifier = modifier.handCursor(),
        colors = ButtonDefaults.buttonColors(containerColor = extended.lime, contentColor = extended.onLime)) {
        Icon(painter = painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        MaterialText(stringResource(Res.string.funnel_new_action))
    }
}

private data class FunnelStepDraft(
    val key: String,
    val event: String = "",
    val name: String = "",
    val screen: String = "",
)

/** Creates a dashboard-owned definition through the existing authenticated funnel API. */
@Composable
internal fun NewFunnelBuilderDialog(onDismiss: () -> Unit, onCreate: (CreateFunnelRequest) -> Unit) {
    var key by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var windowDays by remember { mutableStateOf("7") }
    val steps = remember { mutableStateListOf(FunnelStepDraft("step_1"), FunnelStepDraft("step_2")) }
    val validKey = key.matches(Regex("^[a-z0-9_-]{1,64}$"))
    val validSteps = steps.size in 2..10 && steps.all {
        it.key.matches(Regex("^[a-z0-9_-]{1,64}$")) && it.event.isNotBlank()
    } && steps.map { it.key }.toSet().size == steps.size
    val days = windowDays.toIntOrNull()
    val valid = validKey && name.isNotBlank() && validSteps && (days ?: 0) in 1..90

    ModalShell(
        title = stringResource(Res.string.funnel_builder_title),
        onDismiss = onDismiss,
        footer = {
            ModalFooter(
                secondary = ModalFooterAction(stringResource(Res.string.funnel_editor_cancel), ModalFooterEmphasis.Secondary, onDismiss),
                primary = ModalFooterAction(stringResource(Res.string.funnel_builder_create), ModalFooterEmphasis.Primary) {
                    if (valid) onCreate(
                        CreateFunnelRequest(
                            funnelKey = key.trim(),
                            name = name.trim(),
                            description = description.trim().ifEmpty { null },
                            steps = steps.map {
                                FunnelStepDto(it.key.trim(), it.event.trim(), it.name.trim().ifEmpty { null }, it.screen.trim().ifEmpty { null })
                            },
                            windowSeconds = days!!.toLong() * 86_400,
                        ),
                    )
                },
                primaryEnabled = valid,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(Res.string.funnel_builder_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(key, { key = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' } },
                label = { Text(stringResource(Res.string.funnel_builder_key)) }, singleLine = true,
                supportingText = { Text(stringResource(Res.string.funnel_builder_key_format)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.funnel_builder_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(Res.string.funnel_builder_description)) },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(windowDays, { windowDays = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(Res.string.funnel_builder_window_days)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.funnel_builder_steps), style = MaterialTheme.typography.titleMedium)
            steps.forEachIndexed { index, step ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.funnel_builder_step_number, index + 1),
                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(enabled = index > 0, onClick = { val item = steps.removeAt(index); steps.add(index - 1, item) }, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.funnel_builder_move_up)) }
                            TextButton(enabled = index < steps.lastIndex, onClick = { val item = steps.removeAt(index); steps.add(index + 1, item) }, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.funnel_builder_move_down)) }
                            IconButton(enabled = steps.size > 2, onClick = { steps.removeAt(index) }, modifier = Modifier.handCursor()) {
                                Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.funnel_builder_remove_step),
                                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                        OutlinedTextField(step.key, { value -> steps[index] = step.copy(key = value.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }) },
                            label = { Text(stringResource(Res.string.funnel_builder_step_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(step.event, { value -> steps[index] = step.copy(event = value) },
                            label = { Text(stringResource(Res.string.funnel_builder_event)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(step.name, { value -> steps[index] = step.copy(name = value) },
                            label = { Text(stringResource(Res.string.funnel_builder_step_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(step.screen, { value -> steps[index] = step.copy(screen = value) },
                            label = { Text(stringResource(Res.string.funnel_builder_screen_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            OutlinedButton(onClick = { if (steps.size < 10) steps.add(FunnelStepDraft("step_${steps.size + 1}")) }, enabled = steps.size < 10, modifier = Modifier.handCursor()) {
                MaterialText(stringResource(Res.string.funnel_builder_add_step))
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
