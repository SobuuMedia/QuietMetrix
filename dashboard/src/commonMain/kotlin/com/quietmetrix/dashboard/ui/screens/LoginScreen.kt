package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.brand
import com.quietmetrix.dashboard.resources.ic_brand
import com.quietmetrix.dashboard.resources.login_access_hint
import com.quietmetrix.dashboard.resources.login_button
import com.quietmetrix.dashboard.resources.login_email_label
import com.quietmetrix.dashboard.resources.login_intro
import com.quietmetrix.dashboard.resources.login_password_label
import com.quietmetrix.dashboard.resources.login_tagline
import com.quietmetrix.dashboard.resources.login_title
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.ui.components.handCursor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    loading: Boolean = false,
    errorRes: StringResource? = null,
    onLogin: (email: String, password: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val extended = LocalExtendedColors.current
    val focusManager = LocalFocusManager.current
    val canSubmit = !loading && email.isNotBlank() && password.isNotEmpty()
    val submit = { if (canSubmit) onLogin(email.trim(), password) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        val compact = maxWidth < 480.dp
        // The scroll container keeps the form reachable on short windows and with a keyboard open.
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = 40.dp)
                .heightIn(min = (maxHeight - 80.dp).coerceAtLeast(0.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_brand),
                contentDescription = stringResource(Res.string.brand),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(Res.string.brand),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = if (compact) 38.sp else 56.sp,
                    lineHeight = if (compact) 46.sp else 64.sp,
                ),
                color = colors.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.login_tagline),
                modifier = Modifier.widthIn(max = 420.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Surface(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                shape = QmShapeTokens.card,
                color = colors.surface,
                border = BorderStroke(1.dp, extended.hairline),
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) 24.dp else 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(Res.string.login_title),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            stringResource(Res.string.login_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(Res.string.login_email_label)) },
                        singleLine = true,
                        enabled = !loading,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.login_password_label)) },
                        singleLine = true,
                        enabled = !loading,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (errorRes != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colors.errorContainer,
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(errorRes),
                                modifier = Modifier.padding(12.dp),
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Button(
                        onClick = { submit() },
                        enabled = canSubmit,
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extended.lime,
                            contentColor = extended.onLime,
                            disabledContainerColor = extended.lime.copy(alpha = 0.35f),
                            disabledContentColor = colors.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.onSurface,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            MaterialText(stringResource(Res.string.login_button), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.login_access_hint),
                modifier = Modifier.widthIn(max = 400.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
