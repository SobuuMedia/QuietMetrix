package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.api.ApiManagedUser
import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.access_empty
import com.quietmetrix.dashboard.resources.access_hint
import com.quietmetrix.dashboard.resources.access_title
import com.quietmetrix.dashboard.resources.action_copy
import com.quietmetrix.dashboard.resources.ic_delete
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_refresh
import com.quietmetrix.dashboard.resources.management_invite_invalid
import com.quietmetrix.dashboard.resources.management_invite_link
import com.quietmetrix.dashboard.resources.management_invite_link_hint
import com.quietmetrix.dashboard.resources.management_invite_send
import com.quietmetrix.dashboard.resources.management_invite_sending
import com.quietmetrix.dashboard.resources.management_invite_title
import com.quietmetrix.dashboard.resources.role_admin
import com.quietmetrix.dashboard.resources.role_developer
import com.quietmetrix.dashboard.resources.role_reviewer
import com.quietmetrix.dashboard.resources.status_active
import com.quietmetrix.dashboard.resources.status_invited
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.team_hero_after
import com.quietmetrix.dashboard.resources.team_hero_before
import com.quietmetrix.dashboard.resources.team_hero_before_one
import com.quietmetrix.dashboard.resources.team_hero_highlight
import com.quietmetrix.dashboard.resources.team_invite_hint
import com.quietmetrix.dashboard.resources.team_new_organization
import com.quietmetrix.dashboard.resources.team_project_access
import com.quietmetrix.dashboard.resources.team_recover_account
import com.quietmetrix.dashboard.resources.team_role_explainer
import com.quietmetrix.dashboard.resources.team_you
import com.quietmetrix.dashboard.resources.users_email_label
import com.quietmetrix.dashboard.resources.users_remove
import com.quietmetrix.dashboard.resources.users_remove_confirm_body
import com.quietmetrix.dashboard.resources.users_remove_confirm_title
import com.quietmetrix.dashboard.resources.users_role_label
import com.quietmetrix.dashboard.resources.users_title
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.ConfirmDialog
import com.quietmetrix.dashboard.ui.components.DashboardCard
import com.quietmetrix.dashboard.ui.components.EmojiVector
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.SectionLoading
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.AccessState
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.buildAccessMatrix
import com.quietmetrix.dashboard.viewmodel.canRemoveAdminRole
import com.quietmetrix.dashboard.viewmodel.canToggleProjectAccess
import com.quietmetrix.dashboard.viewmodel.isLoading
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.nav.NavDestination as AppTab
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/** Global role choices accepted by both backends. */
private val ROLE_OPTIONS = listOf(UserRole.ADMIN, UserRole.DEVELOPER, UserRole.REVIEWER)

/** Resolves the localized label for a server role. */
@Composable
internal fun roleLabel(role: String): String = when (role) {
    UserRole.ADMIN -> stringResource(Res.string.role_admin)
    UserRole.DEVELOPER -> stringResource(Res.string.role_developer)
    else -> stringResource(Res.string.role_reviewer)
}

/** Loads management lists and routes actions to the authenticated view model. */
@Composable
fun UsersTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    LaunchedEffect(state.isAdmin) { if (state.isAdmin) viewModel.loadUsers() }
    LaunchedEffect(state.projects, state.isAdmin) { if (state.isAdmin) state.projects.forEach { viewModel.loadMembers(it.id) } }
    TeamContent(state, sizeClass, viewModel::inviteUser, viewModel::updateUserRole, viewModel::deleteUser,
        viewModel::assignMember, viewModel::unassignMember, viewModel::loadUsers, { viewModel.showTabInfo(AppTab.Users) }, onInviteOrganization = viewModel::inviteOrganization, onRecover = viewModel::recoverUser)
}

/** Displays the access matrix beside invitations, stacking both panels on narrow viewports. */
@Composable
internal fun TeamContent(
    state: DashboardState,
    sizeClass: WindowSizeClass,
    onInvite: (String, String, List<String>) -> Unit,
    onRoleChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAssign: (String, String) -> Unit,
    onUnassign: (String, String) -> Unit,
    onRetry: () -> Unit,
    onInfo: () -> Unit = {},
    onInviteOrganization: (String) -> Unit = {},
    onRecover: ((String) -> Unit)? = null,
) {
    var pendingRemove by remember { mutableStateOf<ApiManagedUser?>(null) }
    MaxWidthContainer(sizeClass = sizeClass, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmojiVector("👥", Modifier.size(20.dp))
                Text(stringResource(Res.string.users_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = onInfo, modifier = Modifier.handCursor()) { Icon(painterResource(Res.drawable.ic_info), stringResource(Res.string.tab_info_content_description), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val teamSize = state.users.size.coerceAtLeast(if (state.user == null) 0 else 1)
            val extended = LocalExtendedColors.current
            val heroBefore = if (teamSize == 1) stringResource(Res.string.team_hero_before_one)
                else stringResource(Res.string.team_hero_before, teamSize)
            val heroHighlight = stringResource(Res.string.team_hero_highlight)
            val heroAfter = stringResource(Res.string.team_hero_after)
            Text(buildAnnotatedString {
                    append(heroBefore)
                    withStyle(SpanStyle(background = extended.lime, color = extended.onLime)) {
                        append(heroHighlight)
                    }
                    append(heroAfter)
                },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (sizeClass == WindowSizeClass.Compact) 30.sp else 48.sp,
                    lineHeight = if (sizeClass == WindowSizeClass.Compact) 36.sp else 54.sp,
                    letterSpacing = (-1).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground)
            state.sectionErrors[DataSection.Users]?.let { InlineErrorNotice(it, onRetry) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val people: @Composable (Modifier) -> Unit = { modifier ->
                    DashboardCard(modifier) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.isLoading(DataSection.Users) && state.users.isEmpty()) SectionLoading()
                            else if (state.users.isEmpty()) {
                                state.user?.let { user -> UserRow(ApiManagedUser(user.id, user.email, user.role), {}, false, false, {}) }
                                Text(stringResource(Res.string.team_invite_hint), style = MaterialTheme.typography.bodyMedium)
                            } else if (state.isAdmin) {
                                AccessMatrixSection(state, onRoleChange, { pendingRemove = it }, onAssign, onUnassign, onRecover)
                            } else {
                                state.users.forEach { user -> UserRow(user, {}, false, false, {}) }
                            }
                        }
                    }
                }
                if (state.isAdmin && maxWidth >= 760.dp && sizeClass != WindowSizeClass.Compact) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        people(Modifier.weight(1.6f))
                        InvitationPanel(state, onInvite, Modifier.weight(1f), onInviteOrganization)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        people(Modifier.fillMaxWidth())
                        if (state.isAdmin) InvitationPanel(state, onInvite, Modifier.fillMaxWidth(), onInviteOrganization)
                    }
                }
            }
        }
    }
    pendingRemove?.let { user ->
        ConfirmDialog("👥", stringResource(Res.string.users_remove_confirm_title), stringResource(Res.string.users_remove_confirm_body, user.email),
            stringResource(Res.string.users_remove), true, { onRemove(user.id); pendingRemove = null }, { pendingRemove = null })
    }
}

/** Shows one person's status and role while respecting the supplied action guards. */
@Composable
private fun UserRow(user: ApiManagedUser, onRoleChange: (String) -> Unit, roleEnabled: Boolean, removeEnabled: Boolean, onRemove: () -> Unit) {
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val initials = user.email.substringBefore('@').split('.', '-', '_').filter(String::isNotBlank).take(2)
                .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Text(initials, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                val displayName = user.email.substringBefore('@').split('.', '-', '_').filter(String::isNotBlank)
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(displayName.ifBlank { user.email }, style = MaterialTheme.typography.bodyMedium)
                }
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Presents the server-supported global roles. */
@Composable
private fun RoleDropdown(current: String, onSelect: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.handCursor()) {
            MaterialText(roleLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ROLE_OPTIONS.forEach { role ->
                DropdownMenuItem(
                    text = { MaterialText(roleLabel(role)) },
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

/** Validates a person-specific invitation and exposes its actual server-issued share link. */
@Composable
private fun InvitationPanel(state: DashboardState, onInvite: (String, String, List<String>) -> Unit, modifier: Modifier, onInviteOrganization: (String) -> Unit) {
    var newOrganization by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.REVIEWER) }
    var selectedProjects by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(state.lastInviteLink) {
        if (state.lastInviteLink != null) {
            email = ""
            selectedProjects = emptySet()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
      DashboardCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmojiVector("💌", Modifier.size(24.dp))
                Text(stringResource(Res.string.management_invite_title), style = MaterialTheme.typography.headlineSmall)
            }
            val invalid = email.isNotBlank() && !isInvitationEmailValid(email)
            OutlinedTextField(email, { email = it }, label = { Text(stringResource(Res.string.users_email_label)) }, singleLine = true,
                isError = invalid, enabled = !state.invitationPending, modifier = Modifier.fillMaxWidth().testTag("invitation-email"),
                supportingText = { if (invalid) Text(stringResource(Res.string.management_invite_invalid)) })
            if (state.user?.platformOperator == true && !state.serverSelfhost) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(newOrganization, { newOrganization = it; if (it) { role = UserRole.ADMIN; selectedProjects = emptySet() } }, enabled = !state.invitationPending, modifier = Modifier.handCursor())
                    Text(stringResource(Res.string.team_new_organization))
                }
            }
            Text(stringResource(Res.string.users_role_label), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ROLE_OPTIONS.forEach { option ->
                    // Disabled pills are rendered without click handlers during submission.
                    Pill(roleLabel(option), if (role == option) PillVariant.Lavender else PillVariant.Neutral,
                        onClick = if (state.invitationPending || newOrganization) null else ({ role = option }))
                }
            }
            Text(stringResource(Res.string.team_project_access), style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.projects.forEach { project ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = project.id in selectedProjects,
                            onCheckedChange = { checked -> selectedProjects = if (checked) selectedProjects + project.id else selectedProjects - project.id },
                            enabled = !state.invitationPending && !newOrganization,
                            modifier = Modifier.handCursor().testTag("invite-project-${project.id}"),
                        )
                        Text(project.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(stringResource(Res.string.team_role_explainer), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { if (newOrganization) onInviteOrganization(email.trim()) else onInvite(email.trim(), role, selectedProjects.toList()) }, enabled = isInvitationEmailValid(email) && !state.invitationPending, modifier = Modifier.handCursor()) {
                MaterialText(stringResource(if (state.invitationPending) Res.string.management_invite_sending else Res.string.management_invite_send))
            }
        }
      }
      DashboardCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmojiVector("🔗", Modifier.size(20.dp))
                Text(stringResource(Res.string.management_invite_link), style = MaterialTheme.typography.titleSmall)
            }
            state.lastInviteLink?.let { CopyableManagementValue(it, stringResource(Res.string.action_copy)) }
                ?: Text(stringResource(Res.string.management_invite_link_hint), style = MaterialTheme.typography.bodySmall)
        }
      }
    }
}

/** Shares one horizontal scroll position across headings, roles and project access cells. */
@Composable
private fun AccessMatrixSection(
    state: DashboardState, onRoleChange: (String, String) -> Unit, onRemove: (ApiManagedUser) -> Unit,
    onAssign: (String, String) -> Unit, onUnassign: (String, String) -> Unit,
    onRecover: ((String) -> Unit)? = null,
) {
    val rows = buildAccessMatrix(state.users, state.projects, state.members)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EmojiVector("👥", Modifier.size(20.dp))
        Text(stringResource(Res.string.access_title), style = MaterialTheme.typography.titleSmall)
    }
    Text(stringResource(Res.string.access_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.users_email_label), Modifier.width(240.dp), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(Res.string.users_role_label), Modifier.width(110.dp), style = MaterialTheme.typography.labelMedium)
            state.projects.forEach { project ->
                Text(project.name.removePrefix("QuietMetrix Demo "), Modifier.width(72.dp).padding(4.dp),
                    style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(44.dp))
        }
        rows.forEach { row ->
            val own = row.user.id == state.user?.id
            val canChange = !own && canRemoveAdminRole(row.user, state.users)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(240.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val initials = row.user.email.substringBefore('@').split('.', '-', '_').filter(String::isNotBlank)
                        .take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Text(initials, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                    val displayName = row.user.email.substringBefore('@').split('.', '-', '_').filter(String::isNotBlank)
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(displayName.ifBlank { row.user.email }, style = MaterialTheme.typography.bodySmall)
                        if (row.user.id == state.user?.id) Pill(stringResource(Res.string.team_you), PillVariant.Lime)
                    }
                    Text(row.user.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (row.user.status == "invited") Res.string.status_invited else Res.string.status_active), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box(Modifier.width(110.dp)) { RoleDropdown(row.user.role, { onRoleChange(row.user.id, it) }, canChange) }
                state.projects.forEach { project ->
                    val access = row.accessByProject[project.id] ?: AccessState.Loading
                    val canToggle = access != AccessState.Loading && canToggleProjectAccess(row.user.id, state.user?.id, project.ownerUserId == row.user.id)
                    Box(Modifier.width(72.dp).padding(horizontal = 4.dp)) {
                        Checkbox(access == AccessState.Granted, enabled = canToggle, onCheckedChange = { granted ->
                            if (granted) onAssign(project.id, row.user.email) else onUnassign(project.id, row.user.id)
                        }, modifier = Modifier.handCursor().testTag("access-${row.user.id}-${project.id}"))
                    }
                }
                if (onRecover != null && row.user.status == "active") {
                    IconButton(onClick = { onRecover(row.user.id) }, enabled = !state.invitationPending, modifier = Modifier.size(44.dp).handCursor()) {
                        Icon(painterResource(Res.drawable.ic_refresh), stringResource(Res.string.team_recover_account), modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = { onRemove(row.user) }, enabled = canChange, modifier = Modifier.size(44.dp).handCursor()) {
                    Icon(painterResource(Res.drawable.ic_delete), stringResource(Res.string.users_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider()
        }
    }
    if (state.projects.isEmpty()) Text(stringResource(Res.string.access_empty), style = MaterialTheme.typography.bodySmall)
}
