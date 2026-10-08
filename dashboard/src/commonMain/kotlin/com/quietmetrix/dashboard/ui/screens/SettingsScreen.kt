package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AccessTokenDto
import com.quietmetrix.dashboard.format.formatDateOnly
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_copy
import com.quietmetrix.dashboard.resources.ai_selfhost_instructions
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.management_api_checking
import com.quietmetrix.dashboard.resources.management_api_offline
import com.quietmetrix.dashboard.resources.management_api_online
import com.quietmetrix.dashboard.resources.management_api_unknown
import com.quietmetrix.dashboard.resources.management_check_connection
import com.quietmetrix.dashboard.resources.management_expiry
import com.quietmetrix.dashboard.resources.management_expiry_30
import com.quietmetrix.dashboard.resources.management_expiry_90
import com.quietmetrix.dashboard.resources.management_expiry_never
import com.quietmetrix.dashboard.resources.management_mcp_hint
import com.quietmetrix.dashboard.resources.management_mcp_title
import com.quietmetrix.dashboard.resources.management_server_url
import com.quietmetrix.dashboard.resources.management_token_create
import com.quietmetrix.dashboard.resources.management_version
import com.quietmetrix.dashboard.resources.server_mode_debug
import com.quietmetrix.dashboard.resources.server_mode_production
import com.quietmetrix.dashboard.resources.settings_about_body
import com.quietmetrix.dashboard.resources.settings_about_title
import com.quietmetrix.dashboard.resources.settings_appearance
import com.quietmetrix.dashboard.resources.settings_demo_explanation
import com.quietmetrix.dashboard.resources.settings_demo_title
import com.quietmetrix.dashboard.resources.settings_refresh
import com.quietmetrix.dashboard.resources.settings_server_mode
import com.quietmetrix.dashboard.resources.settings_theme_dark
import com.quietmetrix.dashboard.resources.settings_theme_light
import com.quietmetrix.dashboard.resources.settings_theme_system
import com.quietmetrix.dashboard.resources.settings_title
import com.quietmetrix.dashboard.resources.settings_twemoji_attribution
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.tokens_cancel_action
import com.quietmetrix.dashboard.resources.tokens_created_at_label
import com.quietmetrix.dashboard.resources.tokens_empty
import com.quietmetrix.dashboard.resources.tokens_hint
import com.quietmetrix.dashboard.resources.tokens_last_used_label
import com.quietmetrix.dashboard.resources.tokens_name_and_last4
import com.quietmetrix.dashboard.resources.tokens_name_label
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
import com.quietmetrix.dashboard.theme.DarkColors
import com.quietmetrix.dashboard.theme.LightColors
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.LocalThemeMode
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.ui.components.ConfirmDialog
import com.quietmetrix.dashboard.ui.components.DashboardCard
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.ModalFooter
import com.quietmetrix.dashboard.ui.components.ModalFooterAction
import com.quietmetrix.dashboard.ui.components.ModalFooterEmphasis
import com.quietmetrix.dashboard.ui.components.ModalShell
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.SectionLoading
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.ServerConnection
import com.quietmetrix.dashboard.viewmodel.isLoading
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

internal fun settingsColumnsLayoutFor(sizeClass: WindowSizeClass): WindowSizeClass =
    if (sizeClass == WindowSizeClass.Expanded) WindowSizeClass.Expanded else WindowSizeClass.Compact

/** Loads admin tokens and hosts settings using the authenticated view model. */
@Composable
internal fun SettingsTab(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    LaunchedEffect(state.isAdmin) { if (state.isAdmin) viewModel.loadTokens() }
    SettingsContent(state, viewModel::checkServerConnection, viewModel::refresh, viewModel::createToken,
        viewModel::revokeToken, viewModel::setDemoMode, viewModel::loadTokens, { viewModel.showTabInfo(NavDestination.Settings) }, sizeClass)
}

/** Renders appearance, tooling and server settings without performing network work during composition. */
@Composable
internal fun SettingsContent(
    state: DashboardState,
    onCheckConnection: () -> Unit,
    onRefresh: () -> Unit,
    onCreateToken: (String, List<String>, Long?) -> Unit,
    onRevokeToken: (String) -> Unit,
    onDemoMode: (Boolean) -> Unit,
    onRetryTokens: () -> Unit,
    onInfo: () -> Unit = {},
    sizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val uriHandler = LocalUriHandler.current
    MaxWidthContainer(sizeClass = sizeClass, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                IconButton(onClick = onInfo, modifier = Modifier.handCursor()) { Icon(org.jetbrains.compose.resources.painterResource(Res.drawable.ic_info), stringResource(Res.string.tab_info_content_description), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            ResponsiveRowOrColumn(sizeClass = settingsColumnsLayoutFor(sizeClass), spacing = 16.dp) {
                Column(Modifier.fillSlot(1.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.isAdmin) AccessTokensCard(state, onCreateToken, onRevokeToken, onRetryTokens)
                    McpConnectionCard(state, onCheckConnection)
                }
                Column(Modifier.fillSlot(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppearanceCard()
                    DashboardCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(Res.string.settings_server_mode,
                                stringResource(if (state.serverDebug) Res.string.server_mode_debug else Res.string.server_mode_production)), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(Res.string.management_server_url), style = MaterialTheme.typography.labelLarge)
                            CopyableManagementValue(state.serverBaseUrl, stringResource(Res.string.action_copy))
                            if (state.serverSelfhost) Text(stringResource(Res.string.ai_selfhost_instructions), style = MaterialTheme.typography.bodySmall)
                            if (state.serverDebug) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(state.demoMode, onDemoMode, modifier = Modifier.handCursor())
                                    Text(stringResource(Res.string.settings_demo_title))
                                }
                                Text(stringResource(Res.string.settings_demo_explanation), style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.settings_refresh)) }
                        }
                    }
                    DashboardCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.settings_about_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(Res.string.settings_about_body), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { uriHandler.openUri("https://github.com/jdecked/twemoji") }, modifier = Modifier.handCursor()) {
                                MaterialText(stringResource(Res.string.settings_twemoji_attribution))
                            }
                            state.serverVersion?.let { Text(stringResource(Res.string.management_version, it), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}

/** Shows measured API reachability and the real stdio MCP configuration with a token placeholder. */
@Composable
private fun McpConnectionCard(state: DashboardState, onCheckConnection: () -> Unit) {
    DashboardCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(Res.drawable.ic_info), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.management_mcp_title), style = MaterialTheme.typography.headlineSmall)
            }
            val label = when (state.serverConnection) {
                ServerConnection.Online -> Res.string.management_api_online
                ServerConnection.Offline -> Res.string.management_api_offline
                ServerConnection.Checking -> Res.string.management_api_checking
                ServerConnection.Unknown -> Res.string.management_api_unknown
            }
            Pill(stringResource(label), if (state.serverConnection == ServerConnection.Online) PillVariant.Lime else PillVariant.Neutral)
            Text(stringResource(Res.string.management_mcp_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CopyableManagementValue(mcpClientConfiguration(state.serverBaseUrl), stringResource(Res.string.action_copy))
            OutlinedButton(onClick = onCheckConnection, enabled = state.serverConnection != ServerConnection.Checking, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.management_check_connection)) }
        }
    }
}

/** Provides saved Light/Dark/System selection with previews of the actual theme palettes. */
@Composable
private fun AppearanceCard() {
    val mode = LocalThemeMode.current
    val setMode = LocalThemeModeSetter.current
    DashboardCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.settings_appearance), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeMode.entries.forEach { choice ->
                    val label = when (choice) {
                        ThemeMode.Light -> Res.string.settings_theme_light
                        ThemeMode.Dark -> Res.string.settings_theme_dark
                        ThemeMode.System -> Res.string.settings_theme_system
                    }
                    Surface(onClick = { setMode(choice) }, modifier = Modifier.weight(1f).handCursor().semantics { selected = mode == choice; role = Role.RadioButton }, shape = QmShapeTokens.card,
                        border = BorderStroke(if (mode == choice) 2.dp else 1.dp, if (mode == choice) LocalExtendedColors.current.lime else LocalExtendedColors.current.hairline)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth().height(42.dp)) {
                                if (choice != ThemeMode.Dark) Box(Modifier.weight(1f).fillMaxHeight().background(LightColors.background))
                                if (choice != ThemeMode.Light) Box(Modifier.weight(1f).fillMaxHeight().background(DarkColors.background))
                            }
                            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Renders real tokens and confirms revocation before invoking the backend action. */
@Composable
private fun AccessTokensCard(state: DashboardState, onCreate: (String, List<String>, Long?) -> Unit, onRevoke: (String) -> Unit, onRetry: () -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<AccessTokenDto?>(null) }
    DashboardCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.tokens_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Button(onClick = { showCreate = true }, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.tokens_new_action)) }
            }
            Text(stringResource(Res.string.tokens_hint), style = MaterialTheme.typography.bodySmall)
            state.sectionErrors[DataSection.Tokens]?.let { InlineErrorNotice(it, onRetry) }
            if (state.isLoading(DataSection.Tokens) && state.accessTokens.isEmpty()) SectionLoading()
            else if (state.accessTokens.isEmpty()) Text(stringResource(Res.string.tokens_empty), style = MaterialTheme.typography.bodySmall)
            else state.accessTokens.forEach { token ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(Res.string.tokens_name_and_last4, token.name, token.last4), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.tokens_scope_label, token.scopes.joinToString(", ")), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(Res.string.tokens_created_at_label, formatDateOnly(token.createdAt)), style = MaterialTheme.typography.labelSmall)
                        Text(if (token.lastUsedAt == null) stringResource(Res.string.tokens_never_used) else stringResource(Res.string.tokens_last_used_label, formatDateOnly(token.lastUsedAt)), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { pendingRevoke = token }, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.tokens_revoke_action), color = LocalExtendedColors.current.coral) }
                }
            }
        }
    }
    if (showCreate) CreateTokenDialog({ showCreate = false }) { name, scopes, expiry -> onCreate(name, scopes, expiry); showCreate = false }
    pendingRevoke?.let { token ->
        ConfirmDialog("🔑", stringResource(Res.string.tokens_revoke_action), token.name, stringResource(Res.string.tokens_revoke_action), true,
            { onRevoke(token.id); pendingRevoke = null }, { pendingRevoke = null })
    }
}

/** Collects name, scopes and a backend-supported expiry using the shared modal chrome. */
@Composable
internal fun CreateTokenDialog(onDismiss: () -> Unit, onCreate: (String, List<String>, Long?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var createProjects by remember { mutableStateOf(true) }
    var readProjects by remember { mutableStateOf(false) }
    var readAnalytics by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf<Long?>(null) }
    val scopes = buildList {
        if (createProjects) add("projects:create")
        if (readProjects) add("projects:read")
        if (readAnalytics) add("analytics:read")
    }
    ModalShell(stringResource(Res.string.tokens_new_action), onDismiss, footer = {
        ModalFooter(
            secondary = ModalFooterAction(stringResource(Res.string.tokens_cancel_action), ModalFooterEmphasis.Secondary, onDismiss),
            primary = ModalFooterAction(stringResource(Res.string.management_token_create), ModalFooterEmphasis.Primary) { onCreate(name.trim(), scopes, expiry) },
            primaryEnabled = name.isNotBlank() && scopes.isNotEmpty(),
        )
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.tokens_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.tokens_scope_picker_label), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(createProjects, { createProjects = it }, modifier = Modifier.handCursor()); Text(stringResource(Res.string.tokens_scope_create_projects)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(readProjects, { readProjects = it }, modifier = Modifier.handCursor()); Text(stringResource(Res.string.tokens_scope_read_projects)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(readAnalytics, { readAnalytics = it }, modifier = Modifier.handCursor()); Text(stringResource(Res.string.tokens_scope_read_analytics)) }
            Text(stringResource(Res.string.tokens_scope_read_analytics_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(Res.string.management_expiry), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to Res.string.management_expiry_never, 30L to Res.string.management_expiry_30, 90L to Res.string.management_expiry_90).forEach { (days, label) ->
                    FilterChip(expiry == days, { expiry = days }, label = { MaterialText(stringResource(label)) }, modifier = Modifier.handCursor())
                }
            }
        }
    }
}
