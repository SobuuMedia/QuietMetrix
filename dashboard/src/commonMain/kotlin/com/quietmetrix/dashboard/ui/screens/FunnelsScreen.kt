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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.FunnelDto
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
import com.quietmetrix.dashboard.resources.funnel_kpi_converted
import com.quietmetrix.dashboard.resources.funnel_kpi_entered
import com.quietmetrix.dashboard.resources.funnel_kpi_overall_conversion
import com.quietmetrix.dashboard.resources.funnel_none_selected
import com.quietmetrix.dashboard.resources.funnel_pick_funnel
import com.quietmetrix.dashboard.resources.funnel_tutorial_intro
import com.quietmetrix.dashboard.resources.funnel_tutorial_step1
import com.quietmetrix.dashboard.resources.funnel_tutorial_step2
import com.quietmetrix.dashboard.resources.funnel_tutorial_title
import com.quietmetrix.dashboard.resources.funnels_title
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_funnel
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.KpiCard
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
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
 * not a source of truth for funnel steps any more, so there is no create/edit UI here.
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
                FunnelPicker(state.funnels, state.selectedFunnelKey) { viewModel.selectFunnel(it) }
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
                            funnel = funnel,
                            sizeClass = sizeClass,
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
    funnel: FunnelDto,
    sizeClass: WindowSizeClass,
    onDelete: () -> Unit,
) {
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
    ResponsiveRowOrColumn(sizeClass) {
        when (contentStateFor(state.loading, results == null)) {
            ContentState.Loading -> {
                KpiSkeleton(Modifier.fillSlot())
                KpiSkeleton(Modifier.fillSlot())
                KpiSkeleton(Modifier.fillSlot())
            }
            else -> {
                KpiCard(stringResource(Res.string.funnel_kpi_entered), formatCount((results?.entered ?: 0).toLong()), Modifier.fillSlot())
                KpiCard(stringResource(Res.string.funnel_kpi_converted), formatCount((results?.converted ?: 0).toLong()), Modifier.fillSlot())
                val overallInterval = results?.let { wilsonInterval(it.converted.toLong(), it.entered.toLong()) }
                KpiCard(
                    stringResource(Res.string.funnel_kpi_overall_conversion),
                    formatPercent(results?.overallConversion ?: 0.0),
                    Modifier.fillSlot(),
                    sub = overallInterval?.let { formatWilsonRange(it) },
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

