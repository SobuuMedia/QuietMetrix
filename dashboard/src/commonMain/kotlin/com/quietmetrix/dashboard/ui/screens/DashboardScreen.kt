package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.EventRow
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.TopEvent
import com.quietmetrix.dashboard.api.TopScreen
import com.quietmetrix.dashboard.api.Transition
import com.quietmetrix.dashboard.format.formatBucketLabel
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatDateOnly
import com.quietmetrix.dashboard.format.formatDuration
import com.quietmetrix.dashboard.format.formatFloat
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_copy_api_key
import com.quietmetrix.dashboard.resources.action_got_it
import com.quietmetrix.dashboard.resources.action_regenerate
import com.quietmetrix.dashboard.resources.api_key_label
import com.quietmetrix.dashboard.resources.badge_demo
import com.quietmetrix.dashboard.resources.badge_offline
import com.quietmetrix.dashboard.resources.card_countries
import com.quietmetrix.dashboard.resources.card_daily_volume
import com.quietmetrix.dashboard.resources.card_device_classes
import com.quietmetrix.dashboard.resources.card_platforms
import com.quietmetrix.dashboard.resources.card_sessions_trend
import com.quietmetrix.dashboard.resources.card_top_events
import com.quietmetrix.dashboard.resources.card_top_screens
import com.quietmetrix.dashboard.resources.col_count
import com.quietmetrix.dashboard.resources.col_country
import com.quietmetrix.dashboard.resources.col_event
import com.quietmetrix.dashboard.resources.col_from
import com.quietmetrix.dashboard.resources.col_platform
import com.quietmetrix.dashboard.resources.col_screen
import com.quietmetrix.dashboard.resources.col_time
import com.quietmetrix.dashboard.resources.col_to
import com.quietmetrix.dashboard.resources.create_app_action
import com.quietmetrix.dashboard.resources.create_project_button
import com.quietmetrix.dashboard.resources.create_project_cancel
import com.quietmetrix.dashboard.resources.create_project_description_label
import com.quietmetrix.dashboard.resources.create_project_dialog_title
import com.quietmetrix.dashboard.resources.create_project_name_label
import com.quietmetrix.dashboard.resources.event_meta_country
import com.quietmetrix.dashboard.resources.event_meta_platform
import com.quietmetrix.dashboard.resources.event_meta_screen
import com.quietmetrix.dashboard.resources.event_meta_session
import com.quietmetrix.dashboard.resources.ic_copy
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_events
import com.quietmetrix.dashboard.resources.ic_flow
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_live
import com.quietmetrix.dashboard.resources.ic_overview
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.ic_projects
import com.quietmetrix.dashboard.resources.ic_refresh
import com.quietmetrix.dashboard.resources.ic_search
import com.quietmetrix.dashboard.resources.invite_link_body
import com.quietmetrix.dashboard.resources.invite_link_title
import com.quietmetrix.dashboard.resources.kpi_avg_duration
import com.quietmetrix.dashboard.resources.kpi_errors
import com.quietmetrix.dashboard.resources.kpi_events
import com.quietmetrix.dashboard.resources.kpi_events_per_session
import com.quietmetrix.dashboard.resources.kpi_offline_captured
import com.quietmetrix.dashboard.resources.kpi_sessions
import com.quietmetrix.dashboard.resources.page_info
import com.quietmetrix.dashboard.resources.page_next
import com.quietmetrix.dashboard.resources.page_prev
import com.quietmetrix.dashboard.resources.picker_no_projects
import com.quietmetrix.dashboard.resources.picker_select_project
import com.quietmetrix.dashboard.resources.project_created_api_key_label
import com.quietmetrix.dashboard.resources.project_created_key_warning
import com.quietmetrix.dashboard.resources.project_created_title
import com.quietmetrix.dashboard.resources.projects_created
import com.quietmetrix.dashboard.resources.projects_delete
import com.quietmetrix.dashboard.resources.projects_delete_confirm_body
import com.quietmetrix.dashboard.resources.projects_delete_confirm_title
import com.quietmetrix.dashboard.resources.projects_your
import com.quietmetrix.dashboard.resources.regenerate_confirm_body
import com.quietmetrix.dashboard.resources.regenerate_confirm_title
import com.quietmetrix.dashboard.resources.regenerated_key_title
import com.quietmetrix.dashboard.resources.search_events
import com.quietmetrix.dashboard.resources.server_mode_debug
import com.quietmetrix.dashboard.resources.server_mode_production
import com.quietmetrix.dashboard.resources.settings_about_body
import com.quietmetrix.dashboard.resources.settings_about_title
import com.quietmetrix.dashboard.resources.settings_appearance
import com.quietmetrix.dashboard.resources.settings_demo_explanation
import com.quietmetrix.dashboard.resources.settings_demo_title
import com.quietmetrix.dashboard.resources.settings_refresh
import com.quietmetrix.dashboard.resources.settings_server_mode
import com.quietmetrix.dashboard.resources.settings_theme
import com.quietmetrix.dashboard.resources.settings_theme_dark
import com.quietmetrix.dashboard.resources.settings_theme_light
import com.quietmetrix.dashboard.resources.settings_theme_system
import com.quietmetrix.dashboard.resources.settings_title
import com.quietmetrix.dashboard.resources.snackbar_api_key_copied
import com.quietmetrix.dashboard.resources.snackbar_invite_link_copied
import com.quietmetrix.dashboard.resources.state_no_data
import com.quietmetrix.dashboard.resources.state_no_events
import com.quietmetrix.dashboard.resources.state_no_projects
import com.quietmetrix.dashboard.resources.state_no_transitions
import com.quietmetrix.dashboard.resources.state_waiting_live
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.tab_info_events
import com.quietmetrix.dashboard.resources.tab_info_flow
import com.quietmetrix.dashboard.resources.tab_info_live
import com.quietmetrix.dashboard.resources.tab_info_overview
import com.quietmetrix.dashboard.resources.tab_info_projects
import com.quietmetrix.dashboard.resources.tab_info_settings
import com.quietmetrix.dashboard.resources.tab_info_users
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.LocalThemeMode
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.KpiCard
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.charts.BarChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.BarData
import com.quietmetrix.dashboard.ui.components.charts.DonutChart
import com.quietmetrix.dashboard.ui.components.charts.DonutData
import com.quietmetrix.dashboard.ui.components.charts.LineChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.LineData
import com.quietmetrix.dashboard.ui.components.contentStateFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.table.Column
import com.quietmetrix.dashboard.ui.components.table.DataTable
import com.quietmetrix.dashboard.ui.components.table.SortColumn
import com.quietmetrix.dashboard.ui.components.table.filterItems
import com.quietmetrix.dashboard.ui.components.table.paginate
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.nav.NavDestination as AppTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, state: DashboardState) {
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    DashboardShell(
        viewModel = viewModel,
        state = state,
        snackbarState = snackbarState,
    ) { sizeClass ->
        when (state.activeDestination) {
            AppTab.Overview -> OverviewTab(viewModel, state, sizeClass)
            AppTab.Events   -> EventsTab(viewModel, state, sizeClass)
            AppTab.Flow     -> FlowTab(viewModel, state, sizeClass)
            AppTab.Live     -> LiveTab(viewModel, state)
            AppTab.Projects -> ProjectsTab(viewModel, state, sizeClass)
            AppTab.Users    -> UsersTab(viewModel, state, sizeClass)
            AppTab.Settings -> SettingsTab(viewModel, state)
        }
    }

    state.newProjectKeys?.let { keys ->
        val clipboard = LocalClipboardManager.current
        val copiedMsg = stringResource(Res.string.snackbar_api_key_copied)
        AlertDialog(
            onDismissRequest = { viewModel.dismissNewProjectKeys() },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissNewProjectKeys() },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.action_got_it)) }
            },
            title = { Text(stringResource(Res.string.project_created_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.project_created_key_warning),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(stringResource(Res.string.project_created_api_key_label), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)) {
                            Text(keys.apiKey,
                                 modifier = Modifier.padding(8.dp),
                                 style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(keys.apiKey))
                                scope.launch { snackbarState.showSnackbar(copiedMsg) }
                            },
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_copy),
                                contentDescription = stringResource(Res.string.action_copy_api_key),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
    }

    // One-time reveal of a freshly regenerated API key, with a copy button.
    state.regeneratedKey?.let { regen ->
        val clipboard = LocalClipboardManager.current
        val copiedMsg = stringResource(Res.string.snackbar_api_key_copied)
        AlertDialog(
            onDismissRequest = { viewModel.dismissRegeneratedKey() },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissRegeneratedKey() },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.action_got_it)) }
            },
            title = { Text(stringResource(Res.string.regenerated_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.project_created_key_warning),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(stringResource(Res.string.api_key_label), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)) {
                            Text(regen.apiKey,
                                 modifier = Modifier.padding(8.dp),
                                 style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(regen.apiKey))
                                scope.launch { snackbarState.showSnackbar(copiedMsg) }
                            },
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_copy),
                                contentDescription = stringResource(Res.string.action_copy_api_key),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
    }

    // Invitation link reveal (admin copies it to share with the new user).
    state.lastInviteLink?.let { link ->
        val clipboard = LocalClipboardManager.current
        val copiedMsg = stringResource(Res.string.snackbar_invite_link_copied)
        AlertDialog(
            onDismissRequest = { viewModel.dismissInviteLink() },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissInviteLink() },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.action_got_it)) }
            },
            title = { Text(stringResource(Res.string.invite_link_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.invite_link_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)) {
                            Text(link,
                                 modifier = Modifier.padding(8.dp),
                                 style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(link))
                                scope.launch { snackbarState.showSnackbar(copiedMsg) }
                            },
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_copy),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
    }

    state.showInfo?.let { tab ->
        val infoText = when (tab) {
            AppTab.Overview -> stringResource(Res.string.tab_info_overview)
            AppTab.Events   -> stringResource(Res.string.tab_info_events)
            AppTab.Flow     -> stringResource(Res.string.tab_info_flow)
            AppTab.Live     -> stringResource(Res.string.tab_info_live)
            AppTab.Projects -> stringResource(Res.string.tab_info_projects)
            AppTab.Users    -> stringResource(Res.string.tab_info_users)
            AppTab.Settings -> stringResource(Res.string.tab_info_settings)
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissTabInfo() },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissTabInfo() },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.action_got_it)) }
            },
            title = { Text(stringResource(tab.labelRes)) },
            text = {
                Text(infoText, style = MaterialTheme.typography.bodyMedium)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Overview tab
// ---------------------------------------------------------------------------

@Composable
private fun OverviewTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
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
                WindowPicker(state.range) { viewModel.selectRange(it) }
                if (state.demoMode) DemoBadge()
                IconButton(
                    onClick = { viewModel.showTabInfo(AppTab.Overview) },
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

            val agg = state.aggregates
            val sess = state.sessions

            ResponsiveRowOrColumn(sizeClass) {
                when (contentStateFor(state.loading, agg == null)) {
                    ContentState.Loading -> {
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                    }
                    else -> {
                        KpiCard(stringResource(Res.string.kpi_events), formatCount(agg?.totals?.events ?: 0), Modifier.fillSlot())
                        KpiCard(stringResource(Res.string.kpi_offline_captured), formatCount(agg?.totals?.offline ?: 0), Modifier.fillSlot())
                        KpiCard(stringResource(Res.string.kpi_errors), formatCount(agg?.totals?.errors ?: 0), Modifier.fillSlot())
                    }
                }
            }

            ResponsiveRowOrColumn(sizeClass) {
                when (contentStateFor(state.loading, sess == null)) {
                    ContentState.Loading -> {
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                    }
                    else -> {
                        KpiCard(stringResource(Res.string.kpi_sessions), formatCount((sess?.totalSessions ?: 0).toLong()), Modifier.fillSlot())
                        KpiCard(stringResource(Res.string.kpi_events_per_session), formatFloat(sess?.avgEvents ?: 0f), Modifier.fillSlot())
                        KpiCard(stringResource(Res.string.kpi_avg_duration), formatDuration(sess?.avgDurationSec ?: 0), Modifier.fillSlot())
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.card_daily_volume),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val daily = agg?.daily ?: emptyList()
                    if (daily.isEmpty()) {
                        EmptyState(Res.drawable.ic_events, Res.string.state_no_data)
                    } else {
                        BarChartCanvas(daily.map { BarData(formatBucketLabel(it.day, state.range.hourly), it.total.toFloat()) })
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.card_sessions_trend),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val sessDaily = sess?.dailySessions ?: emptyList()
                    if (sessDaily.isEmpty()) {
                        EmptyState(Res.drawable.ic_live, Res.string.state_no_data)
                    } else {
                        LineChartCanvas(sessDaily.map { LineData(formatBucketLabel(it.day, state.range.hourly), it.total.toFloat()) })
                    }
                }
            }

            ResponsiveRowOrColumn(sizeClass) {
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_top_events),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val top = agg?.topEvents ?: emptyList()
                        if (top.isEmpty()) {
                            EmptyState(Res.drawable.ic_events, Res.string.state_no_data)
                        } else {
                            top.forEach { TopEventRow(it) }
                        }
                    }
                }
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_top_screens),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val top = agg?.topScreens ?: emptyList()
                        if (top.isEmpty()) {
                            EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
                        } else {
                            top.forEach { TopScreenRow(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopEventRow(item: TopEvent) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.eventName, modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodyMedium)
        Text(item.count.toString(), style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopScreenRow(item: TopScreen) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.screen ?: "(none)", modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodyMedium)
        Text(item.count.toString(), style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Events tab
// ---------------------------------------------------------------------------

@Composable
private fun EventsTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
                if (state.demoMode) DemoBadge()
                IconButton(
                    onClick = { viewModel.showTabInfo(AppTab.Events) },
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

            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.search_events)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val filtered = remember(state.recentEvents, query) {
                filterItems(state.recentEvents, query) {
                    listOf(it.eventName, it.screen, it.platform, it.country, it.sessionId)
                }
            }

            var page by remember { mutableIntStateOf(1) }
            LaunchedEffect(query) { page = 1 }
            val pageSize = 20
            val paged = remember(filtered, page) { paginate(filtered, page, pageSize) }

            Card(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    if (filtered.isEmpty()) {
                        EmptyState(
                            Res.drawable.ic_events,
                            Res.string.state_no_events,
                            Modifier.weight(1f),
                        )
                    } else {
                        DataTable(
                            columns = eventColumns(),
                            rows = paged.items,
                            sizeClass = sizeClass,
                            initialSort = SortColumn(0, ascending = true),
                            rowKey = { ev -> ev.id ?: (ev.eventName to ev.ts) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                    PaginationControls(paged, onPrev = { page = (page - 1).coerceAtLeast(1) }, onNext = { page++ })
                }
            }
        }
    }
}

@Composable
private fun eventColumns(): List<Column<EventRow>> {
    val none = stringResource(Res.string.value_none)
    return listOf(
        Column(
            title = stringResource(Res.string.col_event),
            weight = 1.6f,
            cell = { ev ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ev.eventName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (ev.wasOffline) DemoBadgeSmall(stringResource(Res.string.badge_offline))
                }
            },
            sortSelector = { it.eventName },
        ),
        Column(
            title = stringResource(Res.string.col_screen),
            weight = 1.2f,
            cell = { ev -> Text(ev.screen ?: none, style = MaterialTheme.typography.bodySmall) },
            sortSelector = { it.screen },
            hideOnCompact = true,
        ),
        Column(
            title = stringResource(Res.string.col_platform),
            weight = 0.9f,
            cell = { ev -> Text(ev.platform ?: none, style = MaterialTheme.typography.bodySmall) },
            sortSelector = { it.platform },
            hideOnCompact = true,
        ),
        Column(
            title = stringResource(Res.string.col_country),
            weight = 0.7f,
            cell = { ev -> Text(ev.country ?: none, style = MaterialTheme.typography.bodySmall) },
            sortSelector = { it.country },
            hideOnCompact = true,
        ),
        Column(
            title = stringResource(Res.string.col_time),
            weight = 1.1f,
            cell = { ev -> Text(ev.ts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            sortSelector = { it.ts },
        ),
    )
}

@Composable
private fun PaginationControls(
    page: com.quietmetrix.dashboard.ui.components.table.Page<*>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    if (page.totalPages <= 1) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPrev, enabled = page.page > 1) {
            Text(stringResource(Res.string.page_prev))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(Res.string.page_info, page.page, page.totalPages),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onNext, enabled = page.page < page.totalPages) {
            Text(stringResource(Res.string.page_next))
        }
    }
}

@Composable
private fun EventRowItem(ev: EventRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ev.eventName, modifier = Modifier.weight(1f),
                 style = MaterialTheme.typography.bodyMedium)
            if (ev.wasOffline) DemoBadgeSmall(stringResource(Res.string.badge_offline))
            Spacer(Modifier.width(8.dp))
            Text(ev.ts, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sub = listOfNotNull(
            ev.screen?.let { stringResource(Res.string.event_meta_screen, it) },
            ev.platform?.let { stringResource(Res.string.event_meta_platform, it) },
            ev.country?.let { stringResource(Res.string.event_meta_country, it) },
            ev.sessionId?.let { stringResource(Res.string.event_meta_session, it) },
        ).joinToString("  ")
        if (sub.isNotEmpty()) {
            Text(sub, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp),
                          color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Flow tab
// ---------------------------------------------------------------------------

@Composable
private fun FlowTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
                IconButton(
                    onClick = { viewModel.showTabInfo(AppTab.Flow) },
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

            val transitions = state.transitions?.transitions ?: emptyList()
            if (transitions.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(Res.drawable.ic_flow, Res.string.state_no_transitions, Modifier.padding(16.dp))
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DataTable(
                            columns = listOf(
                                Column(
                                    title = stringResource(Res.string.col_from),
                                    weight = 1f,
                                    cell = { t: Transition -> Text(t.fromScreen, style = MaterialTheme.typography.bodyMedium) },
                                    sortSelector = { it.fromScreen },
                                ),
                                Column(
                                    title = stringResource(Res.string.col_to),
                                    weight = 1f,
                                    cell = { t: Transition -> Text(t.toScreen, style = MaterialTheme.typography.bodyMedium) },
                                    sortSelector = { it.toScreen },
                                ),
                                Column(
                                    title = stringResource(Res.string.col_count),
                                    weight = 0.6f,
                                    cell = { t: Transition ->
                                        Text(
                                            formatCount(t.count),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    sortSelector = { it.count },
                                ),
                            ),
                            rows = transitions,
                            sizeClass = sizeClass,
                            initialSort = SortColumn(2, ascending = false),
                            rowKey = { it.fromScreen to it.toScreen },
                            modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                        )
                    }
                }
            }

            val agg2 = state.aggregates
            ResponsiveRowOrColumn(sizeClass) {
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_countries),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        DonutChart(
                            (agg2?.countries ?: emptyList()).map { DonutData(it.displayName, it.count.toFloat()) },
                            sizeClass,
                        )
                    }
                }
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_platforms),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        DonutChart(
                            (agg2?.platforms ?: emptyList()).map { DonutData(it.displayName, it.count.toFloat()) },
                            sizeClass,
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.card_device_classes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    DonutChart(
                        (agg2?.deviceClasses ?: emptyList()).map { DonutData(it.displayName, it.count.toFloat()) },
                        sizeClass,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Live tab
// ---------------------------------------------------------------------------

@Composable
private fun LiveTab(viewModel: DashboardViewModel, state: DashboardState) {
    // Poll the recent-events endpoint every 5s while the tab is composed so the
    // "auto-refreshes every few seconds" claim in the info dialog is true.
    LaunchedEffect(state.currentProjectId) {
        while (true) {
            viewModel.loadLiveEvents()
            kotlinx.coroutines.delay(5_000)
        }
    }

    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
                IconButton(
                    onClick = { viewModel.showTabInfo(AppTab.Live) },
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

            Card(modifier = Modifier.fillMaxSize()) {
                if (state.liveEvents.isEmpty()) {
                    EmptyState(Res.drawable.ic_live, Res.string.state_waiting_live, Modifier.padding(24.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(state.liveEvents) { ev -> EventRowItem(ev) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Projects tab
// ---------------------------------------------------------------------------

@Composable
private fun ProjectsTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ApiProject?>(null) }

    var pendingRegenerate by remember { mutableStateOf<ApiProject?>(null) }

    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.projects_your),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Compact "New app" button, top-aligned, admin only.
                if (state.isAdmin) {
                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.handCursor(),
                    ) {
                        Icon(painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.create_app_action))
                    }
                }
                IconButton(onClick = { viewModel.showTabInfo(AppTab.Projects) }, modifier = Modifier.handCursor()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = stringResource(Res.string.tab_info_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (state.projects.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(Res.drawable.ic_projects, Res.string.state_no_projects, Modifier.padding(16.dp))
                }
            } else {
                val columns = when (sizeClass) {
                    WindowSizeClass.Compact -> 1
                    WindowSizeClass.Medium -> 2
                    WindowSizeClass.Expanded -> 3
                }
                state.projects.chunked(columns).forEach { rowProjects ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        rowProjects.forEach { p ->
                            ProjectCard(
                                project = p,
                                isAdmin = state.isAdmin,
                                canRegenerate = state.canRegenerate,
                                modifier = Modifier.weight(1f),
                                onDelete = { pendingDelete = p },
                                onRegenerate = { pendingRegenerate = p },
                            )
                        }
                        repeat(columns - rowProjects.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    pendingRegenerate?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingRegenerate = null },
            title = { Text(stringResource(Res.string.regenerate_confirm_title)) },
            text = { Text(stringResource(Res.string.regenerate_confirm_body, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.regenerateApiKey(project.id)
                        pendingRegenerate = null
                    },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.action_regenerate)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRegenerate = null }, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.create_project_cancel))
                }
            },
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(Res.string.create_project_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(Res.string.create_project_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newDescription,
                        onValueChange = { newDescription = it },
                        label = { Text(stringResource(Res.string.create_project_description_label)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp),
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val desc = newDescription.trim().takeIf { it.isNotEmpty() }
                            viewModel.createProject(newName.trim(), desc)
                            newName = ""
                            newDescription = ""
                            showDialog = false
                        }
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.create_project_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.create_project_cancel))
                }
            },
        )
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.projects_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.projects_delete_confirm_body, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(project.id)
                        pendingDelete = null
                    },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.projects_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.create_project_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProjectCard(
    project: ApiProject,
    isAdmin: Boolean,
    canRegenerate: Boolean,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (!project.description.isNullOrBlank()) {
                Text(
                    project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.height(4.dp))

            // Masked API key: all hidden except the last 4 chars (the full key is
            // never recoverable — only a hash is stored server-side).
            Text(
                stringResource(Res.string.api_key_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "•".repeat(16) + (project.apiKeyLast4 ?: ""),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.projects_created, formatDateOnly(project.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (canRegenerate) {
                    TextButton(onClick = onRegenerate, modifier = Modifier.handCursor()) {
                        Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.action_regenerate))
                    }
                }
                if (isAdmin) {
                    TextButton(onClick = onDelete, modifier = Modifier.handCursor()) {
                        Icon(painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.projects_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings tab
// ---------------------------------------------------------------------------

@Composable
private fun SettingsTab(viewModel: DashboardViewModel, state: DashboardState) {
    val themeMode = LocalThemeMode.current
    val setThemeMode = LocalThemeModeSetter.current

    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.showTabInfo(AppTab.Settings) }, modifier = Modifier.handCursor()) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_info),
                                contentDescription = stringResource(Res.string.tab_info_content_description),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(stringResource(Res.string.settings_appearance), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(Res.string.settings_theme),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = themeMode == ThemeMode.Light,
                            onClick = { setThemeMode(ThemeMode.Light) },
                            label = { Text(stringResource(Res.string.settings_theme_light)) },
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.Dark,
                            onClick = { setThemeMode(ThemeMode.Dark) },
                            label = { Text(stringResource(Res.string.settings_theme_dark)) },
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.System,
                            onClick = { setThemeMode(ThemeMode.System) },
                            label = { Text(stringResource(Res.string.settings_theme_system)) },
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            Res.string.settings_server_mode,
                            stringResource(if (state.serverDebug) Res.string.server_mode_debug else Res.string.server_mode_production),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.serverDebug) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = state.demoMode,
                                onCheckedChange = { viewModel.setDemoMode(it) },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(Res.string.settings_demo_title))
                        }
                        Text(
                            stringResource(Res.string.settings_demo_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(state.activeDestination) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.settings_refresh))
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.settings_about_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(Res.string.settings_about_body), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared widgets
// ---------------------------------------------------------------------------

@Composable
private fun ProjectPicker(
    projects: List<ApiProject>,
    currentId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = projects.firstOrNull { it.id == currentId }
    val noProjects = stringResource(Res.string.picker_no_projects)
    val selectProject = stringResource(Res.string.picker_select_project)
    OutlinedButton(
        onClick = { expanded = true },
        enabled = projects.isNotEmpty(),
        modifier = Modifier.handCursor(),
    ) {
        Text(current?.name ?: if (projects.isEmpty()) noProjects else selectProject)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        projects.forEach { p ->
            DropdownMenuItem(
                text = { Text(p.name) },
                onClick = { onSelect(p.id); expanded = false },
                modifier = Modifier.handCursor(),
            )
        }
    }
}

@Composable
private fun WindowPicker(range: TimeRange, onSelect: (TimeRange) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.handCursor(),
    ) { Text(stringResource(range.labelRes)) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        TimeRange.entries.forEach { r ->
            DropdownMenuItem(
                text = { Text(stringResource(r.labelRes)) },
                onClick = { onSelect(r); expanded = false },
                modifier = Modifier.handCursor(),
            )
        }
    }
}

@Composable
private fun DemoBadge() {
    val ext = LocalExtendedColors.current
    Surface(color = ext.demoBadge, shape = RoundedCornerShape(999.dp)) {
        Text(
            stringResource(Res.string.badge_demo),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ext.onDemoBadge,
        )
    }
}

@Composable
private fun DemoBadgeSmall(text: String) {
    val ext = LocalExtendedColors.current
    Surface(color = ext.demoBadge, shape = RoundedCornerShape(999.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ext.onDemoBadge,
        )
    }
}
