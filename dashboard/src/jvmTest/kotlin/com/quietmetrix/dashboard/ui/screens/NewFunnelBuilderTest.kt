package com.quietmetrix.dashboard.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.api.CreateFunnelRequest
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NewFunnelBuilderTest {
    @Test
    fun requiresValidMetadataAndOrderedEventStepsBeforeSubmitting() = runComposeUiTest {
        var submitted: CreateFunnelRequest? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                NewFunnelBuilderDialog(onDismiss = {}, onCreate = { submitted = it })
            }
        }

        onNodeWithText("Create funnel").assertIsNotEnabled()
        onNodeWithText("Funnel key").performTextInput("signup-flow")
        onNodeWithText("Name").performTextInput("Signup flow")
        onAllNodes(hasSetTextAction())[5].performTextInput("signup_started")
        onAllNodes(hasSetTextAction())[9].performTextInput("signup_completed")
        onNodeWithText("Create funnel").performClick()

        assertEquals("signup-flow", submitted?.funnelKey)
        assertEquals("Signup flow", submitted?.name)
        assertEquals(listOf("signup_started", "signup_completed"), submitted?.steps?.map { it.event })
        assertEquals(7L * 86_400, submitted?.windowSeconds)
    }
}
