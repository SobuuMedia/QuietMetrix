@file:Suppress("DEPRECATION") // Clipboard API shared with the WASM target.

package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.*
import com.quietmetrix.dashboard.ui.components.charts.BarChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.BarData
import com.quietmetrix.dashboard.viewmodel.DrillDownContent
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** A scoped analytics drawer with current/previous values, visible breakdowns and copy/Ask actions. */
@Composable
internal fun DrillDownDrawer(content: DrillDownContent, scope: String, compare: Boolean, expert: Boolean, onDismiss: () -> Unit, onAsk: (String) -> Unit) {
    val title = content.titleRes?.let { stringResource(it) } ?: content.title ?: content.key
    val explanation = stringResource(content.explanation)
    val breakdownTitle = stringResource(content.breakdownTitle)
    val clipboard = LocalClipboardManager.current
    var copied by remember(content.kind, content.key) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val rows = content.rows.map { (it.labelRes?.let { resource -> stringResource(resource) } ?: it.label) to it }
    DrawerShell(title = title, onDismiss = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(scope, style = MaterialTheme.typography.labelMedium)
            Text(content.value, style = MaterialTheme.typography.displayMedium)
            if (compare) ComparisonNote(content.previousValue)
            Text(explanation, style = MaterialTheme.typography.bodyLarge)
            if (rows.isNotEmpty()) {
                Text(breakdownTitle, style = MaterialTheme.typography.titleMedium)
                val bars = rows.mapNotNull { (label, row) -> row.numeric?.let { BarData(label, it) } }
                if (bars.isNotEmpty()) BarChartCanvas(bars, chartHeight = 140.dp)
                rows.forEach { (label, row) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(row.value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (expert) {
                Text(stringResource(Res.string.drill_raw), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.drill_raw_values, content.kind, content.key, content.rawValue ?: stringResource(Res.string.value_none)), style = MaterialTheme.typography.bodySmall)
            }
            Pill(text = stringResource(Res.string.drill_ask), variant = PillVariant.Lavender, onClick = { onAsk(title) })
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(buildString {
                    appendLine(scope); appendLine("$title: ${content.value}"); appendLine(explanation)
                    rows.forEach { (label, row) -> appendLine("$label: ${row.value}") }
                }))
                copied = true
            }) { Text(stringResource(if (copied) Res.string.drill_copied else Res.string.drill_copy)) }
        }
    }
}
