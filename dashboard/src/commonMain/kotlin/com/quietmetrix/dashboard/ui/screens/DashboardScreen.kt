package com.quietmetrix.dashboard.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.nav.NavDestination as AppTab

/** Routes dashboard destinations and composes the shared shell and dialogs. */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, state: DashboardState) {
    val snackbarState = remember { SnackbarHostState() }
    DashboardShell(
        viewModel = viewModel,
        state = state,
        snackbarState = snackbarState,
    ) { sizeClass ->
        when (state.activeDestination) {
            AppTab.Overview  -> OverviewScreen(viewModel, state, sizeClass)
            AppTab.Flow      -> FlowScreen(viewModel, state, sizeClass)
            AppTab.Retention -> RetentionScreen(viewModel, state, sizeClass)
            AppTab.Projects  -> ProjectsTab(viewModel, state, sizeClass)
            AppTab.Funnels   -> FunnelsTab(viewModel, state, sizeClass)
            AppTab.Experiments -> ExperimentsTab(viewModel, state, sizeClass)
            AppTab.Users     -> UsersTab(viewModel, state, sizeClass)
            AppTab.Settings  -> SettingsTab(viewModel, state, sizeClass)
        }
    }

    DashboardDialogs(viewModel, state, snackbarState)
}
