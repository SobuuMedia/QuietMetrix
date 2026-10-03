package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
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
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.viewmodel.AskMessageRole
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import org.jetbrains.compose.resources.stringResource

/** Global right-side chat drawer for privacy-safe computed analytics answers. */
@Composable
internal fun AskDrawer(viewModel: DashboardViewModel, state: DashboardState) {
    val chat = state.askDrawer
    if (!chat.open) return
    val projectName = state.projects.firstOrNull { it.id == state.currentProjectId }?.name ?: stringResource(Res.string.value_none)
    DrawerShell(
        title = stringResource(Res.string.ask_drawer_title),
        onDismiss = { viewModel.closeAskDrawer() },
        footer = {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = chat.draft,
                    onValueChange = viewModel::editAskDraft,
                    label = { Text(stringResource(Res.string.ask_drawer_input)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                )
                Button(onClick = { viewModel.askQuestion() }, enabled = !chat.loading && chat.draft.isNotBlank()) {
                    Text(stringResource(Res.string.ask_drawer_send))
                }
            }
        },
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.ask_drawer_scope, projectName, stringResource(state.range.labelRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (chat.messages.isEmpty()) {
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
                            AskMessageRole.User -> MaterialTheme.colorScheme.secondaryContainer
                            AskMessageRole.Error -> MaterialTheme.colorScheme.errorContainer
                            AskMessageRole.Answer -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(0.88f),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(message.text, style = MaterialTheme.typography.bodyMedium)
                            message.source?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (message.role == AskMessageRole.Error) {
                                TextButton(onClick = { viewModel.retryAskQuestion() }) { Text(stringResource(Res.string.ask_drawer_retry)) }
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
    AssistChip(onClick = { viewModel.askQuestion(text) }, label = { Text(text) })
}
