package com.quietmetrix.dashboard.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.quietmetrix.dashboard.viewmodel.isLoading
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.ui.components.SectionLoading
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.ic_projects
import com.quietmetrix.dashboard.resources.ic_refresh
import com.quietmetrix.dashboard.resources.projects_created
import com.quietmetrix.dashboard.resources.projects_delete
import com.quietmetrix.dashboard.resources.projects_delete_confirm_body
import com.quietmetrix.dashboard.resources.projects_delete_confirm_title
import com.quietmetrix.dashboard.resources.projects_your
import com.quietmetrix.dashboard.resources.regenerate_confirm_body
import com.quietmetrix.dashboard.resources.regenerate_confirm_title
import com.quietmetrix.dashboard.resources.state_no_projects
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.nav.NavDestination as AppTab

// ---------------------------------------------------------------------------
// Projects tab
// ---------------------------------------------------------------------------

@Composable
internal fun ProjectsTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ApiProject?>(null) }

    var pendingRegenerate by remember { mutableStateOf<ApiProject?>(null) }

    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

            state.sectionErrors[DataSection.Projects]?.let { InlineErrorNotice(it, onRetry = { viewModel.refresh() }) }
            if (state.isLoading(DataSection.Projects) && state.projects.isEmpty()) {
                SectionLoading()
            } else if (state.projects.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
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
