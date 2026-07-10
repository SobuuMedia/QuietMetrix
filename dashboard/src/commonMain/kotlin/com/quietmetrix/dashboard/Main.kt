package com.quietmetrix.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.quietmetrix.dashboard.storage.TokenStorage
import com.quietmetrix.dashboard.theme.LocalThemeMode
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.theme.getThemeMode
import com.quietmetrix.dashboard.theme.resolveSystemThemeMode
import com.quietmetrix.dashboard.theme.saveThemeMode
import com.quietmetrix.dashboard.ui.screens.AcceptInviteScreen
import com.quietmetrix.dashboard.ui.screens.DashboardScreen
import com.quietmetrix.dashboard.ui.screens.LoginScreen
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@Composable
fun App(apiBaseUrl: String, inviteToken: String? = null) {
    var savedMode by remember { mutableStateOf(getThemeMode()) }
    val setMode: (ThemeMode) -> Unit = { newMode ->
        savedMode = newMode
        saveThemeMode(newMode)
    }
    val effective = if (savedMode == ThemeMode.System) resolveSystemThemeMode() else savedMode

    CompositionLocalProvider(
        LocalThemeMode provides savedMode,
        LocalThemeModeSetter provides setMode,
    ) {
        QuietMetrixTheme(themeMode = effective) {
            val viewModel = remember { DashboardViewModel() }
            LaunchedEffect(Unit) {
                viewModel.init(apiBaseUrl)
                TokenStorage.get()?.let { viewModel.restoreSession(it) }
            }
            val state by viewModel.state.collectAsState()
            val scope = rememberCoroutineScope()
            // Held in state so "back to sign in" can dismiss the invite flow even
            // though the ?invite= URL param itself persists.
            var pendingInvite by remember { mutableStateOf(inviteToken) }

            when {
                // An invite link gates entry: the new user must set a password
                // before they can reach the dashboard.
                pendingInvite != null && state.token == null -> {
                    AcceptInviteScreen(
                        inviteToken = pendingInvite!!,
                        state = state,
                        onLoad = { viewModel.loadInvite(it) },
                        onAccept = { token, password -> viewModel.acceptInvite(token, password) },
                        onBackToLogin = {
                            viewModel.cancelInvite()
                            pendingInvite = null
                        },
                    )
                }

                state.token == null -> {
                    LoginScreen(
                        loading = state.loading,
                        errorRes = state.loginErrorRes,
                        onLogin = { email, password ->
                            scope.launch { viewModel.login(email, password) }
                        },
                    )
                }

                else -> DashboardScreen(viewModel, state)
            }
        }
    }
}
