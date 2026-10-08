package com.quietmetrix.dashboard.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.comparison_unavailable
import com.quietmetrix.dashboard.resources.comparison_was
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/** A measured previous value or explicit unavailability, with the shared comparison styling. */
@Composable
fun ComparisonNote(value: String?) {
    Text(comparisonNoteText(value), style = MaterialTheme.typography.labelSmall, color = LocalExtendedColors.current.lavender)
}

/** Localizes the comparison note for cards that own their own caption layout. */
@Composable
fun comparisonNoteText(value: String?): String = if (value == null) stringResource(Res.string.comparison_unavailable)
    else stringResource(Res.string.comparison_was, value)
