package com.quietmetrix.analytics.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.analytics.QuietMetrixDebug

/**
 * A small on-screen panel showing the counter pipeline's most recent flush — every metric+dims
 * cell that was sent, and whether the flush succeeded. Mount it anywhere in your Compose tree
 * (e.g. layered over your root content) while developing against a `QuietMetrixConfig(debug =
 * true)` build; it renders "nothing sent yet" harmlessly if debug mode is off, since
 * [QuietMetrixDebug] publishes nothing in that case.
 *
 * Ships as a separate module ([com.quietmetrix.analytics.debug]) so the core SDK stays free of
 * a Compose Multiplatform dependency for consumers who never use this. Available on Android,
 * iOS, JVM (desktop), and Web (wasmJs) only — see this module's `build.gradle.kts` for why
 * Linux/Windows/macOS-native have no equivalent.
 */
@Composable
fun QuietMetrixDebugOverlay(modifier: Modifier = Modifier) {
    val pending by QuietMetrixDebug.pending.collectAsState(initial = QuietMetrixDebug.currentPending())
    val lastFlush by QuietMetrixDebug.lastFlush.collectAsState(initial = QuietMetrixDebug.currentLastFlush())

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("QuietMetrix Debug", style = MaterialTheme.typography.titleSmall)

            val outcome = lastFlush
            Text(
                text = if (outcome == null) {
                    "No flush yet"
                } else {
                    "Last flush: ${outcome.counterCount} counter(s), ${if (outcome.succeeded) "sent" else "failed"}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (pending.isEmpty()) {
                Text(
                    "Nothing sent yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(pending) { counter ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val dims = counter.dims.entries.joinToString(", ") { (k, v) -> "$k=$v" }
                            Text("${counter.metric}{$dims}", style = MaterialTheme.typography.bodySmall)
                            Text("n=${counter.n}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
