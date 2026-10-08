@file:Suppress("DEPRECATION") // Clipboard API shared with the WASM target.

package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ask_drawer_exhausted
import com.quietmetrix.dashboard.resources.ask_drawer_input
import com.quietmetrix.dashboard.resources.ask_drawer_loading
import com.quietmetrix.dashboard.resources.ask_drawer_retry
import com.quietmetrix.dashboard.resources.ask_drawer_scope
import com.quietmetrix.dashboard.resources.ask_drawer_send
import com.quietmetrix.dashboard.resources.ask_drawer_suggestion_retention
import com.quietmetrix.dashboard.resources.ask_drawer_suggestion_screen
import com.quietmetrix.dashboard.resources.ask_drawer_suggestion_search
import com.quietmetrix.dashboard.resources.ask_drawer_suggestion_sessions
import com.quietmetrix.dashboard.resources.ask_drawer_title
import com.quietmetrix.dashboard.resources.ask_drawer_unavailable
import com.quietmetrix.dashboard.resources.drill_copy
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.viewmodel.AskMessageRole
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/** Global right-side chat drawer for privacy-safe computed analytics answers. */
@Composable
internal fun AskDrawer(viewModel: DashboardViewModel, state: DashboardState) {
    val colors = LocalExtendedColors.current
    val clipboard = LocalClipboardManager.current
    val chat = state.askDrawer
    if (!chat.open) return
    val projectName = state.projects.firstOrNull { it.id == state.currentProjectId }?.name ?: stringResource(Res.string.value_none)
    DrawerShell(
        title = stringResource(Res.string.ask_drawer_title),
        onDismiss = { viewModel.closeAskDrawer() },
        leading = {
            Box(Modifier.padding(end = 10.dp).size(36.dp).background(colors.lavender, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                EmojiVector("✨", Modifier.size(20.dp), contentDescription = null)
            }
        },
        footer = if (state.ai.enabled) {{
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = chat.draft,
                    onValueChange = viewModel::editAskDraft,
                    label = { Text(stringResource(Res.string.ask_drawer_input)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                )
                Button(onClick = { viewModel.askQuestion() }, enabled = state.ai.available && !chat.loading && chat.draft.isNotBlank(), modifier = Modifier.handCursor()) {
                    MaterialText(stringResource(Res.string.ask_drawer_send))
                }
            }
        }} else null,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.ask_drawer_scope, projectName, stringResource(state.range.labelRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.ai.enabled) {
                Text(stringResource(Res.string.ask_drawer_unavailable), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!state.ai.available) {
                Text(stringResource(Res.string.ask_drawer_exhausted, state.ai.reset_date), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.ai.available && chat.messages.isEmpty()) {
                Suggestion(Res.string.ask_drawer_suggestion_sessions, viewModel)
                Suggestion(Res.string.ask_drawer_suggestion_search, viewModel)
                Suggestion(Res.string.ask_drawer_suggestion_retention, viewModel)
                Suggestion(Res.string.ask_drawer_suggestion_screen, viewModel)
            }
            chat.messages.forEach { message ->
                val isUser = message.role == AskMessageRole.User
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = when (message.role) {
                            AskMessageRole.User -> colors.lavender
                            AskMessageRole.Error -> MaterialTheme.colorScheme.errorContainer
                            AskMessageRole.Answer -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(0.88f),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(message.errorRes?.let { stringResource(it) } ?: message.text, style = MaterialTheme.typography.bodyMedium, color = if (isUser) colors.onLime else MaterialTheme.colorScheme.onSurface)
                            if (message.role == AskMessageRole.Answer) TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.drill_copy)) }
                            message.source?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (message.role == AskMessageRole.Error) {
                                TextButton(onClick = { viewModel.retryAskQuestion() }, enabled = state.ai.available, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.ask_drawer_retry)) }
                            }
                        }
                    }
                }
            }
            if (chat.loading) Text(stringResource(Res.string.ask_drawer_loading), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Suggestion(label: org.jetbrains.compose.resources.StringResource, viewModel: DashboardViewModel) {
    val text = stringResource(label)
    AssistChip(onClick = { viewModel.editAskDraft(text) },  label = { MaterialText(text) }, modifier = Modifier.handCursor())
}
