package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.nav.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@Suppress("DEPRECATION")
class MaxWidthContainerLayoutTest {
    @Test
    fun cappedContentHasBalancedOuterGutters() = runComposeUiTest {
        setContent {
            MaxWidthContainer(maxWidth = 600.dp, horizontalPadding = 32.dp, sizeClass = WindowSizeClass.Expanded) {
                Box(Modifier.fillMaxWidth().height(10.dp).testTag("capped-content"))
            }
        }

        val root = onRoot().fetchSemanticsNode().boundsInRoot
        val content = onNodeWithTag("capped-content").fetchSemanticsNode().boundsInRoot
        val left = content.left
        val right = root.width - content.right
        assertTrue(kotlin.math.abs(left - right) <= 1f, "Expected centered capped content, left gap=$left px, right gap=$right px")
    }
}
