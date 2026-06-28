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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiManagedUser
import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.access_add_user
import com.quietmetrix.dashboard.resources.access_empty
import com.quietmetrix.dashboard.resources.access_hint
import com.quietmetrix.dashboard.resources.access_pick_project
import com.quietmetrix.dashboard.resources.access_remove
import com.quietmetrix.dashboard.resources.access_title
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_plus
import com.quietmetrix.dashboard.resources.ic_users
import com.quietmetrix.dashboard.resources.role_admin
import com.quietmetrix.dashboard.resources.role_developer
import com.quietmetrix.dashboard.resources.role_reviewer
import com.quietmetrix.dashboard.resources.status_active
import com.quietmetrix.dashboard.resources.status_invited
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.users_email_label
import com.quietmetrix.dashboard.resources.users_empty
import com.quietmetrix.dashboard.resources.users_invite_action
import com.quietmetrix.dashboard.resources.users_invite_cancel
import com.quietmetrix.dashboard.resources.users_invite_send
import com.quietmetrix.dashboard.resources.users_remove
import com.quietmetrix.dashboard.resources.users_remove_confirm_body
import com.quietmetrix.dashboard.resources.users_remove_confirm_title
import com.quietmetrix.dashboard.resources.users_role_label
import com.quietmetrix.dashboard.resources.users_title
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.nav.NavDestination as AppTab

private val ROLE_OPTIONS = listOf(UserRole.ADMIN, UserRole.DEVELOPER, UserRole.REVIEWER)

@Composable
internal fun roleLabel(role: String): String = when (role) {
    UserRole.ADMIN -> stringResource(Res.string.role_admin)
    UserRole.DEVELOPER -> stringResource(Res.string.role_developer)
    else -> stringResource(Res.string.role_reviewer)
}

@Composable
fun UsersTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    var showInvite by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<ApiManagedUser?>(null) }

    LaunchedEffect(Unit) { viewModel.loadUsers() }

    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.users_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { showInvite = true }, modifier = Modifier.handCursor()) {
                    Icon(painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.users_invite_action))
                }
                IconButton(onClick = { viewModel.showTabInfo(AppTab.Users) }, modifier = Modifier.handCursor()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = stringResource(Res.string.tab_info_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (state.users.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(Res.drawable.ic_users, Res.string.users_empty, Modifier.padding(16.dp))
                }
            } else {
                state.users.forEach { user ->
                    UserRow(
                        user = user,
                        onRoleChange = { viewModel.updateUserRole(user.id, it) },
                        onRemove = { pendingRemove = user },
                    )
                }
            }

            HorizontalDivider()
            ProjectAccessSection(viewModel, state)
        }
    }

    if (showInvite) {
        InviteUserDialog(
            onDismiss = { showInvite = false },
            onInvite = { email, role ->
                viewModel.inviteUser(email, role)
                showInvite = false
            },
        )
    }

    pendingRemove?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(Res.string.users_remove_confirm_title)) },
            text = { Text(stringResource(Res.string.users_remove_confirm_body, user.email)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUser(user.id)
                        pendingRemove = null
                    },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.users_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.users_invite_cancel))
                }
            },
        )
    }
}

@Composable
private fun UserRow(user: ApiManagedUser, onRoleChange: (String) -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                val statusRes = if (user.status == "invited") Res.string.status_invited else Res.string.status_active
                Text(
                    stringResource(statusRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RoleDropdown(current = user.role, onSelect = onRoleChange)
            IconButton(onClick = onRemove, modifier = Modifier.handCursor()) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.users_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RoleDropdown(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.handCursor()) {
            Text(roleLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ROLE_OPTIONS.forEach { role ->
                DropdownMenuItem(
                    text = { Text(roleLabel(role)) },
                    onClick = {
                        expanded = false
                        if (role != current) onSelect(role)
                    },
                    modifier = Modifier.handCursor(),
                )
            }
        }
    }
}

@Composable
private fun InviteUserDialog(onDismiss: () -> Unit, onInvite: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.REVIEWER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.users_invite_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.users_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.users_role_label), style = MaterialTheme.typography.labelLarge)
                    RoleDropdown(current = role, onSelect = { role = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (email.isNotBlank()) onInvite(email.trim(), role) },
                enabled = email.isNotBlank(),
                modifier = Modifier.handCursor(),
            ) { Text(stringResource(Res.string.users_invite_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.users_invite_cancel))
            }
        },
    )
}

/** Pick a project, then assign/unassign users to it (admin only). */
@Composable
private fun ProjectAccessSection(viewModel: DashboardViewModel, state: DashboardState) {
    var selectedProjectId by remember(state.projects) {
        mutableStateOf(state.projects.firstOrNull()?.id)
    }
    LaunchedEffect(selectedProjectId) {
        selectedProjectId?.let { viewModel.loadMembers(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.access_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(Res.string.access_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Project picker
        var projectExpanded by remember { mutableStateOf(false) }
        val selectedProject = state.projects.firstOrNull { it.id == selectedProjectId }
        Box {
            OutlinedButton(onClick = { projectExpanded = true }, modifier = Modifier.handCursor()) {
                Text(selectedProject?.name ?: stringResource(Res.string.access_pick_project))
            }
            DropdownMenu(expanded = projectExpanded, onDismissRequest = { projectExpanded = false }) {
                state.projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = {
                            selectedProjectId = project.id
                            projectExpanded = false
                        },
                        modifier = Modifier.handCursor(),
                    )
                }
            }
        }

        val projectId = selectedProjectId
        if (projectId != null) {
            val members = state.members[projectId].orEmpty()
            val memberIds = members.map { it.userId }.toSet()
            // Assignable: non-admin users not already members.
            val assignable = state.users.filter { it.role != UserRole.ADMIN && it.id !in memberIds }

            // Add-user dropdown
            var addExpanded by remember(projectId) { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { addExpanded = true },
                    enabled = assignable.isNotEmpty(),
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(painterResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.access_add_user))
                }
                DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                    assignable.forEach { user ->
                        DropdownMenuItem(
                            text = {
                                val label = user.email + "  ·  " + roleLabel(user.role)
                                Text(label)
                            },
                            onClick = {
                                addExpanded = false
                                viewModel.assignMember(projectId, user.email)
                            },
                            modifier = Modifier.handCursor(),
                        )
                    }
                }
            }

            if (members.isEmpty()) {
                Text(
                    stringResource(Res.string.access_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                members.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(member.email, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        member.userRole?.let { AssistChip(onClick = {}, label = { Text(roleLabel(it)) }) }
                        TextButton(
                            onClick = { viewModel.unassignMember(projectId, member.userId) },
                            modifier = Modifier.handCursor(),
                        ) { Text(stringResource(Res.string.access_remove), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
