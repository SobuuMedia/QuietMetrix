package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ExperimentDto

/** SDK language shown by [CodeSnippetPanel]. */
internal enum class SnippetLanguage { Kotlin, Swift, JavaScript }

/** Returns the SDK usage example for a language and experiment key. */
internal fun experimentSnippet(language: SnippetLanguage, experiment: ExperimentDto): String {
    fun quote(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
    val escaped = quote(experiment.key)
    val first = quote(experiment.variants.getOrNull(0)?.name ?: "variant_a")
    val second = quote(experiment.variants.getOrNull(1)?.name ?: "variant_b")
    return when (language) {
        SnippetLanguage.Kotlin -> "import com.quietmetrix.analytics.getVariant\nimport com.quietmetrix.analytics.trackExperimentInteraction\n\nwhen (getVariant(\"$escaped\")) {\n    \"$second\" -> NewVariant()\n    else -> OriginalVariant() // \"$first\", or \"none\" if not enrolled\n}\ntrackExperimentInteraction(\"$escaped\")"
        SnippetLanguage.Swift -> "switch ExperimentsKt.getVariant(experimentKey: \"$escaped\") {\ncase \"$second\":\n    NewVariant { ExperimentsKt.trackExperimentInteraction(experimentKey: \"$escaped\") }\ndefault:\n    OriginalVariant() // \"$first\", or \"none\" if not enrolled\n}"
        SnippetLanguage.JavaScript -> "import { getVariant, trackExperimentInteraction } from \"@quietmetrix/sdk\";\n\nswitch (getVariant(\"$escaped\")) {\n  case \"$second\": showNewVariant(); break;\n  default: showOriginalVariant(); // \"$first\" or \"none\"\n}\ntrackExperimentInteraction(\"$escaped\");"
    }
}

/** Reusable language-switching experiment SDK example with an optional explanation action. */
@Composable
internal fun CodeSnippetPanel(experiment: ExperimentDto, onExplain: () -> Unit, modifier: Modifier = Modifier) {
    var language by remember(experiment.key) { mutableStateOf(SnippetLanguage.Kotlin) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SnippetLanguage.entries.forEach { option ->
                    TextButton(onClick = { language = option }) {
                        Text(if (language == option) "• ${option.label}" else option.label)
                    }
                }
            }
            Text(experimentSnippet(language, experiment), Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            TextButton(onClick = onExplain) { Text("Explain the result") }
        }
    }
}

/** User-facing label for a supported snippet language. */
private val SnippetLanguage.label: String
    get() = when (this) {
        SnippetLanguage.Kotlin -> "Kotlin Multiplatform"
        SnippetLanguage.Swift -> "Swift"
        SnippetLanguage.JavaScript -> "JavaScript"
    }
