@file:Suppress("DEPRECATION") // Clipboard API shared with the WASM target.

package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_copy_api_key
import com.quietmetrix.dashboard.resources.action_got_it
import com.quietmetrix.dashboard.resources.api_key_label
import com.quietmetrix.dashboard.resources.ic_copy
import com.quietmetrix.dashboard.resources.invite_link_body
import com.quietmetrix.dashboard.resources.invite_link_title
import com.quietmetrix.dashboard.resources.project_created_api_key_label
import com.quietmetrix.dashboard.resources.project_created_key_warning
import com.quietmetrix.dashboard.resources.project_created_title
import com.quietmetrix.dashboard.resources.regenerated_key_title
import com.quietmetrix.dashboard.resources.snackbar_api_key_copied
import com.quietmetrix.dashboard.resources.snackbar_invite_link_copied
import com.quietmetrix.dashboard.resources.snackbar_token_copied
import com.quietmetrix.dashboard.resources.tab_info_flow
import com.quietmetrix.dashboard.resources.tab_info_experiments
import com.quietmetrix.dashboard.resources.tab_info_funnels
import com.quietmetrix.dashboard.resources.tab_info_overview
import com.quietmetrix.dashboard.resources.tab_info_projects
import com.quietmetrix.dashboard.resources.tab_info_retention
import com.quietmetrix.dashboard.resources.tab_info_settings
import com.quietmetrix.dashboard.resources.tab_info_users
import com.quietmetrix.dashboard.resources.tokens_created_title
import com.quietmetrix.dashboard.resources.tokens_created_warning
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.nav.NavDestination as AppTab

/** Shared one-time credential reveals and destination help dialogs. */
@Composable
internal fun DashboardDialogs(viewModel: DashboardViewModel, state: DashboardState, snackbarState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
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
