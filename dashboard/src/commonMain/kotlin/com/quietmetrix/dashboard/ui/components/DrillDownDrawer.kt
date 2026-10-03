package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.viewmodel.DrillDownContent

/** Shared drawer presentation for a selected visible analytics value. */
@Composable
fun DrillDownDrawer(content: DrillDownContent, onDismiss: () -> Unit, onAsk: () -> Unit) {
    DrawerShell(title = content.key, onDismiss = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(content.value, style = MaterialTheme.typography.displayMedium)
            Text(content.detail, style = MaterialTheme.typography.bodyLarge)
            Pill(text = "Ask about this", variant = PillVariant.Lavender, onClick = onAsk)
        }
    }
}
