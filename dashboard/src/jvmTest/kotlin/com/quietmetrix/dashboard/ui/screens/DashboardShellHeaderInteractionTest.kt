package com.quietmetrix.dashboard.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Functional-wiring coverage for the header pieces introduced by Task 16.
 * The project/date-range dropdowns and the nav pill row are the
 * "already-functional, just restyled" pieces the task brief calls out (they
 * drive the same [ApiProject]/[TimeRange]/[NavDestination] selection
 * callbacks the pre-existing `ProjectPicker`/`WindowPicker`/`SideRail` used)
 * — these tests pin that the restyled triggers still fire those callbacks,
 * independent of the (golden-tested) visual appearance.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardShellHeaderInteractionTest {

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun projectDropdownSelectsTappedProject() = runComposeUiTest {
        var selected: String? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                ProjectDropdown(
                    projects = listOf(ApiProject(id = "p1", name = "Sobuu"), ApiProject(id = "p2", name = "Acme")),
                    currentProjectId = "p1",
                    onSelect = { selected = it },
                )
            }
        }
        waitForIdle()

        onNodeWithText("Sobuu", substring = true).performClick() // opens the dropdown (trigger shows "📦 Sobuu ▾")
        waitForIdle()
        onNodeWithText("Acme").performClick() // picks the other project from the opened menu
        waitForIdle()

        assertEquals("p2", selected)
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun dateRangeDropdownSelectsTappedRange() = runComposeUiTest {
        var selected: TimeRange? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                DateRangeDropdown(range = TimeRange.Day7, onSelect = { selected = it })
            }
        }
        waitForIdle()

        onNodeWithText("Last 7 days", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Last 30 days").performClick()
        waitForIdle()

        assertEquals(TimeRange.Day30, selected)
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun navPillRowSelectsTappedDestination() = runComposeUiTest {
        var selected: NavDestination? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                NavPillRow(
                    destinations = NavDestination.visibleFor(role = null),
                    current = NavDestination.Overview,
                    onSelect = { selected = it },
                )
            }
        }
        waitForIdle()

        onNodeWithText("Flow", substring = true).performClick() // pill shows "🧭 Flow"
        waitForIdle()

        assertEquals(NavDestination.Flow, selected)
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun headerToggleRowRefreshAndSignOutFireCallbacks() = runComposeUiTest {
        var refreshed = false
        var signedOut = false
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                HeaderToggleRow(
                    compareOn = false,
                    onCompareToggle = {},
                    expertOn = false,
                    onExpertToggle = {},
                    refreshing = false,
                    onRefresh = { refreshed = true },
                    themeMode = ThemeMode.Dark,
                    onToggleTheme = {},
                    onAsk = {},
                    userEmail = "dev@quietmetrix.com",
                    onSignOut = { signedOut = true },
                )
            }
        }
        waitForIdle()

        // Refresh and the avatar (sign-out menu) are icon-only controls, found by
        // content description rather than visible text.
        onNodeWithContentDescription("Refresh").performClick()
        waitForIdle()
        assertEquals(true, refreshed)

        onNodeWithContentDescription("Account menu").performClick()
        waitForIdle()
        onNodeWithText("Sign out").performClick()
        waitForIdle()
        assertEquals(true, signedOut)
    }
}
