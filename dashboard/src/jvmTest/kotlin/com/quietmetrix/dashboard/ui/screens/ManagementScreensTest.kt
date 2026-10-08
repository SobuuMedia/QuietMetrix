package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AccessTokenDto
import com.quietmetrix.dashboard.api.ApiManagedUser
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.testutil.writeAsGolden
import com.quietmetrix.dashboard.theme.LocalThemeMode
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.ui.components.LayoutFlow
import com.quietmetrix.dashboard.ui.components.layoutFlowFor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.ServerConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies management screens using callbacks so rendering never starts real network requests. */
@OptIn(ExperimentalTestApi::class)
@Suppress("DEPRECATION")
class ManagementScreensTest {
    private val project = ApiProject("p1", "Reading app", "Private reading analytics", apiKeyLast4 = "1234", ownerUserId = "admin",
        lastReceivedAt = "2026-10-05T10:00:00Z", platforms = listOf("android", "ios"), sdkVersions = listOf("1.2.0"), activityStatus = "receiving_data")
    private val state = DashboardState(user = ApiUser("admin", "owner@example.com", UserRole.ADMIN),
        projects = listOf(project), users = listOf(ApiManagedUser("admin", "owner@example.com", UserRole.ADMIN)),
        members = mapOf("p1" to emptyList()), serverBaseUrl = "https://qm.example.com", serverConnection = ServerConnection.Online,
        serverVersion = "0.6.0", accessTokens = listOf(AccessTokenDto("t1", "Agent", "5678", listOf("analytics:read"))))

    /** Screenshots are explicit regression baselines; the supplied reference panes are obscured. */
    private fun ComposeUiTest.golden(path: String) {
        waitForIdle()
        val captured = onRoot().captureToImage()
        if (System.getProperty("golden.record") == "true") captured.writeAsGolden(path)
        captured.assertMatchesGolden(path)
    }

    /** Project grid and shared confirmation must keep destructive callbacks gated by confirmation. */
    @Test fun projectsGoldenAndDeleteRequiresConfirmation() = runComposeUiTest {
        var deleted: String? = null
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ProjectsContent(state, WindowSizeClass.Expanded, { _, _, _, _ -> }, { deleted = it }, {}, {}, {})
            }
        } }
        golden("projects/grid-normal.png")
        onNodeWithText("Delete").performClick()
        assertNull(deleted)
        onNodeWithText("Cancel").performClick()
        assertNull(deleted)
        onNodeWithText("Delete").performClick()
        onAllNodesWithText("Delete")[1].performClick()
        assertEquals("p1", deleted)
    }

    /** Inline invitation validates input, uses its selected role, and suppresses repeat submissions. */
    @Test fun teamGoldenAndInviteValidation() = runComposeUiTest {
        var invitation: Triple<String, String, List<String>>? = null
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                TeamContent(state, WindowSizeClass.Expanded, { email, role, projects -> invitation = Triple(email, role, projects) }, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {})
            }
        } }
        golden("team/matrix-and-invite.png")
        onNodeWithText("Send invitation").assertIsNotEnabled()
        onNodeWithTag("invitation-email").performTextInput("invalid")
        onNodeWithText("Enter a valid email address.").assertExists()
        onNodeWithText("Send invitation").assertIsNotEnabled()
        onNodeWithTag("invitation-email").performTextReplacement(" person@example.com ")
        onNodeWithTag("invite-project-p1").performClick()
        onNodeWithText("Send invitation").performClick()
        assertEquals(Triple("person@example.com", UserRole.REVIEWER, listOf("p1")), invitation)
    }

    /** Settings exposes real status, swatches and copyable configuration, then passes token expiry. */
    @Test fun settingsGoldenAndTokenExpiry() = runComposeUiTest {
        var created: Triple<String, List<String>, Long?>? = null
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            CompositionLocalProvider(LocalThemeMode provides ThemeMode.Dark, LocalThemeModeSetter provides {}) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SettingsContent(state, {}, {}, { name, scopes, expiry -> created = Triple(name, scopes, expiry) }, {}, {}, {})
                }
            }
        } }
        golden("settings/cards-normal.png")
        onNodeWithText("New token").performClick()
        onNodeWithText("Name").performTextInput("My agent")
        onNodeWithText("30 days").performClick()
        onNodeWithText("Create token").performClick()
        assertEquals(Triple("My agent", listOf("projects:create"), 30L), created)
    }
    /** Narrow Team viewports stack the invite panel and keep every input reachable by scrolling. */
    @Test fun compactTeamStacksAndKeepsInvitationReachable() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    TeamContent(state, WindowSizeClass.Compact, { _, _, _ -> }, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {})
                }
            }
        } }
        val people = onNodeWithText("owner@example.com").fetchSemanticsNode().boundsInRoot
        val invite = onNodeWithText("Invite someone").fetchSemanticsNode().boundsInRoot
        assertTrue(invite.top >= people.bottom)
        onNodeWithText("Send invitation").performScrollTo().assertIsDisplayed()
        val bounds = onNodeWithText("Send invitation").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= 0f && bounds.right <= 360f)
        golden("team/compact-invite.png")
    }

    /** Saved theme selection uses accessible swatches; server controls remain reachable below MCP setup. */
    @Test fun appearanceSelectionAndServerControls() = runComposeUiTest {
        var selected = ThemeMode.Dark
        var checked = false
        var refreshed = false
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            val mode = remember { mutableStateOf(ThemeMode.Dark) }
            CompositionLocalProvider(LocalThemeMode provides mode.value, LocalThemeModeSetter provides { selected = it; mode.value = it }) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    SettingsContent(state.copy(serverConnection = ServerConnection.Offline), { checked = true }, { refreshed = true }, { _, _, _ -> }, {}, {}, {})
                }
            }
        } }
        onNodeWithText("Check connection").performScrollTo().performClick()
        assertTrue(checked)
        onNodeWithText("Server URL").performScrollTo().assertIsDisplayed()
        golden("settings/appearance-and-server.png")
        onNodeWithText("Light").performScrollTo().performClick()
        assertEquals(ThemeMode.Light, selected)
        onNodeWithText("Light").assertIsSelected()
        onNodeWithText("Refresh data").performScrollTo().performClick()
        assertTrue(refreshed)
    }

    @Test fun compactSettingsStackBothColumnsWithoutHorizontalOverflow() = runComposeUiTest {
        assertEquals(WindowSizeClass.Compact, settingsColumnsLayoutFor(WindowSizeClass.Medium))
        assertEquals(WindowSizeClass.Expanded, settingsColumnsLayoutFor(WindowSizeClass.Expanded))
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            CompositionLocalProvider(LocalThemeMode provides ThemeMode.Dark, LocalThemeModeSetter provides {}) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    SettingsContent(state, {}, {}, { _, _, _ -> }, {}, {}, {}, sizeClass = WindowSizeClass.Compact)
                }
            }
        } }
        assertEquals(LayoutFlow.StackedColumn, layoutFlowFor(WindowSizeClass.Compact))
        onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        val appearance = onNodeWithText("Appearance").fetchSemanticsNode().boundsInRoot
        assertTrue(appearance.left >= 0f && appearance.right <= 360f)
    }

    /** No action is offered to submit another invitation while the current request is pending. */
    @Test fun pendingInvitationDisablesInputAndSend() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            TeamContent(state.copy(invitationPending = true), WindowSizeClass.Expanded,
                { _, _, _ -> error("Cannot resubmit") }, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        onNodeWithTag("invitation-email").assertIsNotEnabled()
        onNodeWithText("Sending…").assertIsNotEnabled()
    }

    /** Existing owner/self protections survive the matrix restyle. */
    @Test fun ownAccessAndRemovalRemainLocked() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            TeamContent(state, WindowSizeClass.Expanded, { _, _, _ -> }, { _, _ -> error("Cannot change own role") },
                { error("Cannot remove self") }, { _, _ -> error("Cannot change owner access") }, { _, _ -> error("Cannot change owner access") }, {})
        } }
        onNodeWithTag("access-admin-p1").assertIsNotEnabled()
        onNodeWithContentDescription("Remove").assertIsNotEnabled()
    }

    /** Creation is disabled until a name is entered, and regeneration remains confirm-gated. */
    @Test fun projectModalValidatesNameAndRegenerationRequiresConfirmation() = runComposeUiTest {
        var created: Pair<String, String?>? = null
        var createdIcon: String? = null
        var createdPlatforms: List<String>? = null
        var regenerated: String? = null
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            ProjectsContent(state, WindowSizeClass.Expanded, { name, description, icon, platforms -> created = name to description; createdIcon = icon; createdPlatforms = platforms }, {}, { regenerated = it }, {}, {})
        } }
        onNodeWithText("New app").performClick()
        onNodeWithText("Create").assertIsNotEnabled()
        onNodeWithText("Name").performTextInput(" New project ")
        onNodeWithText("Shopping").performClick()
        onNodeWithText("ANDROID").performClick()
        onNodeWithText("Create").performClick()
        assertEquals("New project" to null, created)
        assertEquals("shopping", createdIcon)
        assertEquals(listOf("android"), createdPlatforms)
        onNodeWithText("Regenerate").performClick()
        assertNull(regenerated)
        onNodeWithText("Cancel").performClick()
        assertNull(regenerated)
        onNodeWithText("Regenerate").performClick()
        onAllNodesWithText("Regenerate")[1].performClick()
        assertEquals("p1", regenerated)
    }

    @Test fun projectRenameUsesTheSelectedProjectAndRequiresAChangedName() = runComposeUiTest {
        var update: Pair<String, String>? = null
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            ProjectsContent(state, WindowSizeClass.Expanded, { _, _, _, _ -> }, {}, {}, {}, {},
                onUpdateProject = { id, name -> update = id to name })
        } }
        onNodeWithText("Edit").performClick()
        onNodeWithText("Save changes").assertIsNotEnabled()
        onNodeWithText("Name").performTextReplacement("Updated reading app")
        onNodeWithText("Save changes").performClick()
        assertEquals("p1" to "Updated reading app", update)
    }

    /** Copy sends the complete displayed configuration through the platform clipboard abstraction. */
    @Test fun configurationCopyUsesFullClipboardValue() = runComposeUiTest {
        var copied: androidx.compose.ui.text.AnnotatedString? = null
        val clipboard = object : androidx.compose.ui.platform.ClipboardManager {
            override fun getText() = copied
            override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) { copied = annotatedString }
        }
        val configuration = mcpClientConfiguration(state.serverBaseUrl)
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalClipboardManager provides clipboard) {
                CopyableManagementValue(configuration, "Copy")
            }
        } }
        onNodeWithText("Copy").performClick()
        assertEquals(configuration, copied?.text)
    }

}
