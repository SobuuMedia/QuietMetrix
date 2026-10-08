package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cleanup-task coverage (post Task 16) proving the header's [NavPillRow] is the
 * *sole* navigation surface at every window size, now that the pre-redesign
 * `SideRail`/`SideDrawerHost` split has been removed from [DashboardShell]: the
 * design handoff (`design_handoff_dashboard_redesign/README.md` > Screens / Views
 * > "1. Header") and the HTML prototype (exactly one `<nav>` element, `flex-wrap:
 * wrap`, no sidebar/rail markup at any viewport) describe navigation as the
 * header's pill row only, at every width — a second, independently-wired nav
 * surface (the old side rail at Medium/Expanded, the hamburger-triggered drawer
 * at Compact) was a holdover this task removes.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardShellNavTest {

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's reviewed
    // convention, commit ab300e0): v1 runComposeUiTest is what the existing
    // screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    private fun renderShellAt(
        widthDp: Int,
        heightDp: Int = 900,
        state: DashboardState = DashboardState(),
        block: androidx.compose.ui.test.ComposeUiTest.(DashboardViewModel) -> Unit,
    ) =
        runComposeUiTest {
            val viewModel = DashboardViewModel()
            setContent {
                CompositionLocalProvider(LocalThemeModeSetter provides {}) {
                    QuietMetrixTheme(ThemeMode.Dark) {
                        Box(modifier = Modifier.size(widthDp.dp, heightDp.dp)) {
                            DashboardShell(
                                viewModel = viewModel,
                                state = state,
                                snackbarState = SnackbarHostState(),
                            ) { _ ->
                                Text("content")
                            }
                        }
                    }
                }
            }
            waitForIdle()
            block(viewModel)
        }

    @Test
    fun navPillRowIsTheOnlyNavSurfaceAtCompactWidth() = renderShellAt(widthDp = 360) { _ ->
        // Exactly one "Flow" label — the pill row's — not a second one from a
        // side drawer/rail rendering the same destination list.
        onAllNodesWithText("Flow", substring = true).assertCountEquals(1)
    }

    @Test
    fun navPillRowIsTheOnlyNavSurfaceAtMediumWidth() = renderShellAt(widthDp = 700) { _ ->
        onAllNodesWithText("Flow", substring = true).assertCountEquals(1)
    }

    @Test
    fun navPillRowIsTheOnlyNavSurfaceAtExpandedWidth() = renderShellAt(widthDp = 1200) { _ ->
        onAllNodesWithText("Flow", substring = true).assertCountEquals(1)
    }

    @Test
    fun hamburgerMenuButtonNoLongerRendersAtCompactWidth() = renderShellAt(widthDp = 360) { _ ->
        // The old compact-width hamburger opened SideDrawerHost; there is no
        // drawer left for it to open, so the trigger itself must be gone.
        onAllNodesWithContentDescription("Open navigation menu").assertCountEquals(0)
    }

    @Test
    fun rangeMenuOmitsUnsupportedLastHourChoice() = renderShellAt(widthDp = 1200) { _ ->
        onNodeWithText("Last 7 days").performClick()
        onNodeWithText("Today").assertExists()
        onAllNodesWithText("Last hour").assertCountEquals(0)
    }

    @Test
    fun allEightDestinationsAreReachableAndClickableAtMinimumSupportedWidth() {
        // 320dp is this project's established "tiny width is still Compact" floor
        // (nav/Breakpoint.kt's BreakpointTest.tinyWidthIsCompact asserts
        // classifyWindow(320.dp) == Compact) -- the narrowest width the app
        // commits to supporting. Removing SideDrawerHost means NavPillRow's
        // FlowRow is now the *only* way to reach any destination at this width,
        // so every destination (admin role, so all 8 -- including the
        // admin-only "Users" entry -- are visible) must still render as a
        // present, clickable pill once it wraps onto its own line(s).
        // Navigation now renders vector icons alongside localized labels.
        // Matching label text and click semantics still proves each destination is reachable.
        val labelsByDestination = mapOf(
            NavDestination.Overview to "Overview",
            NavDestination.Flow to "Flow",
            NavDestination.Retention to "Retention",
            NavDestination.Projects to "Projects",
            NavDestination.Funnels to "Funnels",
            NavDestination.Experiments to "Experiments",
            NavDestination.Users to "Team",
            NavDestination.Settings to "Settings",
        )
        val adminState = DashboardState(user = ApiUser(id = "1", email = "admin@x.c", role = UserRole.ADMIN))
        renderShellAt(widthDp = 320, state = adminState) { _ ->
            NavDestination.entries.forEach { destination ->
                val pillText = labelsByDestination.getValue(destination)
                onAllNodesWithText(pillText, substring = true)
                    .filter(hasClickAction())
                    .assertCountEquals(1)
            }
        }
    }

    @Test
    fun navPillRowSelectsDestinationAtCompactWidth() = renderShellAt(widthDp = 360) { viewModel ->
        onNodeWithText("Settings", substring = true).performClick()
        waitForIdle()
        assertEquals(NavDestination.Settings, viewModel.state.value.activeDestination)
    }

    @Test
    fun navPillRowSelectsDestinationAtExpandedWidth() = renderShellAt(widthDp = 1200) { viewModel ->
        onNodeWithText("Settings", substring = true).performClick()
        waitForIdle()
        assertEquals(NavDestination.Settings, viewModel.state.value.activeDestination)
    }
}
