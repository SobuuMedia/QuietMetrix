package com.quietmetrix.dashboard.nav

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.ui.components.handCursor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Permanent navigation rail shown on Medium and Expanded widths. Thin icon rail
 * with a label under each item — the desktop/tablet "side menu".
 */
@Composable
fun SideRail(
    current: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<NavDestination> = NavDestination.entries,
) {
    NavigationRail(modifier = modifier) {
        // Push the destinations off the very top so the rail isn't flush against
        // the app bar; the trailing spacer keeps them vertically centred.
        Spacer(Modifier.weight(1f))
        destinations.forEach { dest ->
            NavigationRailItem(
                modifier = Modifier.handCursor(),
                selected = dest == current,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        painter = painterResource(dest.icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        stringResource(dest.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationRailItemDefaults.colors(),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Compact-width navigation drawer shown via a hamburger in the top bar. Wraps
 * the given [content] and slides the destination list over it when open.
 */
@Composable
fun SideDrawerHost(
    current: NavDestination,
    onSelect: (NavDestination) -> Unit,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    destinations: List<NavDestination> = NavDestination.entries,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                destinations.forEach { dest ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(dest.icon),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = { Text(stringResource(dest.labelRes)) },
                        selected = dest == current,
                        onClick = {
                            onSelect(dest)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).handCursor(),
                        colors = NavigationDrawerItemDefaults.colors(),
                    )
                }
            }
        },
        content = content,
    )
}
