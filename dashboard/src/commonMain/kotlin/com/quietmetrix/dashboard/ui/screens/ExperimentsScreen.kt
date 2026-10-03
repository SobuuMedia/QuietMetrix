package com.quietmetrix.dashboard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.CreateExperimentRequest
import com.quietmetrix.dashboard.api.ExperimentDto
import com.quietmetrix.dashboard.api.VariantDto
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.format.formatWilsonRange
import com.quietmetrix.dashboard.format.wilsonInterval
import com.quietmetrix.dashboard.nav.classifyWindow
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_got_it
import com.quietmetrix.dashboard.resources.experiment_action_delete
import com.quietmetrix.dashboard.resources.experiment_action_end
import com.quietmetrix.dashboard.resources.experiment_action_pause
import com.quietmetrix.dashboard.resources.experiment_action_resume
import com.quietmetrix.dashboard.resources.experiment_action_start
import com.quietmetrix.dashboard.resources.experiment_delete_confirm_body
import com.quietmetrix.dashboard.resources.experiment_delete_confirm_title
import com.quietmetrix.dashboard.resources.experiment_editor_cancel
import com.quietmetrix.dashboard.resources.experiment_editor_countries_hint
import com.quietmetrix.dashboard.resources.experiment_editor_countries_invalid
import com.quietmetrix.dashboard.resources.experiment_editor_countries_label
import com.quietmetrix.dashboard.resources.experiment_editor_key_hint
import com.quietmetrix.dashboard.resources.experiment_editor_key_label
import com.quietmetrix.dashboard.resources.experiment_editor_name_label
import com.quietmetrix.dashboard.resources.experiment_editor_screen_label
import com.quietmetrix.dashboard.resources.experiment_editor_split_label
import com.quietmetrix.dashboard.resources.experiment_editor_submit
import com.quietmetrix.dashboard.resources.experiment_editor_title
import com.quietmetrix.dashboard.resources.experiment_editor_traffic_label
import com.quietmetrix.dashboard.resources.experiment_editor_variant_a_label
import com.quietmetrix.dashboard.resources.experiment_editor_variant_b_label
import com.quietmetrix.dashboard.resources.experiment_empty
import com.quietmetrix.dashboard.resources.experiment_help_content_description
import com.quietmetrix.dashboard.resources.experiment_impressions_label
import com.quietmetrix.dashboard.resources.experiment_interactions_label
import com.quietmetrix.dashboard.resources.experiment_limit_reached_message
import com.quietmetrix.dashboard.resources.experiment_card_emoji
import com.quietmetrix.dashboard.resources.experiment_new
import com.quietmetrix.dashboard.resources.experiment_none_selected
import com.quietmetrix.dashboard.resources.experiment_rate_label
import com.quietmetrix.dashboard.resources.experiment_results_baseline_badge
import com.quietmetrix.dashboard.resources.experiment_results_not_enough_data
import com.quietmetrix.dashboard.resources.experiment_results_title
import com.quietmetrix.dashboard.resources.experiment_results_unit_note
import com.quietmetrix.dashboard.resources.experiment_screen_label
import com.quietmetrix.dashboard.resources.experiment_status_active
import com.quietmetrix.dashboard.resources.experiment_status_draft
import com.quietmetrix.dashboard.resources.experiment_status_ended
import com.quietmetrix.dashboard.resources.experiment_status_finished
import com.quietmetrix.dashboard.resources.experiment_status_paused
import com.quietmetrix.dashboard.resources.experiment_status_running
import com.quietmetrix.dashboard.resources.experiment_traffic_label
import com.quietmetrix.dashboard.resources.experiment_usage_line
import com.quietmetrix.dashboard.resources.experiment_variants_split
import com.quietmetrix.dashboard.resources.experiment_verdict_no_difference
import com.quietmetrix.dashboard.resources.experiment_verdict_not_enough_data
import com.quietmetrix.dashboard.resources.experiment_verdict_winner
import com.quietmetrix.dashboard.resources.experiments_title
import com.quietmetrix.dashboard.resources.ic_experiment
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.KpiCard
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.contentStateFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The A/B testing "Experiments" tab: list + create/status-change (experiments are
 * dashboard-authored, unlike funnels — see the plan doc's rationale for why, in short: an
 * operator needs to pause/reweight an experiment without an app release), plus the selected
 * experiment's results. The first-run setup guide ([ExperimentsGuide]) is rendered by
 * [DashboardScreen] alongside this tab, driven by the same [DashboardState].
 */
@Composable
fun ExperimentsTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ExperimentDto?>(null) }

    val experiment = currentExperiment(state.experiments, state.selectedExperimentKey)
    val activeCount = state.experiments.count { it.status != "ended" }

    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.experiments_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
                WindowPicker(state.range) { viewModel.selectRange(it) }
                IconButton(onClick = { viewModel.openExperimentsGuide() }, modifier = Modifier.handCursor()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = stringResource(Res.string.experiment_help_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (state.canWriteExperiments) {
                    OutlinedButton(onClick = { showCreateDialog = true }, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.experiment_new))
                    }
                }
            }

            state.sectionErrors[DataSection.Experiments]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }
            state.sectionErrors[DataSection.ExperimentResults]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }

            usageLabelArgs(activeCount, state.experimentLimit)?.let { (count, limit) ->
                Text(
                    stringResource(Res.string.experiment_usage_line, count, limit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.experimentLimitReached) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(Res.string.experiment_limit_reached_message, state.experimentLimit ?: 0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.dismissExperimentLimitReached() }, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.action_got_it), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            when (contentStateFor(state.loading, state.experiments.isEmpty())) {
                ContentState.Loading -> {
                    ResponsiveRowOrColumn(sizeClass) {
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                    }
                }
                ContentState.Empty -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        EmptyState(Res.drawable.ic_experiment, Res.string.experiment_empty, Modifier.padding(16.dp))
                    }
                }
                ContentState.Data -> {
                    ExperimentsBody(
                        state = state,
                        experiment = experiment,
                        onSelect = { viewModel.selectExperiment(it) },
                        onAction = { action ->
                            if (experiment != null) {
                                if (action == ExperimentAction.Delete) {
                                    pendingDelete = experiment
                                } else {
                                    viewModel.changeExperimentStatus(experiment.key, action.targetStatus!!)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ExperimentCreateDialog(
            onDismiss = { showCreateDialog = false },
            onSubmit = { request ->
                viewModel.createExperiment(request)
                showCreateDialog = false
            },
        )
    }

    pendingDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.experiment_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.experiment_delete_confirm_body, e.name)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteExperiment(e.key); pendingDelete = null },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.experiment_action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.experiment_editor_cancel))
                }
            },
        )
    }
}

@Composable
private fun statusLabel(status: String) = when (status) {
    "active" -> stringResource(Res.string.experiment_status_active)
    "paused" -> stringResource(Res.string.experiment_status_paused)
    "ended" -> stringResource(Res.string.experiment_status_ended)
    else -> stringResource(Res.string.experiment_status_draft)
}

@Composable
private fun actionLabel(action: ExperimentAction) = when (action) {
    ExperimentAction.Start -> stringResource(Res.string.experiment_action_start)
    ExperimentAction.Pause -> stringResource(Res.string.experiment_action_pause)
    ExperimentAction.Resume -> stringResource(Res.string.experiment_action_resume)
    ExperimentAction.End -> stringResource(Res.string.experiment_action_end)
    ExperimentAction.Delete -> stringResource(Res.string.experiment_action_delete)
}

@Composable
private fun ExperimentDetail(
    state: DashboardState,
    experiment: ExperimentDto,
    sizeClass: WindowSizeClass,
    onAction: (ExperimentAction) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = {}, label = { Text(statusLabel(experiment.status)) })
        if (experiment.screen != null) {
            Text(
                stringResource(Res.string.experiment_screen_label, experiment.screen),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (experiment.variants.size == 2) {
            Text(
                stringResource(Res.string.experiment_variants_split, "${experiment.variants[0].name} ${experiment.variants[0].weight}", "${experiment.variants[1].name} ${experiment.variants[1].weight}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(Res.string.experiment_traffic_label, "${experiment.trafficPercent}%"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (action in availableActions(experiment.status, state.canWriteExperiments)) {
            TextButton(onClick = { onAction(action) }, modifier = Modifier.handCursor()) {
                Text(
                    actionLabel(action),
                    color = if (action == ExperimentAction.Delete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    ExperimentResultsPanel(state, sizeClass)
}

/** Width of the card-list sidebar when actual body width supports two usable panes. */
private val CARD_LIST_WIDTH = 320.dp

/**
 * The Experiments tab's two-pane body (Task 30): a scrollable [ExperimentCardList] on the
 * left drives [experiment] selection ([onSelect], which the caller wires to
 * `DashboardViewModel.selectExperiment`) for the existing [ExperimentDetail] panel on the
 * right — same selection mechanism the old `ExperimentPicker` dropdown drove, just fed by a
 * different control (README's Experiments spec: "Left: list of experiment cards ... clicking
 * selects it. Right: detail panel ..."). Measures available body width rather than using
 * the viewport class: the 320dp sidebar plus 16dp gutter requires at least 600dp for
 * usable detail content. Otherwise the panes stack. The detail KPI layout receives its
 * own measured width class, accounting for sidebar space and parent padding.
 */
@Composable
internal fun ExperimentsBody(
    state: DashboardState,
    experiment: ExperimentDto?,
    onSelect: (String) -> Unit,
    onAction: (ExperimentAction) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val split = maxWidth >= CARD_LIST_WIDTH + 16.dp + 600.dp
        val detailSizeClass = classifyWindow(if (split) maxWidth - CARD_LIST_WIDTH - 16.dp else maxWidth)
        val detail: @Composable (Modifier) -> Unit = { modifier ->
            if (experiment == null) {
                Card(modifier = modifier) {
                    EmptyState(Res.drawable.ic_experiment, Res.string.experiment_none_selected, Modifier.padding(16.dp))
                }
            } else {
                Column(modifier = modifier) {
                    ExperimentDetail(state = state, experiment = experiment, sizeClass = detailSizeClass, onAction = onAction)
                }
            }
        }

        if (split) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExperimentCardList(
                    experiments = state.experiments,
                    selectedKey = state.selectedExperimentKey,
                    onSelect = onSelect,
                    modifier = Modifier.width(CARD_LIST_WIDTH),
                )
                detail(Modifier.weight(1f))
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExperimentCardList(
                    experiments = state.experiments,
                    selectedKey = state.selectedExperimentKey,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
                detail(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The status label shown on a card-list badge, from [ExperimentCardStatus] (see its KDoc
 * for the honest 4-way status mapping this reads). Deliberately separate from [statusLabel]
 * (the existing detail-panel chip) rather than reusing it: the detail chip keeps its current
 * "Active"/"Ended" wording (out of this task's scope — see the task brief's "don't rebuild
 * the detail panel"), while the new card badge adopts the README spec's own list-badge words
 * ("Running"/"Finished"). A follow-up task could unify both onto one vocabulary if desired.
 */
@Composable
private fun cardStatusLabel(status: ExperimentCardStatus) = when (status) {
    ExperimentCardStatus.Running -> stringResource(Res.string.experiment_status_running)
    ExperimentCardStatus.Draft -> stringResource(Res.string.experiment_status_draft)
    ExperimentCardStatus.Paused -> stringResource(Res.string.experiment_status_paused)
    ExperimentCardStatus.Finished -> stringResource(Res.string.experiment_status_finished)
}

/** [PillVariant] for a card-list status badge: lime (the spec's "winning"/positive accent)
 *  only for [ExperimentCardStatus.Running], neutral otherwise — mirrors [Pill]'s own
 *  "lime = positive/success" rule, not a fabricated per-status palette. */
private fun cardStatusVariant(status: ExperimentCardStatus): PillVariant = when (status) {
    ExperimentCardStatus.Running -> PillVariant.Lime
    ExperimentCardStatus.Draft, ExperimentCardStatus.Paused, ExperimentCardStatus.Finished -> PillVariant.Neutral
}

/**
 * The card's one-line meta line: `"Screen: {screen} · Traffic: {percent}%"`, or just the
 * traffic clause when [ExperimentDto.screen] is null — reusing the exact same
 * `experiment_screen_label`/`experiment_traffic_label` strings [ExperimentDetail] already
 * renders, so the list and the detail panel never disagree about these two fields' wording.
 * Writing this composable's test surfaced a real pre-existing bug in `experiment_traffic_label`
 * (`"Traffic: %1$d%%"` rendered literally as `"Traffic: 100%%"` — the exact Compose-resources
 * "%%" unescaping bug Task 18 already found and documented for `overview_vital_trend_up`). Fixed
 * the string to take a pre-formatted `"N%"` Kotlin string (`%1$s`) instead, the same workaround
 * Task 18 used — this is a one-line string-resource fix at both of its call sites (here and
 * [ExperimentDetail]), not a rebuild of the detail panel.
 */
@Composable
private fun cardMetaLine(experiment: ExperimentDto): String {
    val traffic = stringResource(Res.string.experiment_traffic_label, "${experiment.trafficPercent}%")
    val screen = experiment.screen
    return if (screen != null) stringResource(Res.string.experiment_screen_label, screen) + " · " + traffic else traffic
}

/**
 * A scrollable list of [ExperimentCard]s (README's Experiments spec: "Left: list of
 * experiment cards (emoji, name, status badge ... meta line); clicking selects it"). Not
 * independently scrollable (no nested `verticalScroll`) — it relies on `ExperimentsTab`'s
 * own page-level scroll, the same idiom [FunnelStepList] uses, since Compose doesn't support
 * nesting two same-axis scrollables without an explicit bounded height neither this list nor
 * its sibling detail panel actually needs.
 */
@Composable
internal fun ExperimentCardList(
    experiments: List<ExperimentDto>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (e in experiments) {
            ExperimentCard(experiment = e, selected = e.key == selectedKey, onClick = { onSelect(e.key) })
        }
    }
}

/**
 * One row in [ExperimentCardList]: a fixed 🧪 emoji (no per-experiment emoji field exists on
 * [ExperimentDto] — same "no fabricated icon" call Task 28 made for `FunnelStepRow`'s
 * ordinal-instead-of-icon), the experiment's name, a status [Pill] ([cardStatusLabel]/
 * [cardStatusVariant]), and [cardMetaLine]. Copies the hover/press/selection idiom
 * [KpiCard]'s KDoc names as the one later clickable components (explicitly "experiment
 * cards") should reuse: a single [MutableInteractionSource] drives manual press-scale and
 * hover-brighten, while `Card(onClick = ...)`'s own elevation animation is left to Material3.
 * [selected] additionally swaps the hairline border for a 2dp lime one, so the chosen card is
 * identifiable without relying on color/elevation alone.
 */
@Composable
private fun ExperimentCard(experiment: ExperimentDto, selected: Boolean, onClick: () -> Unit) {
    val extended = LocalExtendedColors.current
    val cardStatus = cardStatusFor(experiment.status)
    val restingContainer = MaterialTheme.colorScheme.surface

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "experimentCardScale")
    val containerColor by animateColorAsState(
        if (hovered) lerp(restingContainer, Color.White, 0.12f) else restingContainer,
        label = "experimentCardBrighten",
    )
    val border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) extended.lime else extended.hairline)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale).handCursor(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        interactionSource = interactionSource,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.experiment_card_emoji), style = MaterialTheme.typography.titleMedium)
                Text(
                    experiment.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Pill(text = cardStatusLabel(cardStatus), variant = cardStatusVariant(cardStatus))
            }
            Text(
                cardMetaLine(experiment),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExperimentCreateDialog(onDismiss: () -> Unit, onSubmit: (CreateExperimentRequest) -> Unit) {
    var key by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("") }
    var variantA by remember { mutableStateOf("a") }
    var variantB by remember { mutableStateOf("b") }
    var splitText by remember { mutableStateOf("50") }
    var trafficText by remember { mutableStateOf("100") }
    var countriesText by remember { mutableStateOf("") }

    val split = splitText.toIntOrNull() ?: -1
    val traffic = trafficText.toIntOrNull() ?: -1
    val countryParse = parseCountryList(countriesText)
    val valid = isExperimentFormValid(key, name, variantA, variantB, split, traffic, countriesText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.experiment_editor_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text(stringResource(Res.string.experiment_editor_key_label)) },
                    placeholder = { Text(stringResource(Res.string.experiment_editor_key_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.experiment_editor_name_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = screen, onValueChange = { screen = it },
                    label = { Text(stringResource(Res.string.experiment_editor_screen_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = variantA, onValueChange = { variantA = it },
                        label = { Text(stringResource(Res.string.experiment_editor_variant_a_label)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = variantB, onValueChange = { variantB = it },
                        label = { Text(stringResource(Res.string.experiment_editor_variant_b_label)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = splitText, onValueChange = { splitText = it },
                        label = { Text(stringResource(Res.string.experiment_editor_split_label)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = trafficText, onValueChange = { trafficText = it },
                        label = { Text(stringResource(Res.string.experiment_editor_traffic_label)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = countriesText, onValueChange = { countriesText = it },
                    label = { Text(stringResource(Res.string.experiment_editor_countries_label)) },
                    placeholder = { Text(stringResource(Res.string.experiment_editor_countries_hint)) },
                    isError = countryParse.invalidTokens.isNotEmpty(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (countryParse.invalidTokens.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.experiment_editor_countries_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        CreateExperimentRequest(
                            key = key.trim(),
                            name = name.trim(),
                            screen = screen.trim().takeIf { it.isNotEmpty() },
                            trafficPercent = traffic,
                            variants = listOf(VariantDto(variantA.trim(), split), VariantDto(variantB.trim(), 100 - split)),
                            countries = countryParse.normalized.takeIf { it.isNotEmpty() },
                        ),
                    )
                },
                enabled = valid,
                modifier = Modifier.handCursor(),
            ) { Text(stringResource(Res.string.experiment_editor_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.experiment_editor_cancel))
            }
        },
    )
}

@Composable
private fun ExperimentResultsPanel(state: DashboardState, sizeClass: WindowSizeClass) {
    val results = state.experimentResults

    ResponsiveRowOrColumn(sizeClass) {
        when (contentStateFor(state.loading, results == null)) {
            ContentState.Loading -> {
                KpiSkeleton(Modifier.fillSlot())
                KpiSkeleton(Modifier.fillSlot())
            }
            else -> {
                results?.variants?.forEach { v ->
                    val interval = if (v.impressions != null && v.interactions != null) {
                        wilsonInterval(v.interactions, v.impressions)
                    } else {
                        null
                    }
                    KpiCard(
                        label = v.name,
                        value = v.rate?.let { formatPercent(it) } ?: stringResource(Res.string.experiment_results_not_enough_data),
                        modifier = Modifier.fillSlot(),
                        sub = interval?.let { formatWilsonRange(it) },
                    )
                }
            }
        }
    }

    if (results != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.experiment_results_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                VerdictBanner(verdictFor(results.comparison))

                results.variants.forEachIndexed { index, v ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(v.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        if (index == 0) {
                            AssistChip(onClick = {}, label = { Text(stringResource(Res.string.experiment_results_baseline_badge)) })
                        }
                        Column(modifier = Modifier.weight(0.6f)) {
                            Text(
                                stringResource(Res.string.experiment_impressions_label) + ": " + (v.impressions?.let { formatCount(it) } ?: stringResource(Res.string.experiment_results_not_enough_data)),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                stringResource(Res.string.experiment_interactions_label) + ": " + (v.interactions?.let { formatCount(it) } ?: stringResource(Res.string.experiment_results_not_enough_data)),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Text(
                    stringResource(Res.string.experiment_results_unit_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VerdictBanner(verdict: ExperimentVerdict) {
    val text = when (verdict) {
        is ExperimentVerdict.NotEnoughData -> stringResource(Res.string.experiment_verdict_not_enough_data)
        is ExperimentVerdict.NoDifference -> stringResource(Res.string.experiment_verdict_no_difference, formatPValue(verdict.pValue))
        is ExperimentVerdict.Winner -> stringResource(Res.string.experiment_verdict_winner, verdict.variantName, formatPValue(verdict.pValue))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (verdict is ExperimentVerdict.Winner) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
