package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.experiments_guide_back
import com.quietmetrix.dashboard.resources.experiments_guide_done
import com.quietmetrix.dashboard.resources.experiments_guide_next
import com.quietmetrix.dashboard.resources.experiments_guide_skip
import com.quietmetrix.dashboard.resources.experiments_guide_step1_body
import com.quietmetrix.dashboard.resources.experiments_guide_step1_title
import com.quietmetrix.dashboard.resources.experiments_guide_step2_body
import com.quietmetrix.dashboard.resources.experiments_guide_step2_title
import com.quietmetrix.dashboard.resources.experiments_guide_step3_body
import com.quietmetrix.dashboard.resources.experiments_guide_step3_code
import com.quietmetrix.dashboard.resources.experiments_guide_step3_title
import com.quietmetrix.dashboard.resources.experiments_guide_step4_body
import com.quietmetrix.dashboard.resources.experiments_guide_step4_code
import com.quietmetrix.dashboard.resources.experiments_guide_step4_title
import com.quietmetrix.dashboard.resources.experiments_guide_step5_body
import com.quietmetrix.dashboard.resources.experiments_guide_step5_title
import com.quietmetrix.dashboard.resources.experiments_guide_step_of
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/**
 * Step-by-step "how to set up an experiment" guide. It opens on demand via the tab's help icon
 * ([DashboardViewModel.openExperimentsGuide]) so the actual Experiments screen remains visible
 * on first visit. See [EXPERIMENTS_GUIDE_STEP_COUNT] for the step count this must stay in sync with.
 */
@Composable
fun ExperimentsGuide(viewModel: DashboardViewModel, state: DashboardState) {
    if (!state.experimentsGuideOpen) return
    val step = state.experimentsGuideStep
    val isLast = isLastGuideStep(step, EXPERIMENTS_GUIDE_STEP_COUNT)

    AlertDialog(
        onDismissRequest = { viewModel.closeExperimentsGuide() },
        title = {
            Column {
                Text(stringResource(Res.string.experiments_guide_step_of, step + 1, EXPERIMENTS_GUIDE_STEP_COUNT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(guideStepTitle(step), style = MaterialTheme.typography.titleMedium)
            }
        },
        text = { GuideStepBody(step) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (step > 0) {
                    TextButton(onClick = { viewModel.previousExperimentsGuideStep() }, modifier = Modifier.handCursor()) {
                        MaterialText(stringResource(Res.string.experiments_guide_back))
                    }
                }
                TextButton(
                    onClick = { if (isLast) viewModel.closeExperimentsGuide() else viewModel.nextExperimentsGuideStep() },
                    modifier = Modifier.handCursor(),
                ) {
                    MaterialText(stringResource(if (isLast) Res.string.experiments_guide_done else Res.string.experiments_guide_next))
                }
            }
        },
        dismissButton = {
            if (!isLast) {
                TextButton(onClick = { viewModel.closeExperimentsGuide() }, modifier = Modifier.handCursor()) {
                    MaterialText(stringResource(Res.string.experiments_guide_skip))
                }
            }
        },
    )
}

@Composable
private fun guideStepTitle(step: Int) = when (step) {
    0 -> stringResource(Res.string.experiments_guide_step1_title)
    1 -> stringResource(Res.string.experiments_guide_step2_title)
    2 -> stringResource(Res.string.experiments_guide_step3_title)
    3 -> stringResource(Res.string.experiments_guide_step4_title)
    else -> stringResource(Res.string.experiments_guide_step5_title)
}

@Composable
private fun GuideStepBody(step: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (step) {
            0 -> Text(stringResource(Res.string.experiments_guide_step1_body), style = MaterialTheme.typography.bodyMedium)
            1 -> Text(stringResource(Res.string.experiments_guide_step2_body), style = MaterialTheme.typography.bodyMedium)
            2 -> {
                CodeBlock(stringResource(Res.string.experiments_guide_step3_code))
                Text(stringResource(Res.string.experiments_guide_step3_body), style = MaterialTheme.typography.bodyMedium)
            }
            3 -> {
                CodeBlock(stringResource(Res.string.experiments_guide_step4_code))
                Text(stringResource(Res.string.experiments_guide_step4_body), style = MaterialTheme.typography.bodyMedium)
            }
            else -> Text(stringResource(Res.string.experiments_guide_step5_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
