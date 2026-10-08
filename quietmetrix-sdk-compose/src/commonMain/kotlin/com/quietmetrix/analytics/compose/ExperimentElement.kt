package com.quietmetrix.analytics.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.quietmetrix.analytics.ExperimentDecision
import com.quietmetrix.analytics.Variant
import com.quietmetrix.analytics.observeExperiment
import com.quietmetrix.analytics.recordExperimentExposure

/**
 * Displays [content] only for variant B. Variant A, Pending, ineligible and unavailable states
 * render [control] (empty by default). The decision is pinned once Ready so a config refresh
 * cannot replace content while this placement remains composed.
 */
@Composable
fun ExperimentElement(
    key: String,
    control: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    when (val decision = rememberPinnedExperimentDecision(key)) {
        is ExperimentDecision.Ready -> if (decision.variant == Variant.B) content() else control()
        else -> control()
    }
}

/** Renders both explicitly authored variants; A is the fallback in every unresolved state. */
@Composable
fun ExperimentVariants(
    key: String,
    variantA: @Composable () -> Unit,
    variantB: @Composable () -> Unit,
) {
    when (val decision = rememberPinnedExperimentDecision(key)) {
        is ExperimentDecision.Ready -> if (decision.variant == Variant.B) variantB() else variantA()
        else -> variantA()
    }
}

@Composable
private fun rememberPinnedExperimentDecision(key: String): ExperimentDecision {
    var decision by remember(key) { mutableStateOf<ExperimentDecision>(ExperimentDecision.Pending) }
    var pinned by remember(key) { mutableStateOf<ExperimentDecision.Ready?>(null) }

    DisposableEffect(key) {
        val subscription = observeExperiment(key) { update ->
            Snapshot.withMutableSnapshot {
                if (update !is ExperimentDecision.Ready && update != ExperimentDecision.Pending) {
                    pinned = null
                    decision = update
                } else if (pinned == null) {
                    decision = update
                    if (update is ExperimentDecision.Ready) pinned = update
                }
            }
        }
        onDispose { subscription.close() }
    }

    val resolved = pinned ?: decision
    if (resolved is ExperimentDecision.Ready) {
        LaunchedEffect(resolved) { recordExperimentExposure(resolved) }
    }
    return resolved
}
