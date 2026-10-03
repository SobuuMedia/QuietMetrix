package com.quietmetrix.dashboard.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.quietmetrix.dashboard.resources.*
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import org.jetbrains.compose.resources.stringResource

/** A measured previous value or explicit unavailability, with the shared comparison styling. */
@Composable
fun ComparisonNote(value: String?) {
    Text(comparisonNoteText(value), style = MaterialTheme.typography.labelSmall, color = LocalExtendedColors.current.lavender)
}

/** Localizes the comparison note for cards that own their own caption layout. */
@Composable
fun comparisonNoteText(value: String?): String = if (value == null) stringResource(Res.string.comparison_unavailable)
    else stringResource(Res.string.comparison_was, value)
