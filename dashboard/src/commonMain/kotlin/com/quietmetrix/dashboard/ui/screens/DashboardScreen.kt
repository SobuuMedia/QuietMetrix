package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.DailyPoint
import com.quietmetrix.dashboard.api.EventRow
import com.quietmetrix.dashboard.api.TopEvent
import com.quietmetrix.dashboard.api.TopScreen
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_copy
import com.quietmetrix.dashboard.resources.tab_info_events
import com.quietmetrix.dashboard.resources.tab_info_flow
import com.quietmetrix.dashboard.resources.tab_info_live
import com.quietmetrix.dashboard.resources.tab_info_overview
import com.quietmetrix.dashboard.resources.tab_info_projects
import com.quietmetrix.dashboard.resources.tab_info_settings
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.viewmodel.Tab as AppTab

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, state: DashboardState) {
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarState) },
        topBar = {
            TopAppBar(
                title = { Text("QuietMetrix") },
                actions = {
                    Text(
                        text = state.user?.email.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) { Text("Sign out") }
                    Spacer(Modifier.width(8.dp))
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.activeTab.ordinal) {
                AppTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.activeTab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.name) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            }

            state.error?.let { errorMsg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text("✕")
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (state.activeTab) {
                    AppTab.Overview -> OverviewTab(viewModel, state)
                    AppTab.Events   -> EventsTab(viewModel, state)
                    AppTab.Flow     -> FlowTab(viewModel, state)
                    AppTab.Live     -> LiveTab(viewModel, state)
                    AppTab.Projects -> ProjectsTab(viewModel, state)
                    AppTab.Settings -> SettingsTab(viewModel, state)
                }
            }
        }
    }

    state.newProjectKeys?.let { keys ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissNewProjectKeys() },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissNewProjectKeys() },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) { Text("Got it") }
            },
            title = { Text("Project created") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copy this key now — it will not be shown again.",
                         style = MaterialTheme.typography.bodySmall)
                    Text("API key", style = MaterialTheme.typography.labelMedium)
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
                                scope.launch { snackbarState.showSnackbar("API key copied to clipboard") }
                            },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_copy),
                                contentDescription = "Copy API key",
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
            AppTab.Settings -> stringResource(Res.string.tab_info_settings)
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissTabInfo() },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissTabInfo() },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) { Text("Got it") }
            },
            title = { Text(tab.name) },
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
private fun OverviewTab(viewModel: DashboardViewModel, state: DashboardState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
            WindowPicker(state.windowDays) { viewModel.selectWindow(it) }
            if (state.demoMode) DemoBadge()
            IconButton(
                onClick = { viewModel.showTabInfo(AppTab.Overview) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).size(24.dp)
            ) {
                Text("ℹ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val agg = state.aggregates
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Kpi("Events",          (agg?.totals?.events  ?: 0).toString(), Modifier.weight(1f))
            Kpi("Offline-captured",(agg?.totals?.offline ?: 0).toString(), Modifier.weight(1f))
            Kpi("Window",          "${state.windowDays} days",             Modifier.weight(1f))
        }

        val sess = state.sessions
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Kpi("Sessions",        (sess?.totalSessions  ?: 0).toString(),      Modifier.weight(1f))
            Kpi("Events/session",  formatFloat(sess?.avgEvents ?: 0f),        Modifier.weight(1f))
            Kpi("Avg duration",    formatDuration(sess?.avgDurationSec ?: 0),   Modifier.weight(1f))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Daily volume", style = MaterialTheme.typography.titleSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                DailyVolumeList(agg?.daily ?: emptyList())
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Top events", style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    (agg?.topEvents ?: emptyList()).forEach { TopEventRow(it) }
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Top screens", style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    (agg?.topScreens ?: emptyList()).forEach { TopScreenRow(it) }
                }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val mins = seconds / 60
    val secs = seconds % 60
    return "${mins}m ${secs}s"
}

private fun formatFloat(value: Float): String {
    val n = (value * 10).toInt()
    return "${n / 10}.${n % 10}"
}

@Composable
private fun DailyVolumeList(daily: List<DailyPoint>) {
    if (daily.isEmpty()) {
        Text("No data yet.", style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = daily.maxOf { it.total }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        daily.takeLast(14).forEach { point ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(point.day, modifier = Modifier.width(96.dp),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                val ratio = point.total.toFloat() / max
                Surface(color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.height(10.dp).fillMaxWidth(ratio.coerceIn(0.02f, 1f))) {}
                Spacer(Modifier.width(8.dp))
                Text(point.total.toString(), style = MaterialTheme.typography.bodySmall)
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
private fun EventsTab(viewModel: DashboardViewModel, state: DashboardState) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
            if (state.demoMode) DemoBadge()
            IconButton(
                onClick = { viewModel.showTabInfo(AppTab.Events) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).size(24.dp)
            ) {
                Text("ℹ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(state.recentEvents) { ev -> EventRowItem(ev) }
                if (state.recentEvents.isEmpty()) {
                    item {
                        Text("No events yet.", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRowItem(ev: EventRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ev.eventName, modifier = Modifier.weight(1f),
                 style = MaterialTheme.typography.bodyMedium)
            if (ev.wasOffline) DemoBadgeSmall("offline")
            Spacer(Modifier.width(8.dp))
            Text(ev.ts, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sub = listOfNotNull(
            ev.screen?.let { "screen=$it" },
            ev.platform?.let { "platform=$it" },
            ev.country?.let { "country=$it" },
            ev.sessionId?.let { "sid=$it" },
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
private fun FlowTab(viewModel: DashboardViewModel, state: DashboardState) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
            IconButton(
                onClick = { viewModel.showTabInfo(AppTab.Flow) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).size(24.dp)
            ) {
                Text("ℹ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val transitions = state.transitions?.transitions ?: emptyList()
        if (transitions.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("No screen transitions yet. Events with screen names and session IDs are needed.",
                     modifier = Modifier.padding(16.dp),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("From", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("To", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Count", style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(transitions) { t ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(t.fromScreen, modifier = Modifier.weight(1f),
                                     style = MaterialTheme.typography.bodyMedium)
                                Text(t.toScreen, modifier = Modifier.weight(1f),
                                     style = MaterialTheme.typography.bodyMedium)
                                Text(t.count.toString(), style = MaterialTheme.typography.bodyMedium,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }

        val agg2 = state.aggregates
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Countries", style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val countries = agg2?.countries ?: emptyList()
                    if (countries.isEmpty()) {
                        Text("No data", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    countries.take(5).forEach { BreakdownRow(it) }
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Platforms", style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val platforms = agg2?.platforms ?: emptyList()
                    if (platforms.isEmpty()) {
                        Text("No data", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    platforms.forEach { BreakdownRow(it) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device classes", style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val devices = agg2?.deviceClasses ?: emptyList()
                    if (devices.isEmpty()) {
                        Text("No data", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    devices.forEach { BreakdownRow(it) }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BreakdownRow(item: com.quietmetrix.dashboard.api.BreakdownItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(item.displayName, modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodySmall)
        Text(item.count.toString(),
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Live tab
// ---------------------------------------------------------------------------

@Composable
private fun LiveTab(viewModel: DashboardViewModel, state: DashboardState) {
    LaunchedEffect(state.currentProjectId) {
        viewModel.loadLiveEvents()
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
            IconButton(
                onClick = { viewModel.showTabInfo(AppTab.Live) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).size(24.dp)
            ) {
                Text("ℹ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(state.liveEvents) { ev -> EventRowItem(ev) }
                if (state.liveEvents.isEmpty()) {
                    item {
                        Text("Waiting for events...", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             modifier = Modifier.padding(8.dp))
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
private fun ProjectsTab(viewModel: DashboardViewModel, state: DashboardState) {
    var newName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your projects", style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.showTabInfo(AppTab.Projects) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).size(24.dp)
                    ) {
                        Text("ℹ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.projects.isEmpty()) {
                    Text("No projects yet — create one below.",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.projects.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, modifier = Modifier.weight(1f),
                             style = MaterialTheme.typography.bodyMedium)
                        Text(p.createdAt.orEmpty(),
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.deleteProject(p.id) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text("Delete")
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New project name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.createProject(newName.trim())
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) { Text("Create") }
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
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.titleMedium,
                         modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.showTabInfo(AppTab.Settings) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).size(24.dp)
                    ) {
                        Text("ℹ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Server mode: ${if (state.serverDebug) "debug" else "production"}",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (state.serverDebug) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = state.demoMode,
                            onCheckedChange = { viewModel.setDemoMode(it) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Use demo data in this dashboard")
                    }
                    Text(
                        "Available because the server is running with DEBUG=true. " +
                            "When enabled, charts and tables show synthetic data instead of " +
                            "querying the database. Has no effect in production deployments.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    OutlinedButton(
        onClick = { expanded = true },
        enabled = projects.isNotEmpty(),
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
    ) {
        Text(current?.name ?: if (projects.isEmpty()) "No projects" else "Select a project")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        projects.forEach { p ->
            DropdownMenuItem(
                text = { Text(p.name) },
                onClick = { onSelect(p.id); expanded = false },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}

@Composable
private fun WindowPicker(days: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
    ) { Text("Last $days days") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf(7, 30, 90).forEach { d ->
            DropdownMenuItem(
                text = { Text("Last $d days") },
                onClick = { onSelect(d); expanded = false },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}

@Composable
private fun DemoBadge() {
    Surface(color = Color(0xFFD29922), shape = RoundedCornerShape(999.dp)) {
        Text(
            "DEMO",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF1C2128),
        )
    }
}

@Composable
private fun DemoBadgeSmall(text: String) {
    Surface(color = Color(0xFFD29922), shape = RoundedCornerShape(999.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF1C2128),
        )
    }
}
