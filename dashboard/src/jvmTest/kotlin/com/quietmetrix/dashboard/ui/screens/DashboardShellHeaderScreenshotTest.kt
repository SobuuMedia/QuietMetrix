package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 16's restyled header pieces
 * (`DashboardShell.kt`'s `HeaderLogoAndWordmark`/`ProjectDropdown`/
 * `DateRangeDropdown`/`HeaderToggleRow`/`NavPillRow`), split the same way
 * `goldens/README.md` records the derivation for each region:
 *
 * - [brandAndDropdownsMatchGolden]: real-crop-derived — the logo badge,
 *   wordmark, project switcher and date-range dropdown triggers are all
 *   visible (unobscured by the Ask-drawer overlay) in the left ~465px of
 *   `_source/01-overview-normal.png`'s header band.
 * - [navPillRowMatchesGolden]: hybrid — the active/inactive pill treatment
 *   for Overview/Flow/Retention/Funnels/Team/Settings is real-crop-derived
 *   (same visible region); Projects/Experiments' positions and the
 *   Experiments "NEW" badge are covered/cut in the source frame, so those
 *   are token/prototype-derived per the standing fallback ruling.
 * - [toggleRowMatchesGolden]: fallback-derived — the Compare/Expert/
 *   Refresh/Theme/Ask/avatar row sits to the right of the Ask-drawer's left
 *   edge in the source frame (only a stray "ⓘ" and a cut "🤓 Exper[t]" are
 *   visible), so this golden is derived from design tokens + the HTML
 *   prototype markup + README prose, not a JPEG crop.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardShellHeaderScreenshotTest {

    private val sampleProjects = listOf(
        ApiProject(id = "p1", name = "Sobuu"),
        ApiProject(id = "p2", name = "Acme"),
    )

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun brandAndDropdownsMatchGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HeaderLogoAndWordmark()
                        ProjectDropdown(projects = sampleProjects, currentProjectId = "p1", onSelect = {})
                        DateRangeDropdown(range = TimeRange.Day7, onSelect = {})
                    }
                }
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("header/brand-and-dropdowns.png")
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun navPillRowMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                ) {
                    NavPillRow(
                        destinations = NavDestination.visibleFor(role = null),
                        current = NavDestination.Overview,
                        onSelect = {},
                        modifier = Modifier.width(460.dp),
                    )
                }
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("header/nav-pill-row.png")
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun toggleRowMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                ) {
                    HeaderToggleRow(
                        compareOn = false,
                        onCompareToggle = {},
                        expertOn = false,
                        onExpertToggle = {},
                        refreshing = false,
                        onRefresh = {},
                        themeMode = ThemeMode.Dark,
                        onToggleTheme = {},
                        onAsk = {},
                        userEmail = "dev@quietmetrix.com",
                        onSignOut = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("header/toggle-row.png")
    }
}
