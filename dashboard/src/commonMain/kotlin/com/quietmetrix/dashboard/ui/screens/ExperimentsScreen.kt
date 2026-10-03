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
import androidx.compose.material3.Button
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
import com.quietmetrix.dashboard.resources.*
import com.quietmetrix.dashboard.ui.components.ConfirmDialog
import com.quietmetrix.dashboard.ui.components.ModalShell
import com.quietmetrix.dashboard.ui.components.ModalFooter
import com.quietmetrix.dashboard.ui.components.ModalFooterAction
import com.quietmetrix.dashboard.ui.components.ModalFooterEmphasis
import com.quietmetrix.dashboard.ui.components.CodeSnippetPanel
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
    var pendingAction by remember { mutableStateOf<PendingExperimentAction?>(null) }

    val experiment = currentExperiment(state.experiments, state.selectedExperimentKey)
    val explainQuestion = experiment?.let { stringResource(Res.string.experiment_explain_question, it.name) }
    val activeCount = state.experiments.count { it.status in setOf("draft", "active", "paused") }

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
                        onExplain = { viewModel.openAskDrawer(explainQuestion) },
                        onAction = { action ->
                            if (experiment != null) {
                                if (action in setOf(ExperimentAction.Start, ExperimentAction.Resume)) {
                                    viewModel.changeExperimentStatus(experiment.key, action.targetStatus!!)
                                } else {
                                    pendingAction = PendingExperimentAction(experiment, action,
                                        selectedVariantFor(action, experiment, currentExperimentResults(state, experiment)?.comparison))
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

    pendingAction?.let { pending ->
        ExperimentActionConfirmation(pending, onDismiss = { pendingAction = null }) {
            if (pending.action == ExperimentAction.Delete) viewModel.deleteExperiment(pending.experiment.key)
            else viewModel.changeExperimentStatus(pending.experiment.key, pending.action.targetStatus!!, pending.selectedVariant)
            pendingAction = null
        }
    }
}

/** Immutable action snapshot prevents a live result refresh from changing the confirmed choice. */
internal data class PendingExperimentAction(val experiment: ExperimentDto, val action: ExperimentAction, val selectedVariant: String?)

/** Shared consequence dialog for pause, finish, close, rollout and deletion. */
@Composable
internal fun ExperimentActionConfirmation(pending: PendingExperimentAction, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val action = pending.action
    val label = actionLabel(action)
    ConfirmDialog(
        emoji = if (action == ExperimentAction.Ship) "🏆" else "🧪",
        title = stringResource(Res.string.experiment_confirm_title, label, pending.experiment.name),
        body = when {
            action == ExperimentAction.Delete -> stringResource(Res.string.experiment_delete_confirm_body, pending.experiment.name)
            pending.selectedVariant != null -> stringResource(Res.string.experiment_confirm_rollout, pending.selectedVariant)
            else -> stringResource(Res.string.experiment_confirm_stop)
        },
        confirmLabel = label,
        danger = action in setOf(ExperimentAction.Delete, ExperimentAction.End, ExperimentAction.Close),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Localized persisted lifecycle state. */
@Composable
private fun statusLabel(status: String) = cardStatusLabel(cardStatusFor(status))

/** Localized operator action. */
@Composable
private fun actionLabel(action: ExperimentAction) = when (action) {
    ExperimentAction.Start -> stringResource(Res.string.experiment_action_start)
    ExperimentAction.Pause -> stringResource(Res.string.experiment_action_pause)
    ExperimentAction.Resume -> stringResource(Res.string.experiment_action_resume)
    ExperimentAction.End -> stringResource(Res.string.experiment_action_end)
    ExperimentAction.Delete -> stringResource(Res.string.experiment_action_delete)
    ExperimentAction.Ship -> stringResource(Res.string.experiment_action_ship)
    ExperimentAction.KeepBaseline -> stringResource(Res.string.experiment_action_keep)
    ExperimentAction.Close -> stringResource(Res.string.experiment_action_close)
}

/** Responsive metadata, status-aware primary action and result detail. */
@Composable
private fun ExperimentDetail(
    state: DashboardState,
    experiment: ExperimentDto,
    sizeClass: WindowSizeClass,
    onAction: (ExperimentAction) -> Unit,
    onExplain: () -> Unit = {},
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
        for ((index, action) in detailActions(experiment, currentExperimentResults(state, experiment)?.comparison, state.canWriteExperiments).withIndex()) {
            if (index == 0) {
                Button(onClick = { onAction(action) }, modifier = Modifier.handCursor()) { Text(actionLabel(action)) }
                continue
            }
            TextButton(onClick = { onAction(action) }, modifier = Modifier.handCursor()) {
                Text(
                    actionLabel(action),
                    color = if (action == ExperimentAction.Delete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    ExperimentResultsPanel(state, experiment, sizeClass)
    CodeSnippetPanel(experiment = experiment, onExplain = onExplain)
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
    onExplain: () -> Unit = {},
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
                    ExperimentDetail(state = state, experiment = experiment, sizeClass = detailSizeClass, onAction = onAction, onExplain = onExplain)
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

/** Shared localized status vocabulary for list and detail panels. */
@Composable
private fun cardStatusLabel(status: ExperimentCardStatus) = when (status) {
    ExperimentCardStatus.Running -> stringResource(Res.string.experiment_status_running)
    ExperimentCardStatus.Draft -> stringResource(Res.string.experiment_status_draft)
    ExperimentCardStatus.Paused -> stringResource(Res.string.experiment_status_paused)
    ExperimentCardStatus.Finished -> stringResource(Res.string.experiment_status_finished)
    ExperimentCardStatus.Shipped -> stringResource(Res.string.experiment_status_shipped)
    ExperimentCardStatus.Closed -> stringResource(Res.string.experiment_status_closed)
}

/** [PillVariant] for a card-list status badge: lime (the spec's "winning"/positive accent)
 *  only for [ExperimentCardStatus.Running], neutral otherwise — mirrors [Pill]'s own
 *  "lime = positive/success" rule, not a fabricated per-status palette. */
private fun cardStatusVariant(status: ExperimentCardStatus): PillVariant = when (status) {
    ExperimentCardStatus.Running -> PillVariant.Lime
    ExperimentCardStatus.Draft, ExperimentCardStatus.Paused, ExperimentCardStatus.Finished, ExperimentCardStatus.Shipped, ExperimentCardStatus.Closed -> PillVariant.Neutral
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
    val traffic = stringResource(Res.string.experiment_traffic_label, "${if (experiment.status == "shipped") 100 else experiment.trafficPercent}%")
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
    var step by remember { mutableStateOf(0) }
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
    val basicsValid = key.isNotBlank() && name.isNotBlank()

    ModalShell(
        title = stringResource(Res.string.experiment_editor_title),
        onDismiss = onDismiss,
        footer = {
            ModalFooter(
                leading = if (step > 0) ModalFooterAction(stringResource(Res.string.experiment_wizard_back), ModalFooterEmphasis.Secondary) { step-- } else null,
                secondary = ModalFooterAction(stringResource(Res.string.experiment_editor_cancel), ModalFooterEmphasis.Secondary, onDismiss),
                primary = ModalFooterAction(
                    label = stringResource(if (step == 2) Res.string.experiment_editor_submit else Res.string.experiment_wizard_next),
                    emphasis = ModalFooterEmphasis.Primary,
                    onClick = {
                        if ((step == 0 && !basicsValid) || (step > 0 && !valid)) return@ModalFooterAction
                        if (step < 2) step++ else onSubmit(CreateExperimentRequest(
                            key = key.trim(), name = name.trim(), screen = screen.trim().takeIf { it.isNotEmpty() },
                            trafficPercent = traffic,
                            variants = listOf(VariantDto(variantA.trim(), split), VariantDto(variantB.trim(), 100 - split)),
                            countries = countryParse.normalized.takeIf { it.isNotEmpty() },
                        ))
                    },
                ),
            )
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.experiment_wizard_step, step + 1), style = MaterialTheme.typography.labelMedium)
            when (step) {
                0 -> {
                    OutlinedTextField(key, { key = it }, label = { Text(stringResource(Res.string.experiment_editor_key_label)) }, placeholder = { Text(stringResource(Res.string.experiment_editor_key_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.experiment_editor_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(screen, { screen = it }, label = { Text(stringResource(Res.string.experiment_editor_screen_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                1 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(variantA, { variantA = it }, label = { Text(stringResource(Res.string.experiment_editor_variant_a_label)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(variantB, { variantB = it }, label = { Text(stringResource(Res.string.experiment_editor_variant_b_label)) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(splitText, { splitText = it }, label = { Text(stringResource(Res.string.experiment_editor_split_label)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(trafficText, { trafficText = it }, label = { Text(stringResource(Res.string.experiment_editor_traffic_label)) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(countriesText, { countriesText = it }, label = { Text(stringResource(Res.string.experiment_editor_countries_label)) }, placeholder = { Text(stringResource(Res.string.experiment_editor_countries_hint)) }, isError = countryParse.invalidTokens.isNotEmpty(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (countryParse.invalidTokens.isNotEmpty()) Text(stringResource(Res.string.experiment_editor_countries_invalid), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Text(experimentWizardSummary(name.trim(), key.trim(), variantA.trim(), variantB.trim(), split, traffic), style = MaterialTheme.typography.bodyLarge)
                    if (screen.isNotBlank()) Text(stringResource(Res.string.experiment_wizard_screen_summary, screen.trim()), style = MaterialTheme.typography.bodyMedium)
                    if (countryParse.normalized.isNotEmpty()) Text(stringResource(Res.string.experiment_wizard_countries_summary, countryParse.normalized.joinToString()), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
