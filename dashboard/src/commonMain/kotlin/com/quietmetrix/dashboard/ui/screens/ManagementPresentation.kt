@file:Suppress("DEPRECATION") // Shared clipboard support on JVM and WASM.
package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.ui.components.handCursor
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/** Displays a selectable-looking value with an accessible copy action on every supported host. */
@Composable
internal fun CopyableManagementValue(value: String, copyLabel: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(value)) }, modifier = Modifier.handCursor()) { MaterialText(copyLabel) }
    }
}
