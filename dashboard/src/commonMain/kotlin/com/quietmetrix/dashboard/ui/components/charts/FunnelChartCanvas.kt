package com.quietmetrix.dashboard.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.theme.LocalExtendedColors

data class FunnelStepData(val label: String, val count: Int)

/**
 * Horizontal step-conversion chart: one tapering bar per funnel step, centered on a shared
 * vertical axis, width proportional to that step's share of the entry count. A thin
 * drop-off wedge — the gap between this bar's edge and the previous step's — is drawn where
 * the funnel narrows, in [LocalExtendedColors]'s dedicated drop-off color.
 */
@Composable
fun FunnelChartCanvas(
    steps: List<FunnelStepData>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    rowHeight: androidx.compose.ui.unit.Dp = 64.dp,
) {
    if (steps.isEmpty() || steps.all { it.count == 0 }) return
    val textMeasurer = rememberTextMeasurer()
    val dropoffColor = LocalExtendedColors.current.chartDropoff
    // Labels live INSIDE the bar (contrasting color) when the bar is wide enough to hold
    // them, and just after the bar's edge (muted color) otherwise — see drawStepLabels. A
    // fixed vertical position (e.g. "above the bar") doesn't work here: every bar shares the
    // same height, so the entry step's bar spans almost the whole row and leaves no room
    // above it.
    val onBarStyle = TextStyle(color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp)
    val offBarStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

    val chartHeight = rowHeight * steps.size

    Canvas(modifier.fillMaxWidth().height(chartHeight)) {
        val leftPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 4.dp.toPx()
        val bottomPad = 4.dp.toPx()
        val areaX = leftPad
        val areaY = topPad
        val areaW = size.width - leftPad - rightPad
        val areaH = size.height - topPad - bottomPad
        if (areaW <= 0f || areaH <= 0f) return@Canvas

        val bars = funnelBars(
            counts = steps.map { it.count },
            areaX = areaX,
            areaY = areaY,
            areaWidth = areaW,
            areaHeight = areaH,
        )
        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        bars.forEachIndexed { i, bar ->
            if (i > 0) {
                drawDropoffWedge(bars[i - 1], bar, dropoffColor)
            }
            drawRoundRect(
                color = barColor,
                topLeft = Offset(bar.x, bar.y),
                size = Size(bar.width, bar.height),
                cornerRadius = corner,
            )
            drawStepLabels(textMeasurer, onBarStyle, offBarStyle, steps[i], bar, areaX, areaW)
        }
    }
}

/** The trapezoid where the funnel narrows from [previous]'s edges to [current]'s. */
private fun DrawScope.drawDropoffWedge(previous: FunnelBar, current: FunnelBar, color: Color) {
    if (previous.width <= current.width) return
    val top = previous.y + previous.height
    val bottom = current.y
    if (bottom <= top) return
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(previous.x, top)
        lineTo(previous.x + previous.width, top)
        lineTo(current.x + current.width, bottom)
        lineTo(current.x, bottom)
        close()
    }
    drawPath(path, color = color.copy(alpha = 0.12f))
}

/**
 * Places the step name and count+percentage relative to the bar, not a fixed row position:
 * every bar shares the same height (only width varies with the step's share of entry), so
 * for a near-full-width bar there is no space above or beside it to draw into. When the bar
 * is wide enough to hold both strings, they're drawn INSIDE it in a contrasting color;
 * otherwise they sit just after the bar's edge in the chart's normal muted text color.
 */
private fun DrawScope.drawStepLabels(
    textMeasurer: TextMeasurer,
    onBarStyle: TextStyle,
    offBarStyle: TextStyle,
    step: FunnelStepData,
    bar: FunnelBar,
    areaX: Float,
    areaW: Float,
) {
    val valueText = "${formatCount(step.count.toLong())} (${formatPercent(bar.fraction.toDouble())})"
    val innerPad = 10.dp.toPx()
    val centerY = bar.y + bar.height / 2f

    val labelOnBar = textMeasurer.measure(step.label, onBarStyle)
    val valueOnBar = textMeasurer.measure(valueText, onBarStyle)
    val fitsInsideBar = bar.width >= labelOnBar.size.width + valueOnBar.size.width + innerPad * 3

    if (fitsInsideBar) {
        drawText(labelOnBar, topLeft = Offset(bar.x + innerPad, centerY - labelOnBar.size.height / 2f))
        drawText(
            valueOnBar,
            topLeft = Offset(bar.x + bar.width - valueOnBar.size.width - innerPad, centerY - valueOnBar.size.height / 2f),
        )
    } else {
        val labelOff = textMeasurer.measure(step.label, offBarStyle)
        val valueOff = textMeasurer.measure(valueText, offBarStyle)
        drawText(labelOff, topLeft = Offset(bar.x + bar.width + innerPad, centerY - labelOff.size.height / 2f))
        drawText(valueOff, topLeft = Offset(areaX + areaW - valueOff.size.width, centerY - valueOff.size.height / 2f))
    }
}
