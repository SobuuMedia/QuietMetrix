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
import androidx.compose.material3.Checkbox
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
import com.quietmetrix.dashboard.api.AccessTokenDto
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.format.formatDateOnly
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_close
import com.quietmetrix.dashboard.resources.action_copy_api_key
import com.quietmetrix.dashboard.resources.action_got_it
import com.quietmetrix.dashboard.resources.action_regenerate
import com.quietmetrix.dashboard.resources.api_key_label
import com.quietmetrix.dashboard.resources.badge_demo
import com.quietmetrix.dashboard.resources.badge_offline
import com.quietmetrix.dashboard.resources.create_app_action
import com.quietmetrix.dashboard.resources.create_project_button
import com.quietmetrix.dashboard.resources.create_project_cancel
import com.quietmetrix.dashboard.resources.create_project_description_label
import com.quietmetrix.dashboard.resources.create_project_dialog_title
import com.quietmetrix.dashboard.resources.create_project_name_label
import com.quietmetrix.dashboard.resources.ic_copy
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.ic_projects
import com.quietmetrix.dashboard.resources.ic_refresh
import com.quietmetrix.dashboard.resources.ic_search
import com.quietmetrix.dashboard.resources.invite_link_body
import com.quietmetrix.dashboard.resources.invite_link_title
import com.quietmetrix.dashboard.resources.label_language
import com.quietmetrix.dashboard.resources.label_sdk
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
import com.quietmetrix.dashboard.resources.snackbar_token_copied
import com.quietmetrix.dashboard.resources.state_no_projects
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.tab_info_flow
import com.quietmetrix.dashboard.resources.tab_info_experiments
import com.quietmetrix.dashboard.resources.tab_info_funnels
import com.quietmetrix.dashboard.resources.tab_info_overview
import com.quietmetrix.dashboard.resources.tab_info_projects
import com.quietmetrix.dashboard.resources.tab_info_retention
import com.quietmetrix.dashboard.resources.tab_info_settings
import com.quietmetrix.dashboard.resources.tab_info_users
import com.quietmetrix.dashboard.resources.tokens_cancel_action
import com.quietmetrix.dashboard.resources.tokens_create_action
import com.quietmetrix.dashboard.resources.tokens_created_at_label
import com.quietmetrix.dashboard.resources.tokens_created_title
import com.quietmetrix.dashboard.resources.tokens_created_warning
import com.quietmetrix.dashboard.resources.tokens_empty
import com.quietmetrix.dashboard.resources.tokens_hint
import com.quietmetrix.dashboard.resources.tokens_last_used_label
import com.quietmetrix.dashboard.resources.tokens_name_and_last4
import com.quietmetrix.dashboard.resources.tokens_name_label
import com.quietmetrix.dashboard.resources.tokens_name_placeholder
import com.quietmetrix.dashboard.resources.tokens_never_used
import com.quietmetrix.dashboard.resources.tokens_new_action
import com.quietmetrix.dashboard.resources.tokens_revoke_action
import com.quietmetrix.dashboard.resources.tokens_scope_create_projects
import com.quietmetrix.dashboard.resources.tokens_scope_label
import com.quietmetrix.dashboard.resources.tokens_scope_picker_label
import com.quietmetrix.dashboard.resources.tokens_scope_read_analytics
import com.quietmetrix.dashboard.resources.tokens_scope_read_analytics_hint
import com.quietmetrix.dashboard.resources.tokens_scope_read_projects
import com.quietmetrix.dashboard.resources.tokens_title
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.LocalThemeMode
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.KpiCard
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.contentStateFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
            AppTab.Overview  -> OverviewScreen(viewModel, state, sizeClass)
            AppTab.Flow      -> FlowScreen(viewModel, state, sizeClass)
            AppTab.Retention -> RetentionScreen(viewModel, state, sizeClass)
            AppTab.Projects  -> ProjectsTab(viewModel, state, sizeClass)
            AppTab.Funnels   -> FunnelsTab(viewModel, state, sizeClass)
            AppTab.Experiments -> ExperimentsTab(viewModel, state, sizeClass)
            AppTab.Users     -> UsersTab(viewModel, state, sizeClass)
            AppTab.Settings  -> SettingsTab(viewModel, state)
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

    // One-time reveal of a freshly created personal access token, with a copy button.
    state.newAccessToken?.let { plaintextToken ->
        val clipboard = LocalClipboardManager.current
        val copiedMsg = stringResource(Res.string.snackbar_token_copied)
        AlertDialog(
            onDismissRequest = { viewModel.dismissNewAccessToken() },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissNewAccessToken() },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.action_got_it)) }
            },
            title = { Text(stringResource(Res.string.tokens_created_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.tokens_created_warning),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)) {
                            Text(plaintextToken,
                                 modifier = Modifier.padding(8.dp),
                                 style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(plaintextToken))
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

    ExperimentsGuide(viewModel, state)

    state.showInfo?.let { tab ->
        val infoText = when (tab) {
            AppTab.Overview  -> stringResource(Res.string.tab_info_overview)
            AppTab.Flow      -> stringResource(Res.string.tab_info_flow)
            AppTab.Retention -> stringResource(Res.string.tab_info_retention)
            AppTab.Projects  -> stringResource(Res.string.tab_info_projects)
            AppTab.Funnels   -> stringResource(Res.string.tab_info_funnels)
            AppTab.Experiments -> stringResource(Res.string.tab_info_experiments)
            AppTab.Users     -> stringResource(Res.string.tab_info_users)
            AppTab.Settings  -> stringResource(Res.string.tab_info_settings)
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

            if (state.isAdmin) {
                AccessTokensCard(viewModel, state)
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

/**
 * Personal access tokens (`qm_pat_…`) for agents/CLIs to create projects without a
 * password — see docs/agents/setup.md. Admin-only, since only an admin session may mint
 * a token with the projects:create scope.
 */
@Composable
private fun AccessTokensCard(viewModel: DashboardViewModel, state: DashboardState) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<AccessTokenDto?>(null) }

    LaunchedEffect(Unit) { viewModel.loadTokens() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.tokens_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { showCreate = true }, modifier = Modifier.handCursor()) {
                    Icon(painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.tokens_new_action))
                }
            }
            Text(
                stringResource(Res.string.tokens_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.accessTokens.isEmpty()) {
                Text(
                    stringResource(Res.string.tokens_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.accessTokens.forEach { tok ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(Res.string.tokens_name_and_last4, tok.name, tok.last4),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(Res.string.tokens_scope_label, tok.scopes.joinToString(", ")),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(Res.string.tokens_created_at_label, formatDateOnly(tok.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (tok.lastUsedAt != null) {
                                    stringResource(Res.string.tokens_last_used_label, formatDateOnly(tok.lastUsedAt))
                                } else {
                                    stringResource(Res.string.tokens_never_used)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { pendingRevoke = tok }, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.tokens_revoke_action), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateTokenDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, scopes ->
                viewModel.createToken(name, scopes)
                showCreate = false
            },
        )
    }

    pendingRevoke?.let { tok ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text(stringResource(Res.string.tokens_revoke_action)) },
            text = { Text(tok.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revokeToken(tok.id)
                        pendingRevoke = null
                    },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.tokens_revoke_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.tokens_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun CreateTokenDialog(onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var canCreateProjects by remember { mutableStateOf(true) }
    var canReadProjects by remember { mutableStateOf(false) }
    var canReadAnalytics by remember { mutableStateOf(false) }
    val selectedScopes = buildList {
        if (canCreateProjects) add("projects:create")
        if (canReadProjects) add("projects:read")
        if (canReadAnalytics) add("analytics:read")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.tokens_new_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.tokens_name_label)) },
                    placeholder = { Text(stringResource(Res.string.tokens_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.tokens_scope_picker_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.handCursor()) {
                    Checkbox(checked = canCreateProjects, onCheckedChange = { canCreateProjects = it })
                    Text(stringResource(Res.string.tokens_scope_create_projects))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.handCursor()) {
                    Checkbox(checked = canReadProjects, onCheckedChange = { canReadProjects = it })
                    Text(stringResource(Res.string.tokens_scope_read_projects))
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.handCursor()) {
                        Checkbox(checked = canReadAnalytics, onCheckedChange = { canReadAnalytics = it })
                        Text(stringResource(Res.string.tokens_scope_read_analytics))
                    }
                    Text(
                        stringResource(Res.string.tokens_scope_read_analytics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && selectedScopes.isNotEmpty()) onCreate(name.trim(), selectedScopes) },
                enabled = name.isNotBlank() && selectedScopes.isNotEmpty(),
                modifier = Modifier.handCursor(),
            ) { Text(stringResource(Res.string.tokens_create_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.tokens_cancel_action))
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Shared widgets
// ---------------------------------------------------------------------------

@Composable
internal fun ProjectPicker(
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
internal fun WindowPicker(range: TimeRange, onSelect: (TimeRange) -> Unit) {
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
internal fun DemoBadge() {
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
