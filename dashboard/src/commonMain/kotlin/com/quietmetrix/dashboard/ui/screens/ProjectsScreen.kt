package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.format.formatDateOnly
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_regenerate
import com.quietmetrix.dashboard.resources.api_key_label
import com.quietmetrix.dashboard.resources.create_app_action
import com.quietmetrix.dashboard.resources.create_project_button
import com.quietmetrix.dashboard.resources.create_project_cancel
import com.quietmetrix.dashboard.resources.create_project_description_label
import com.quietmetrix.dashboard.resources.create_project_dialog_title
import com.quietmetrix.dashboard.resources.create_project_name_label
import com.quietmetrix.dashboard.resources.ic_arrow_right
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.ic_projects
import com.quietmetrix.dashboard.resources.ic_refresh
import com.quietmetrix.dashboard.resources.ic_twemoji_1f39f
import com.quietmetrix.dashboard.resources.ic_twemoji_1f3c6
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4bb
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4d6
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4e6
import com.quietmetrix.dashboard.resources.ic_twemoji_2708
import com.quietmetrix.dashboard.resources.management_key_hint
import com.quietmetrix.dashboard.resources.project_activity_receiving
import com.quietmetrix.dashboard.resources.project_activity_stale
import com.quietmetrix.dashboard.resources.project_activity_waiting
import com.quietmetrix.dashboard.resources.project_declared_platforms_label
import com.quietmetrix.dashboard.resources.project_icon_label
import com.quietmetrix.dashboard.resources.project_last_received
import com.quietmetrix.dashboard.resources.project_platform_coverage_private
import com.quietmetrix.dashboard.resources.project_platforms_configured
import com.quietmetrix.dashboard.resources.project_platforms_observed
import com.quietmetrix.dashboard.resources.project_sdk_versions
import com.quietmetrix.dashboard.resources.projects_created
import com.quietmetrix.dashboard.resources.projects_delete
import com.quietmetrix.dashboard.resources.projects_delete_confirm_body
import com.quietmetrix.dashboard.resources.projects_delete_confirm_title
import com.quietmetrix.dashboard.resources.projects_edit
import com.quietmetrix.dashboard.resources.projects_edit_save
import com.quietmetrix.dashboard.resources.projects_edit_title
import com.quietmetrix.dashboard.resources.projects_open_dashboard
import com.quietmetrix.dashboard.resources.projects_your
import com.quietmetrix.dashboard.resources.regenerate_confirm_body
import com.quietmetrix.dashboard.resources.regenerate_confirm_title
import com.quietmetrix.dashboard.resources.state_no_projects
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.ui.components.ConfirmDialog
import com.quietmetrix.dashboard.ui.components.DashboardCard
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.ModalFooter
import com.quietmetrix.dashboard.ui.components.ModalFooterAction
import com.quietmetrix.dashboard.ui.components.ModalFooterEmphasis
import com.quietmetrix.dashboard.ui.components.ModalShell
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.SectionLoading
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.isLoading
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.nav.NavDestination as AppTab
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

// ---------------------------------------------------------------------------
// Projects tab
// ---------------------------------------------------------------------------

/** Hosts project actions using the existing authenticated view model. */
@Composable
internal fun ProjectsTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    ProjectsContent(state, sizeClass, viewModel::createProject, viewModel::deleteProject, viewModel::regenerateApiKey,
        { viewModel.showTabInfo(AppTab.Projects) }, viewModel::refresh, { id -> viewModel.selectProject(id); viewModel.navigateTo(AppTab.Overview) }, viewModel::updateProject)
}

/** Displays the project grid and routes confirmed actions through the existing project API. */
@Composable
internal fun ProjectsContent(
    state: DashboardState,
    sizeClass: WindowSizeClass,
    onCreate: (String, String?, String, List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onInfo: () -> Unit,
    onRetry: () -> Unit,
    onOpenProject: (String) -> Unit = {},
    onUpdateProject: (String, String) -> Unit = { _, _ -> },
) {
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var newIconKey by remember { mutableStateOf("book") }
    var newPlatforms by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<ApiProject?>(null) }

    var pendingRegenerate by remember { mutableStateOf<ApiProject?>(null) }
    var editingProject by remember { mutableStateOf<ApiProject?>(null) }
    var editName by remember { mutableStateOf("") }

    MaxWidthContainer(sizeClass = sizeClass, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.projects_your),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
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
                        MaterialText(stringResource(Res.string.create_app_action))
                    }
                }
                IconButton(onClick = onInfo, modifier = Modifier.handCursor()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = stringResource(Res.string.tab_info_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            state.sectionErrors[DataSection.Projects]?.let { InlineErrorNotice(it, onRetry = onRetry) }
            if (state.isLoading(DataSection.Projects) && state.projects.isEmpty()) {
                SectionLoading()
            } else if (state.projects.isEmpty()) {
                DashboardCard(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(Res.drawable.ic_projects, Res.string.state_no_projects, Modifier.padding(16.dp))
                }
                if (state.sectionErrors[DataSection.Projects] == null) SetupInstructionsSection()
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
                                onOpen = { onOpenProject(p.id) },
                                onEdit = { editingProject = p; editName = p.name },
                            )
                        }
                        repeat(columns - rowProjects.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    pendingRegenerate?.let { project ->
        ConfirmDialog(
            emoji = "🔑", title = stringResource(Res.string.regenerate_confirm_title),
            body = stringResource(Res.string.regenerate_confirm_body, project.name),
            confirmLabel = stringResource(Res.string.action_regenerate), danger = true,
            onConfirm = { onRegenerate(project.id); pendingRegenerate = null },
            onDismiss = { pendingRegenerate = null },
        )
    }
    pendingDelete?.let { project ->
        ConfirmDialog(
            emoji = "🗑️", title = stringResource(Res.string.projects_delete_confirm_title),
            body = stringResource(Res.string.projects_delete_confirm_body, project.name),
            confirmLabel = stringResource(Res.string.projects_delete), danger = true,
            onConfirm = { onDelete(project.id); pendingDelete = null }, onDismiss = { pendingDelete = null },
        )
    }
    if (showDialog) {
        ModalShell(
            title = stringResource(Res.string.create_project_dialog_title), onDismiss = { showDialog = false },
            footer = {
                ModalFooter(
                    secondary = ModalFooterAction(stringResource(Res.string.create_project_cancel), ModalFooterEmphasis.Secondary) { showDialog = false },
                    primaryEnabled = newName.isNotBlank(),
                    primary = ModalFooterAction(stringResource(Res.string.create_project_button), ModalFooterEmphasis.Primary) {
                        if (newName.isNotBlank()) {
                            onCreate(newName.trim(), newDescription.trim().takeIf { it.isNotEmpty() }, newIconKey, newPlatforms.sorted())
                            showDialog = false; newName = ""; newDescription = ""; newIconKey = "book"; newPlatforms = emptySet()
                        }
                    },
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(Res.string.create_project_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(newDescription, { newDescription = it }, label = { Text(stringResource(Res.string.create_project_description_label)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp))
                Text(stringResource(Res.string.project_icon_label), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("book", "shopping", "music", "fitness", "travel", "other").forEach { key ->
                        FilterChip(selected = newIconKey == key, onClick = { newIconKey = key }, label = { MaterialText(key.replaceFirstChar { it.uppercase() }) }, modifier = Modifier.handCursor(),
                            leadingIcon = { Icon(painterResource(projectPresetIcon(key)), null, tint = Color.Unspecified, modifier = Modifier.size(18.dp)) })
                    }
                }
                Text(stringResource(Res.string.project_declared_platforms_label), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("android", "ios", "web", "desktop").forEach { platform ->
                        FilterChip(selected = platform in newPlatforms, onClick = {
                            newPlatforms = if (platform in newPlatforms) newPlatforms - platform else newPlatforms + platform
                        }, label = { MaterialText(platform.uppercase()) }, modifier = Modifier.handCursor())
                    }
                }
            }
        }
    }
    editingProject?.let { project ->
        ModalShell(
            title = stringResource(Res.string.projects_edit_title), onDismiss = { editingProject = null },
            footer = {
                ModalFooter(
                    secondary = ModalFooterAction(stringResource(Res.string.create_project_cancel), ModalFooterEmphasis.Secondary) { editingProject = null },
                    primaryEnabled = editName.isNotBlank() && editName.trim() != project.name,
                    primary = ModalFooterAction(stringResource(Res.string.projects_edit_save), ModalFooterEmphasis.Primary) {
                        if (editName.isNotBlank()) onUpdateProject(project.id, editName.trim())
                        editingProject = null
                    },
                )
            },
        ) {
            OutlinedTextField(editName, { editName = it }, label = { Text(stringResource(Res.string.create_project_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun projectPresetIcon(key: String): DrawableResource = when (key) {
    "book" -> Res.drawable.ic_twemoji_1f4d6
    "shopping" -> Res.drawable.ic_twemoji_1f4e6
    "music" -> Res.drawable.ic_twemoji_1f39f
    "fitness" -> Res.drawable.ic_twemoji_1f3c6
    "travel" -> Res.drawable.ic_twemoji_2708
    else -> Res.drawable.ic_twemoji_1f4bb
}

/** Displays a project and its server-stored key tail; full keys are revealed only on creation or regeneration. */
@Composable
private fun ProjectCard(
    project: ApiProject,
    isAdmin: Boolean,
    canRegenerate: Boolean,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    DashboardCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(projectPresetIcon(project.iconKey)), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (!project.description.isNullOrBlank()) Text(project.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            val status = when (project.activityStatus) {
                "receiving_data" -> stringResource(Res.string.project_activity_receiving)
                "no_recent_data" -> stringResource(Res.string.project_activity_stale)
                else -> stringResource(Res.string.project_activity_waiting)
            }
            Pill(status, if (project.activityStatus == "receiving_data") PillVariant.Lime else PillVariant.Neutral)
            if (project.platforms.isNotEmpty()) {
                Text(stringResource(Res.string.project_platforms_observed, project.platforms.joinToString(" · ") { value -> value.replaceFirstChar { it.uppercase() } }),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (project.sdkVersions.isNotEmpty()) Text(stringResource(Res.string.project_sdk_versions, project.sdkVersions.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (project.lastReceivedAt != null) {
                Text(stringResource(Res.string.project_platform_coverage_private), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (project.declaredPlatforms.isNotEmpty()) Text(stringResource(Res.string.project_platforms_configured,
                project.declaredPlatforms.joinToString(" · ") { it.replaceFirstChar { c -> c.uppercase() } }),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            project.lastReceivedAt?.let { Text(stringResource(Res.string.project_last_received, formatDateOnly(it)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

            Text(stringResource(Res.string.management_key_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.projects_created, formatDateOnly(project.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpen, modifier = Modifier.handCursor()) {
                    MaterialText(stringResource(Res.string.projects_open_dashboard))
                    Spacer(Modifier.width(6.dp))
                    Icon(painterResource(Res.drawable.ic_arrow_right), null, modifier = Modifier.size(16.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isAdmin) {
                    TextButton(onClick = onEdit, modifier = Modifier.handCursor()) {
                        MaterialText(stringResource(Res.string.projects_edit))
                    }
                }
                if (canRegenerate) {
                    TextButton(onClick = onRegenerate, modifier = Modifier.handCursor()) {
                        Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        MaterialText(stringResource(Res.string.action_regenerate))
                    }
                }
                if (isAdmin) {
                    TextButton(onClick = onDelete, modifier = Modifier.handCursor()) {
                        Icon(painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        MaterialText(stringResource(Res.string.projects_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            }
        }
    }
}
