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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.quietmetrix.dashboard.viewmodel.isLoading
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.ui.components.SectionLoading
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
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
import com.quietmetrix.dashboard.resources.team_invite_hint
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.access_add_user
import com.quietmetrix.dashboard.resources.access_empty
import com.quietmetrix.dashboard.resources.access_hint
import com.quietmetrix.dashboard.resources.team_role_explainer
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
import com.quietmetrix.dashboard.viewmodel.AccessState
import com.quietmetrix.dashboard.viewmodel.buildAccessMatrix
import com.quietmetrix.dashboard.viewmodel.canToggleProjectAccess
import com.quietmetrix.dashboard.viewmodel.canRemoveAdminRole
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
    LaunchedEffect(state.projects) { state.projects.forEach { viewModel.loadMembers(it.id) } }

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

            state.sectionErrors[DataSection.Users]?.let { InlineErrorNotice(it, onRetry = { viewModel.loadUsers() }) }
            if (state.isLoading(DataSection.Users) && state.users.isEmpty()) {
                SectionLoading()
            } else if (state.users.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        state.user?.let { user ->
                            UserRow(ApiManagedUser(user.id, user.email, user.role), onRoleChange = {}, roleEnabled = false, removeEnabled = false, onRemove = {})
                        }
                        Text(stringResource(Res.string.team_invite_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                state.users.forEach { user ->
                    UserRow(
                        user = user,
                        onRoleChange = { viewModel.updateUserRole(user.id, it) },
                        roleEnabled = canRemoveAdminRole(user, state.users),
                        removeEnabled = canRemoveAdminRole(user, state.users),
                        onRemove = { pendingRemove = user },
                    )
                }
            }

            HorizontalDivider()
            if (state.isAdmin) AccessMatrixSection(viewModel, state)
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
private fun UserRow(user: ApiManagedUser, onRoleChange: (String) -> Unit, roleEnabled: Boolean, removeEnabled: Boolean, onRemove: () -> Unit) {
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
            RoleDropdown(current = user.role, onSelect = onRoleChange, enabled = roleEnabled)
            IconButton(onClick = onRemove, enabled = removeEnabled, modifier = Modifier.handCursor()) {
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
private fun RoleDropdown(current: String, onSelect: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.handCursor()) {
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

/** Renders the admin's person-by-project access matrix; membership writes use the existing server routes. */
@Composable
private fun AccessMatrixSection(viewModel: DashboardViewModel, state: DashboardState) {
    val rows = buildAccessMatrix(state.users, state.projects, state.members)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.access_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.access_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.team_role_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.projects.isEmpty()) {
            Text(stringResource(Res.string.access_empty), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.users_email_label), Modifier.width(220.dp), style = MaterialTheme.typography.labelMedium)
            state.projects.forEach { project -> Text(project.name, Modifier.width(150.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium) }
        }
        rows.forEach { row ->
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Text(row.user.email, Modifier.width(220.dp).padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                state.projects.forEach { project ->
                    val access = row.accessByProject[project.id] ?: AccessState.Loading
                    val checked = access == AccessState.Granted
                    val isOwner = project.ownerUserId == row.user.id
                    val canToggle = state.isAdmin && access != AccessState.Loading && canToggleProjectAccess(row.user.id, state.user?.id, isOwner)
                    Row(Modifier.width(150.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            enabled = canToggle,
                            onCheckedChange = { granted ->
                                if (granted) viewModel.assignMember(project.id, row.user.email)
                                else viewModel.unassignMember(project.id, row.user.id)
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
