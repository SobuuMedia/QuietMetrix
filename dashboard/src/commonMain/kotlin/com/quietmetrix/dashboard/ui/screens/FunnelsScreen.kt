package com.quietmetrix.dashboard.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelStepDto
import com.quietmetrix.dashboard.api.FunnelStepResultDto
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatDuration
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.col_conversion_from_entry
import com.quietmetrix.dashboard.resources.col_conversion_from_previous
import com.quietmetrix.dashboard.resources.col_count
import com.quietmetrix.dashboard.resources.col_dropped
import com.quietmetrix.dashboard.resources.col_median_time
import com.quietmetrix.dashboard.resources.col_step
import com.quietmetrix.dashboard.resources.funnel_badge_locked
import com.quietmetrix.dashboard.resources.funnel_badge_sdk
import com.quietmetrix.dashboard.resources.funnel_breakdown_country
import com.quietmetrix.dashboard.resources.funnel_breakdown_device
import com.quietmetrix.dashboard.resources.funnel_breakdown_language
import com.quietmetrix.dashboard.resources.funnel_breakdown_none
import com.quietmetrix.dashboard.resources.funnel_breakdown_platform
import com.quietmetrix.dashboard.resources.funnel_card_breakdown
import com.quietmetrix.dashboard.resources.funnel_card_steps
import com.quietmetrix.dashboard.resources.funnel_card_trend
import com.quietmetrix.dashboard.resources.funnel_create_action
import com.quietmetrix.dashboard.resources.funnel_delete
import com.quietmetrix.dashboard.resources.funnel_delete_confirm_body
import com.quietmetrix.dashboard.resources.funnel_delete_confirm_title
import com.quietmetrix.dashboard.resources.funnel_edit
import com.quietmetrix.dashboard.resources.funnel_editor_cancel
import com.quietmetrix.dashboard.resources.funnel_editor_key_label
import com.quietmetrix.dashboard.resources.funnel_editor_name_label
import com.quietmetrix.dashboard.resources.funnel_editor_remove_step
import com.quietmetrix.dashboard.resources.funnel_editor_save
import com.quietmetrix.dashboard.resources.funnel_editor_step_add
import com.quietmetrix.dashboard.resources.funnel_editor_step_event_label
import com.quietmetrix.dashboard.resources.funnel_editor_step_screen_label
import com.quietmetrix.dashboard.resources.funnel_editor_title_create
import com.quietmetrix.dashboard.resources.funnel_editor_title_edit
import com.quietmetrix.dashboard.resources.funnel_editor_window_label
import com.quietmetrix.dashboard.resources.funnel_empty
import com.quietmetrix.dashboard.resources.funnel_kpi_converted
import com.quietmetrix.dashboard.resources.funnel_kpi_entered
import com.quietmetrix.dashboard.resources.funnel_kpi_median_time
import com.quietmetrix.dashboard.resources.funnel_kpi_overall_conversion
import com.quietmetrix.dashboard.resources.funnel_none_selected
import com.quietmetrix.dashboard.resources.funnel_pick_funnel
import com.quietmetrix.dashboard.resources.funnel_select_breakdown
import com.quietmetrix.dashboard.resources.funnel_truncated_warning
import com.quietmetrix.dashboard.resources.funnel_tutorial_cta
import com.quietmetrix.dashboard.resources.funnel_tutorial_intro
import com.quietmetrix.dashboard.resources.funnel_tutorial_step1
import com.quietmetrix.dashboard.resources.funnel_tutorial_step2
import com.quietmetrix.dashboard.resources.funnel_tutorial_step3
import com.quietmetrix.dashboard.resources.funnel_tutorial_title
import com.quietmetrix.dashboard.resources.funnels_title
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_funnel
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.KpiCard
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.charts.FunnelChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.FunnelStepData
import com.quietmetrix.dashboard.ui.components.charts.LineChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.LineData
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

private val BREAKDOWN_DIMENSIONS = listOf("country", "platform", "device_class", "language")

@Composable
private fun breakdownLabel(dimension: String?): String = when (dimension) {
    "country" -> stringResource(Res.string.funnel_breakdown_country)
    "platform" -> stringResource(Res.string.funnel_breakdown_platform)
    "device_class" -> stringResource(Res.string.funnel_breakdown_device)
    "language" -> stringResource(Res.string.funnel_breakdown_language)
    else -> stringResource(Res.string.funnel_breakdown_none)
}

@Composable
fun FunnelsTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    var showEditor by remember { mutableStateOf(false) }
    var editingFunnel by remember { mutableStateOf<FunnelDto?>(null) }
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
                FunnelPicker(state.funnels, state.selectedFunnelKey) { viewModel.selectFunnel(it) }
                WindowPicker(state.range) { viewModel.selectRange(it) }
                Button(
                    onClick = { editingFunnel = null; showEditor = true },
                    enabled = !state.isReadOnly,
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.funnel_create_action))
                }
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
                        KpiSkeleton(Modifier.fillSlot())
                    }
                }
                ContentState.Empty -> {
                    FunnelsGettingStarted(
                        canCreate = !state.isReadOnly,
                        onCreateClick = { editingFunnel = null; showEditor = true },
                    )
                }
                ContentState.Data -> {
                    if (funnel == null) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            EmptyState(Res.drawable.ic_funnel, Res.string.funnel_none_selected, Modifier.padding(16.dp))
                        }
                    } else {
                        FunnelDetail(
                            viewModel = viewModel,
                            state = state,
                            funnel = funnel,
                            sizeClass = sizeClass,
                            onEdit = { editingFunnel = funnel; showEditor = true },
                            onDelete = { pendingDelete = funnel },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        FunnelEditorDialog(
            existing = editingFunnel,
            onDismiss = { showEditor = false },
            onSave = { key, name, steps, windowSeconds ->
                if (editingFunnel == null) {
                    viewModel.createFunnel(key, name, steps, windowSeconds)
                } else {
                    viewModel.updateFunnel(key, name, steps, windowSeconds)
                }
                showEditor = false
            },
        )
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

/** Shown instead of the plain empty state when a project has no funnels yet — walks a
 *  first-time user through what a funnel is and how to create one. */
@Composable
private fun FunnelsGettingStarted(canCreate: Boolean, onCreateClick: () -> Unit) {
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
                FunnelTutorialStep(3, stringResource(Res.string.funnel_tutorial_step3))
            }
            Button(onClick = onCreateClick, enabled = canCreate, modifier = Modifier.handCursor()) {
                Icon(painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.funnel_tutorial_cta))
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
    viewModel: DashboardViewModel,
    state: DashboardState,
    funnel: FunnelDto,
    sizeClass: WindowSizeClass,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (funnel.source == "sdk") {
            AssistChip(onClick = {}, label = { Text(stringResource(Res.string.funnel_badge_sdk)) })
        }
        if (funnel.locked) {
            AssistChip(onClick = {}, label = { Text(stringResource(Res.string.funnel_badge_locked)) })
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onEdit, enabled = !state.isReadOnly && funnel.source != "sdk", modifier = Modifier.handCursor()) {
            Text(stringResource(Res.string.funnel_edit))
        }
        TextButton(onClick = onDelete, enabled = !state.isReadOnly, modifier = Modifier.handCursor()) {
            Text(stringResource(Res.string.funnel_delete), color = MaterialTheme.colorScheme.error)
        }
    }

    val results = state.funnelResults
    ResponsiveRowOrColumn(sizeClass) {
        when (contentStateFor(state.loading, results == null)) {
            ContentState.Loading -> {
                KpiSkeleton(Modifier.fillSlot())
                KpiSkeleton(Modifier.fillSlot())
                KpiSkeleton(Modifier.fillSlot())
                KpiSkeleton(Modifier.fillSlot())
            }
            else -> {
                KpiCard(stringResource(Res.string.funnel_kpi_entered), formatCount((results?.entered ?: 0).toLong()), Modifier.fillSlot())
                KpiCard(stringResource(Res.string.funnel_kpi_converted), formatCount((results?.converted ?: 0).toLong()), Modifier.fillSlot())
                KpiCard(
                    stringResource(Res.string.funnel_kpi_overall_conversion),
                    formatPercent(results?.overallConversion ?: 0.0),
                    Modifier.fillSlot(),
                )
                KpiCard(
                    stringResource(Res.string.funnel_kpi_median_time),
                    results?.medianTotalMs?.let { formatDuration((it / 1000).toInt()) } ?: "—",
                    Modifier.fillSlot(),
                )
            }
        }
    }

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
                    Text("No entrants in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FunnelChartCanvas(results.steps.map { FunnelStepData(it.name ?: it.key, it.count) })
                }
                Spacer(Modifier.height(8.dp))
                DataTable(
                    columns = funnelStepColumns(),
                    rows = results.steps,
                    sizeClass = sizeClass,
                    modifier = Modifier.fillMaxWidth().height((results.steps.size * 56 + 56).dp),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.funnel_card_breakdown),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    BreakdownPicker(state.funnelBreakdownDimension) { viewModel.selectFunnelBreakdown(it) }
                }
                Spacer(Modifier.height(8.dp))
                val breakdown = results.breakdown
                if (breakdown == null) {
                    Text(
                        stringResource(Res.string.funnel_select_breakdown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    breakdown.values.forEach { value ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(value.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(formatCount(value.entered.toLong()), style = MaterialTheme.typography.bodySmall)
                            Text(formatPercent(value.overallConversion), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(Res.string.funnel_card_trend),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val trend = results.trend.orEmpty()
                if (trend.isEmpty()) {
                    EmptyState(Res.drawable.ic_funnel, Res.string.funnel_empty)
                } else {
                    LineChartCanvas(trend.map { LineData(it.bucket, (it.conversion * 100).toFloat()) })
                }
            }
        }

        if (results.truncated) {
            Text(
                stringResource(Res.string.funnel_truncated_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun funnelStepColumns(): List<TableColumn<FunnelStepResultDto>> = listOf(
    TableColumn(stringResource(Res.string.col_step), 1.4f, { Text(it.name ?: it.key) }),
    TableColumn(stringResource(Res.string.col_count), 0.8f, { Text(formatCount(it.count.toLong())) }),
    TableColumn(
        stringResource(Res.string.col_conversion_from_entry), 1f,
        { Text(formatPercent(it.conversionFromEntry)) }, hideOnCompact = true,
    ),
    TableColumn(
        stringResource(Res.string.col_conversion_from_previous), 1f,
        { Text(if (it.medianMsFromPrevious == null) "—" else formatPercent(it.conversionFromPrevious)) }, hideOnCompact = true,
    ),
    TableColumn(stringResource(Res.string.col_dropped), 0.8f, { Text(formatCount(it.dropped.toLong())) }),
    TableColumn(
        stringResource(Res.string.col_median_time), 1f,
        { step -> Text(step.medianMsFromPrevious?.let { formatDuration((it / 1000).toInt()) } ?: "—") },
        hideOnCompact = true,
    ),
)

@Composable
private fun FunnelPicker(funnels: List<FunnelDto>, currentKey: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = funnels.firstOrNull { it.funnelKey == currentKey }
    OutlinedButton(onClick = { expanded = true }, enabled = funnels.isNotEmpty(), modifier = Modifier.handCursor()) {
        Text(current?.name ?: stringResource(Res.string.funnel_pick_funnel))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        funnels.forEach { f ->
            DropdownMenuItem(
                text = { Text(f.name) },
                onClick = { onSelect(f.funnelKey); expanded = false },
                modifier = Modifier.handCursor(),
            )
        }
    }
}

@Composable
private fun BreakdownPicker(current: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.handCursor()) {
        Text(breakdownLabel(current))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(breakdownLabel(null)) },
            onClick = { onSelect(null); expanded = false },
            modifier = Modifier.handCursor(),
        )
        BREAKDOWN_DIMENSIONS.forEach { dim ->
            DropdownMenuItem(
                text = { Text(breakdownLabel(dim)) },
                onClick = { onSelect(dim); expanded = false },
                modifier = Modifier.handCursor(),
            )
        }
    }
}

private data class FunnelStepFormRow(val event: String, val screen: String)

@Composable
private fun FunnelEditorDialog(
    existing: FunnelDto?,
    onDismiss: () -> Unit,
    onSave: (funnelKey: String, name: String, steps: List<FunnelStepDto>, windowSeconds: Long) -> Unit,
) {
    var funnelKey by remember { mutableStateOf(existing?.funnelKey ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var windowSeconds by remember { mutableStateOf(existing?.windowSeconds ?: TimeRange.Default.seconds) }
    var steps by remember {
        mutableStateOf(
            existing?.steps?.map { FunnelStepFormRow(it.event, it.screen ?: "") }
                ?: listOf(FunnelStepFormRow("", ""), FunnelStepFormRow("", "")),
        )
    }
    val isEditing = existing != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEditing) Res.string.funnel_editor_title_edit else Res.string.funnel_editor_title_create)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = funnelKey,
                    onValueChange = { if (!isEditing) funnelKey = it.trim() },
                    label = { Text(stringResource(Res.string.funnel_editor_key_label)) },
                    singleLine = true,
                    enabled = !isEditing,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.funnel_editor_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.funnel_editor_window_label), style = MaterialTheme.typography.labelLarge)
                    FunnelWindowPicker(windowSeconds) { windowSeconds = it }
                }

                HorizontalDivider()

                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = step.event,
                                onValueChange = { v -> steps = steps.toMutableList().also { it[index] = step.copy(event = v.trim()) } },
                                label = { Text(stringResource(Res.string.funnel_editor_step_event_label, index + 1)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = step.screen,
                                onValueChange = { v -> steps = steps.toMutableList().also { it[index] = step.copy(screen = v.trim()) } },
                                label = { Text(stringResource(Res.string.funnel_editor_step_screen_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconButton(
                            onClick = { steps = steps.toMutableList().also { it.removeAt(index) } },
                            enabled = steps.size > 2,
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_delete),
                                contentDescription = stringResource(Res.string.funnel_editor_remove_step),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { steps = steps + FunnelStepFormRow("", "") },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.funnel_editor_step_add))
                }
            }
        },
        confirmButton = {
            val valid = isFunnelFormValid(funnelKey, name, steps.map { it.event })
            TextButton(
                enabled = valid,
                modifier = Modifier.handCursor(),
                onClick = {
                    val dtos = steps.mapIndexed { i, s ->
                        FunnelStepDto(key = "step${i + 1}", event = s.event, screen = s.screen.ifBlank { null })
                    }
                    onSave(funnelKey, name, dtos, windowSeconds)
                },
            ) { Text(stringResource(Res.string.funnel_editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.funnel_editor_cancel))
            }
        },
    )
}

@Composable
private fun FunnelWindowPicker(seconds: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = TimeRange.entries.firstOrNull { it.seconds == seconds } ?: TimeRange.Default
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.handCursor()) {
        Text(stringResource(current.labelRes))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        TimeRange.entries.forEach { r ->
            DropdownMenuItem(
                text = { Text(stringResource(r.labelRes)) },
                onClick = { onSelect(r.seconds); expanded = false },
                modifier = Modifier.handCursor(),
            )
        }
    }
}
