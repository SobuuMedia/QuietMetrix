package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.DailyPoint
import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.api.ScreenDuration
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TopEvent
import com.quietmetrix.dashboard.api.TopScreen
import com.quietmetrix.dashboard.api.Totals
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Produces a fresh current-code Overview capture at the handoff's CSS viewport
 * (the source screenshots are 2x pixel captures). This complements component
 * goldens and does not silently replace them or claim browser/Ktor parity.
 */
@OptIn(ExperimentalTestApi::class)
class ReferenceViewportCaptureTest {
    @Test
    fun captureOverviewAtReferenceViewport() = runDesktopComposeUiTest(width = 1504, height = 734) {
        val project = ApiProject(
            id = "demo-local",
            name = "QuietMetrix Demo Local",
            description = "Local seeded dashboard fixture",
            apiKeyLast4 = "a91f",
            lastReceivedAt = "2026-10-07T12:00:00Z",
            platforms = listOf("android", "ios", "web"),
            sdkVersions = listOf("0.6.0"),
            activityStatus = "receiving_data",
            declaredPlatforms = listOf("android"),
        )
        val state = DashboardState(
            user = ApiUser("demo-user", "owner@quietmetrix.test", "admin"),
            projects = listOf(project),
            currentProjectId = project.id,
            range = com.quietmetrix.dashboard.api.TimeRange.Day7,
            activeDestination = NavDestination.Overview,
            aggregates = AggregatesResponse(
                windowDays = 7,
                people = 1284,
                peopleState = "available",
                peopleSource = "v2",
                peopleComparison = 1088,
                v2Visits = 8734,
                v2VisitDurationMs = 252_000,
                v2VisitDurationState = "available",
                v2Crashes = 3,
                v2CrashesState = "available",
                totals = Totals(events = 8_734),
                daily = listOf(612L, 543L, 483L, 713L, 914L, 1038L, 840L).mapIndexed { i, n ->
                    DailyPoint(day = "2026-09-${18 + i}", total = n)
                },
                topEvents = listOf(TopEvent("app_open", 4_200)),
                topScreens = listOf(TopScreen("home_shelf", 1_104), TopScreen("book_detail", 812)),
                screenDurations = listOf(ScreenDuration("book_detail", 812, 141_000, 114_492_000)),
                countries = listOf(BreakdownItem("ES", 41), BreakdownItem("MX", 18), BreakdownItem("AR", 11)),
                platforms = listOf(BreakdownItem("android", 54), BreakdownItem("ios", 38), BreakdownItem("web", 8)),
                deviceClasses = listOf(BreakdownItem("phone", 82), BreakdownItem("tablet", 14), BreakdownItem("desktop", 4)),
            ),
            sessions = SessionsResponse(totalSessions = 8_734, avgDurationSec = 252),
            retention = RetentionResponse(
                source = "v2",
                cohorts = listOf(
                    RetentionCohort("2026-09-28", 204, .42, .31, .22, .17, .11),
                    RetentionCohort("2026-09-21", 198, .41, .30, .21, .15, .09),
                ),
            ),
        )
        val viewModel = DashboardViewModel()
        setContent {
            CompositionLocalProvider(LocalThemeModeSetter provides {}) {
                QuietMetrixTheme(ThemeMode.Dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        DashboardShell(viewModel, state, SnackbarHostState()) { sizeClass ->
                            OverviewScreen(viewModel, state, sizeClass)
                        }
                    }
                }
            }
        }
        waitForIdle()
        val output = File("build/test-results/parity-captures/overview-dark-1504x734.png")
        output.parentFile?.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output)
        check(output.isFile && output.length() > 0L) { "Overview screenshot was not written: ${output.path}" }
    }
}
