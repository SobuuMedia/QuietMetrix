package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ExperimentDto
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.format.formatWilsonRange
import com.quietmetrix.dashboard.format.wilsonInterval
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.*
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.viewmodel.DashboardState
import org.jetbrains.compose.resources.stringResource

/** Declared variants, honest unavailable previews, a confidence meter and opt-in raw statistics. */
@Composable
internal fun ExperimentResultsPanel(state: DashboardState, experiment: ExperimentDto, sizeClass: WindowSizeClass) {
    val results = state.experimentResults?.takeIf { it.experiment.key == experiment.key }
    val winner = experimentWinner(experiment, results?.comparison)
    val verdict = verdictFor(results?.comparison).let { candidate ->
        if (candidate is ExperimentVerdict.Winner && experiment.variants.none { it.name == candidate.variantName })
            ExperimentVerdict.NotEnoughData else candidate
    }
    val colors = LocalExtendedColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(experiment.name, style = MaterialTheme.typography.headlineSmall)
        if (experiment.status == "shipped") {
            Text(stringResource(Res.string.experiment_rollout_note, experiment.selectedVariant.orEmpty()),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.experiment_original_split), style = MaterialTheme.typography.labelMedium)
        }
        ResponsiveRowOrColumn(sizeClass) {
            experiment.variants.forEachIndexed { index, variant ->
                Card(
                    modifier = Modifier.fillSlot().testTag("experiment-variant-${variant.name}"),
                    border = BorderStroke(if (winner == variant.name) 2.dp else 1.dp,
                        if (winner == variant.name) colors.lime else colors.hairline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.experiment_variant_letter, ('A' + index).toString(), variant.name),
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                color = if (index == 1) colors.lavender else MaterialTheme.colorScheme.onSurface)
                            if (winner == variant.name) Text(stringResource(Res.string.experiment_winner_medal), Modifier.testTag("experiment-winner-${variant.name}"))
                        }
                        Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, colors.hairline)) {
                            Box(Modifier.fillMaxWidth().height(120.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.experiment_preview_unavailable),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val metric = results?.variants?.firstOrNull { it.name == variant.name }
                        if (state.loading && results == null) KpiSkeleton(Modifier.fillMaxWidth())
                        else {
                            Text(metric?.rate?.let { formatPercent(it) } ?: stringResource(Res.string.experiment_results_not_enough_data),
                                style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(Res.string.experiment_rate_label), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.experiment_confidence_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(confidenceScoreFor(verdict)?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineSmall)
                }
                ConfidenceMeter(confidenceScoreFor(verdict))
                Text(when (verdict) {
                    is ExperimentVerdict.NotEnoughData -> stringResource(Res.string.experiment_verdict_not_enough_data)
                    is ExperimentVerdict.NoDifference -> stringResource(Res.string.experiment_plain_tie)
                    is ExperimentVerdict.Winner -> stringResource(Res.string.experiment_plain_winner, verdict.variantName)
                }, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.experiment_results_unit_note), style = MaterialTheme.typography.bodySmall)
                if (state.expertMode && results != null) {
                    Column(Modifier.testTag("experiment-raw-statistics"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.experiment_raw_stats), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.experiment_confidence_note), style = MaterialTheme.typography.bodySmall)
                        results.comparison?.let {
                            Text(stringResource(Res.string.experiment_raw_comparison, it.z.toString(), formatPValue(it.pValue)))
                        }
                        results.variants.forEach { metric ->
                            Text(metric.name, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(Res.string.experiment_impressions_label) + ": " +
                                (metric.impressions?.let { formatCount(it) } ?: "—"))
                            Text(stringResource(Res.string.experiment_interactions_label) + ": " +
                                (metric.interactions?.let { formatCount(it) } ?: "—"))
                            if (metric.impressions != null && metric.interactions != null) {
                                wilsonInterval(metric.interactions, metric.impressions)?.let { Text(formatWilsonRange(it)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A bounded 0–100 score track with the actual 95 significance boundary marked independently. */
@Composable
internal fun ConfidenceMeter(score: Int?) {
    val colors = LocalExtendedColors.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().testTag("experiment-confidence-meter")) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            drawRoundRect(track, topLeft = Offset(0f, 5.dp.toPx()), size = Size(size.width, 8.dp.toPx()))
            if (score != null) drawRoundRect(colors.lime, topLeft = Offset(0f, 5.dp.toPx()),
                size = Size(size.width * score.coerceIn(0, 100) / 100f, 8.dp.toPx()))
            drawLine(marker, Offset(size.width * .95f, 0f), Offset(size.width * .95f, size.height), 2.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.experiment_meter_min), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(Res.string.experiment_meter_max), style = MaterialTheme.typography.labelSmall)
        }
    }
}
