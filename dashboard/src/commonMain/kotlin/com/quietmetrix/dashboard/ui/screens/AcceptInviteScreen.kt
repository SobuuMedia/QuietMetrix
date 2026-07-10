package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.brand
import com.quietmetrix.dashboard.resources.invite_accept_subtitle
import com.quietmetrix.dashboard.resources.invite_accept_title
import com.quietmetrix.dashboard.resources.invite_back_to_login
import com.quietmetrix.dashboard.resources.invite_confirm_password
import com.quietmetrix.dashboard.resources.invite_invalid_token
import com.quietmetrix.dashboard.resources.invite_loading
import com.quietmetrix.dashboard.resources.invite_password_mismatch
import com.quietmetrix.dashboard.resources.invite_password_too_short
import com.quietmetrix.dashboard.resources.invite_set_password
import com.quietmetrix.dashboard.resources.invite_submit
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import org.jetbrains.compose.resources.stringResource

/**
 * Dedicated "accept invitation" page. Opened when the dashboard is loaded with a
 * `?invite=<token>` link. The invited user must set a password here before they
 * can access the dashboard.
 */
@Composable
fun AcceptInviteScreen(
    inviteToken: String,
    state: DashboardState,
    onLoad: (String) -> Unit,
    onAccept: (token: String, password: String) -> Unit,
    onBackToLogin: () -> Unit,
) {
    LaunchedEffect(inviteToken) { onLoad(inviteToken) }

    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.widthIn(min = 280.dp, max = 380.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.brand), style = MaterialTheme.typography.titleLarge)

                when {
                    state.inviteTokenInvalid -> {
                        Text(
                            stringResource(Res.string.invite_invalid_token),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onBackToLogin, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.invite_back_to_login))
                        }
                    }

                    state.inviteEmail == null -> {
                        Text(
                            stringResource(Res.string.invite_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    }

                    else -> {
                        Text(
                            stringResource(Res.string.invite_accept_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(Res.string.invite_accept_subtitle, state.inviteEmail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val error = validateInvitePassword(password, confirm)
                        val tooShort = password.isNotEmpty() && error == InvitePasswordError.TOO_SHORT
                        val mismatch = confirm.isNotEmpty() && error == InvitePasswordError.MISMATCH

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(Res.string.invite_set_password)) },
                            singleLine = true,
                            isError = tooShort,
                            enabled = !state.inviteSubmitting,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = { Text(stringResource(Res.string.invite_confirm_password)) },
                            singleLine = true,
                            isError = mismatch,
                            enabled = !state.inviteSubmitting,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (tooShort) {
                            Text(
                                stringResource(Res.string.invite_password_too_short),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (mismatch) {
                            Text(
                                stringResource(Res.string.invite_password_mismatch),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        val valid = error == null
                        Button(
                            onClick = { onAccept(inviteToken, password) },
                            enabled = valid && !state.inviteSubmitting,
                            modifier = Modifier.fillMaxWidth().handCursor(),
                        ) {
                            if (state.inviteSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(Res.string.invite_submit))
                            }
                        }
                        TextButton(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth().handCursor()) {
                            Text(stringResource(Res.string.invite_back_to_login))
                        }
                    }
                }
            }
        }
    }
}
