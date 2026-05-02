package com.quietmetrix.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.quietmetrix.dashboard.storage.TokenStorage
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.ui.screens.DashboardScreen
import com.quietmetrix.dashboard.ui.screens.LoginScreen
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@Composable
fun App(apiBaseUrl: String) {
    QuietMetrixTheme {
        val viewModel = remember { DashboardViewModel() }
        LaunchedEffect(Unit) {
            viewModel.init(apiBaseUrl)
            TokenStorage.get()?.let { viewModel.restoreSession(it) }
        }
        val state by viewModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        if (state.token == null) {
            LoginScreen(
                loading = state.loading,
                errorRes = state.loginErrorRes,
                onLogin = { email, password ->
                    scope.launch { viewModel.login(email, password) }
                },
            )
        } else {
            DashboardScreen(viewModel, state)
        }
    }
}
