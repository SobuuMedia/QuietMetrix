package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.experiment_confidence_note
import com.quietmetrix.dashboard.resources.experiment_confidence_title
import com.quietmetrix.dashboard.resources.experiment_conversions_label
import com.quietmetrix.dashboard.resources.experiment_exposures_label
import com.quietmetrix.dashboard.resources.experiment_goal_rate_label
import com.quietmetrix.dashboard.resources.experiment_impressions_label
import com.quietmetrix.dashboard.resources.experiment_installation_evidence_collecting
import com.quietmetrix.dashboard.resources.experiment_installation_unit_note
import com.quietmetrix.dashboard.resources.experiment_interactions_label
import com.quietmetrix.dashboard.resources.experiment_meter_max
import com.quietmetrix.dashboard.resources.experiment_meter_min
import com.quietmetrix.dashboard.resources.experiment_original_split
import com.quietmetrix.dashboard.resources.experiment_plain_tie
import com.quietmetrix.dashboard.resources.experiment_plain_winner
import com.quietmetrix.dashboard.resources.experiment_preview_unavailable
import com.quietmetrix.dashboard.resources.experiment_rate_label
import com.quietmetrix.dashboard.resources.experiment_raw_comparison
import com.quietmetrix.dashboard.resources.experiment_raw_stats
import com.quietmetrix.dashboard.resources.experiment_results_not_enough_data
import com.quietmetrix.dashboard.resources.experiment_results_unit_note
import com.quietmetrix.dashboard.resources.experiment_revision_cumulative_note
import com.quietmetrix.dashboard.resources.experiment_rollout_note
import com.quietmetrix.dashboard.resources.experiment_variant_letter
import com.quietmetrix.dashboard.resources.experiment_verdict_not_enough_data
import com.quietmetrix.dashboard.resources.experiment_winner_medal
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.ComparisonNote
import com.quietmetrix.dashboard.ui.components.DashboardCard
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.isLoading
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/** Declared variants, honest unavailable previews, a confidence meter and opt-in raw statistics. */
@Composable
internal fun ExperimentResultsPanel(state: DashboardState, experiment: ExperimentDto, sizeClass: WindowSizeClass) {
    val results = currentExperimentResults(state, experiment)
    val winner = experimentWinner(experiment, results?.comparison)
    val verdict = verdictFor(results?.comparison).let { candidate ->
        if (candidate is ExperimentVerdict.Winner && experiment.variants.none { it.name == candidate.variantName })
            ExperimentVerdict.NotEnoughData else candidate
    }
    val colors = LocalExtendedColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(experiment.name, style = MaterialTheme.typography.headlineSmall)
        if (results?.basis == "since_revision_started") {
            Text(stringResource(Res.string.experiment_revision_cumulative_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (experiment.status == "shipped") {
            Text(stringResource(Res.string.experiment_rollout_note, experiment.selectedVariant.orEmpty()),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.experiment_original_split), style = MaterialTheme.typography.labelMedium)
        }
        ResponsiveRowOrColumn(sizeClass) {
            experiment.variants.forEachIndexed { index, variant ->
                DashboardCard(
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
                        if (state.isLoading(DataSection.ExperimentResults) && results == null) KpiSkeleton(Modifier.fillMaxWidth())
                        else {
                            Text(metric?.rate?.let { formatPercent(it) } ?: stringResource(Res.string.experiment_results_not_enough_data),
                                style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(if (results?.unit == "installations") Res.string.experiment_goal_rate_label else Res.string.experiment_rate_label), style = MaterialTheme.typography.labelMedium)
                            if (state.compare && experiment.mode == "legacy") ComparisonNote(state.comparison.experiment?.variants?.firstOrNull { it.name == variant.name }?.rate?.let(::formatPercent))
                        }
                    }
                }
            }
        }
        DashboardCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.experiment_confidence_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(confidenceScoreFor(verdict)?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineSmall)
                }
                if (results?.unit == "installations" && results.comparison == null) {
                    Text(stringResource(Res.string.experiment_installation_evidence_collecting), style = MaterialTheme.typography.bodySmall)
                } else {
                    ConfidenceMeter(confidenceScoreFor(verdict))
                }
                if (state.compare && experiment.mode == "legacy") ComparisonNote(confidenceScoreFor(verdictFor(state.comparison.experiment?.comparison))?.let { "$it%" })
                Text(when (verdict) {
                    is ExperimentVerdict.NotEnoughData -> stringResource(Res.string.experiment_verdict_not_enough_data)
                    is ExperimentVerdict.NoDifference -> stringResource(Res.string.experiment_plain_tie)
                    is ExperimentVerdict.Winner -> stringResource(Res.string.experiment_plain_winner, verdict.variantName)
                }, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (results?.unit == "installations") Res.string.experiment_installation_unit_note else Res.string.experiment_results_unit_note), style = MaterialTheme.typography.bodySmall)
                if (state.expertMode && results != null) {
                    Column(Modifier.testTag("experiment-raw-statistics"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.experiment_raw_stats), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.experiment_confidence_note), style = MaterialTheme.typography.bodySmall)
                        results.comparison?.let {
                            Text(stringResource(Res.string.experiment_raw_comparison, it.z.toString(), formatPValue(it.pValue)))
                        }
                        results.variants.forEach { metric ->
                            Text(metric.name, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(if (results.unit == "installations") Res.string.experiment_exposures_label else Res.string.experiment_impressions_label) + ": " +
                                (metric.impressions?.let { formatCount(it) } ?: "—"))
                            Text(stringResource(if (results.unit == "installations") Res.string.experiment_conversions_label else Res.string.experiment_interactions_label) + ": " +
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
