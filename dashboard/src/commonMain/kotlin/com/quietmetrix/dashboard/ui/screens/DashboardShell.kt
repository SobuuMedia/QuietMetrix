package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.nav.SideDrawerHost
import com.quietmetrix.dashboard.nav.SideRail
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.nav.classifyWindow
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_dismiss
import com.quietmetrix.dashboard.resources.action_refresh
import com.quietmetrix.dashboard.resources.action_signout
import com.quietmetrix.dashboard.resources.brand
import com.quietmetrix.dashboard.resources.ic_close
import com.quietmetrix.dashboard.resources.ic_menu
import com.quietmetrix.dashboard.resources.ic_refresh
import com.quietmetrix.dashboard.resources.ic_signout
import com.quietmetrix.dashboard.resources.nav_menu_cd
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardShell(
    viewModel: DashboardViewModel,
    state: DashboardState,
    snackbarState: SnackbarHostState,
    content: @Composable (WindowSizeClass) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val sizeClass = classifyWindow(maxWidth)
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val body: @Composable () -> Unit = {
            Scaffold(
                topBar = { DashboardTopBar(viewModel, state, sizeClass) { scope.launch { drawerState.open() } } },
                snackbarHost = { SnackbarHost(hostState = snackbarState) },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    ErrorBar(state.error, onDismiss = { viewModel.clearError() })
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (state.refreshing) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            content(sizeClass)
                        }
                    }
                }
            }
        }

        val destinations = NavDestination.visibleFor(state.user?.role)
        if (sizeClass == WindowSizeClass.Compact) {
            SideDrawerHost(
                current = state.activeDestination,
                onSelect = { viewModel.navigateTo(it) },
                drawerState = drawerState,
                destinations = destinations,
                content = body,
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                SideRail(
                    current = state.activeDestination,
                    onSelect = { viewModel.navigateTo(it) },
                    modifier = Modifier.fillMaxHeight(),
                    destinations = destinations,
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { body() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    viewModel: DashboardViewModel,
    state: DashboardState,
    sizeClass: WindowSizeClass,
    onOpenDrawer: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(Res.string.brand),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (sizeClass == WindowSizeClass.Compact) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.handCursor()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_menu),
                        contentDescription = stringResource(Res.string.nav_menu_cd),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        actions = {
            if (sizeClass != WindowSizeClass.Compact && !state.user?.email.isNullOrEmpty()) {
                Text(
                    text = state.user?.email.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            IconButton(
                onClick = { viewModel.refresh() },
                enabled = !state.refreshing,
                modifier = Modifier.handCursor(),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_refresh),
                    contentDescription = stringResource(Res.string.action_refresh),
                    modifier = Modifier.size(20.dp),
                )
            }
            TextButton(onClick = { viewModel.logout() }, modifier = Modifier.handCursor()) {
                Icon(
                    painter = painterResource(Res.drawable.ic_signout),
                    contentDescription = stringResource(Res.string.action_signout),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.action_signout))
            }
            Spacer(Modifier.width(8.dp))
        },
    )
}

@Composable
private fun ErrorBar(error: String?, onDismiss: () -> Unit) {    error ?: return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.action_dismiss),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

