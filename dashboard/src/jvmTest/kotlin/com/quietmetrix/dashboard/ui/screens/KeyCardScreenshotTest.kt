package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Screenshot-golden coverage for Task 21's "🔑 Your {project} key" card (`OverviewScreen.kt`'s
 * `KeyCard`), derived per `goldens/README.md`'s "How a task derives its own golden" process.
 *
 * **Data-honesty finding (documented in `KeyCard`'s own KDoc):** the design handoff asks for a
 * "masked key + Copy button". This dashboard never holds a project's full plaintext API key
 * during a normal Overview visit — `DashboardScreen.kt`'s `ProjectCard` already documents "the
 * full key is never recoverable — only a hash is stored server-side"; the only 2 places a real
 * plaintext key briefly exists in `DashboardState` ([com.quietmetrix.dashboard.viewmodel.DashboardState.newProjectKeys]/
 * [com.quietmetrix.dashboard.viewmodel.DashboardState.regeneratedKey]) are transient,
 * create/regenerate-modal-only state that's cleared well before a user would be looking at
 * Overview. A "Copy" button here would therefore have nothing real to copy. [KeyCard] instead
 * shows the masked key (last 4 digits, matching `ProjectCard`'s own convention) plus a real
 * "Manage key →" action that navigates to the Projects screen, where Copy/Regenerate genuinely
 * work. The status line similarly can't honestly claim a "last received" timestamp (no such field
 * exists anywhere in the API — see `OverviewTopListsLogic.kt`'s investigation for the same kind of
 * gap elsewhere on this screen); instead it reports a real binary signal (receiving data this
 * period vs. not, or unknown while the server is unreachable).
 *
 * Real-crop derivation checked and REJECTED for all states — same finding as the other new Task 21
 * sections (sits below the fold in every `_source/` frame).
 */
@OptIn(ExperimentalTestApi::class)
class KeyCardScreenshotTest {

    private val project = ApiProject(id = "p1", name = "Sobuu", apiKeyLast4 = "a1b2")

    @Suppress("DEPRECATION")
    @Test
    fun receivingDataStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    KeyCard(
                        project = project,
                        isReceivingData = true,
                        serverUnreachable = false,
                        onManageKey = {},
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/key-card/receiving.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun loadingStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    KeyCard(
                        project = null,
                        isReceivingData = false,
                        serverUnreachable = false,
                        onManageKey = {},
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/key-card/loading.png")
    }

    // "Waiting" status — no events received yet this period, but still real data (a project, a
    // masked key), distinct from the whole-screen first-run Empty state that swaps this card out
    // entirely for SetupInstructionsSection.
    @Suppress("DEPRECATION")
    @Test
    fun waitingStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    KeyCard(
                        project = project,
                        isReceivingData = false,
                        serverUnreachable = false,
                        onManageKey = {},
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/key-card/waiting.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun serverUnreachableStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    KeyCard(
                        project = project,
                        isReceivingData = true,
                        serverUnreachable = true,
                        onManageKey = {},
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/key-card/unreachable.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun showsMaskedKeyNotTheRealOne() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    KeyCard(
                        project = project,
                        isReceivingData = true,
                        serverUnreachable = false,
                        onManageKey = {},
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("a1b2", substring = true).assertExists()
    }

    @Suppress("DEPRECATION")
    @Test
    fun manageKeyClickInvokesCallback() = runComposeUiTest {
        var clicked = false
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    KeyCard(
                        project = project,
                        isReceivingData = true,
                        serverUnreachable = false,
                        onManageKey = { clicked = true },
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Manage key").performClick()
        waitForIdle()
        assertTrue(clicked)
    }
}
