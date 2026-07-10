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
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import kotlin.math.max

data class BarData(val label: String, val value: Float)

/**
 * Vertical bar chart with y-axis gridlines + tick labels and a thinned-out set
 * of x-axis labels. Replaces the old hand-drawn "Daily volume" rectangle rows
 * and shows the full selected window instead of just the last 14 days.
 */
@Composable
fun BarChartCanvas(
    bars: List<BarData>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: androidx.compose.ui.unit.Dp = 220.dp,
) {
    if (bars.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val gridColor = LocalExtendedColors.current.chartGrid
    val tickStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
    )

    Canvas(modifier.fillMaxWidth().height(chartHeight)) {
        val leftPad = 44.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val areaX = leftPad
        val areaY = topPad
        val areaW = size.width - leftPad - rightPad
        val areaH = size.height - topPad - bottomPad
        if (areaW <= 0f || areaH <= 0f) return@Canvas

        val maxV = bars.maxOf { it.value }.coerceAtLeast(1f)
        drawYAxis(textMeasurer, tickStyle, gridColor, areaX, areaY, areaW, areaH, maxV)

        val specs = barRects(
            values = bars.map { it.value },
            areaX = areaX,
            areaY = areaY,
            areaWidth = areaW,
            areaHeight = areaH,
            gapFraction = 0.2f,
            max = maxV,
        )
        val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        specs.forEach { spec ->
            drawRoundRect(
                color = barColor,
                topLeft = Offset(spec.x, spec.y),
                size = Size(spec.width, spec.height.coerceAtLeast(1f)),
                cornerRadius = corner,
            )
        }

        drawXLabels(textMeasurer, tickStyle, bars, specs, areaY + areaH)
    }
}

private fun DrawScope.drawYAxis(
    textMeasurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
    areaX: Float,
    areaY: Float,
    areaW: Float,
    areaH: Float,
    maxV: Float,
) {
    yTicks(maxV).forEach { t ->
        val y = areaY + areaH - (t / maxV) * areaH
        drawLine(
            color = gridColor,
            start = Offset(areaX, y),
            end = Offset(areaX + areaW, y),
            strokeWidth = 1f,
        )
        val measured = textMeasurer.measure(formatCount(t.toLong()), style)
        drawText(
            measured,
            topLeft = Offset(
                (areaX - measured.size.width - 4.dp.toPx()).coerceAtLeast(0f),
                y - measured.size.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawXLabels(
    textMeasurer: TextMeasurer,
    style: TextStyle,
    bars: List<BarData>,
    specs: List<BarRect>,
    labelsTop: Float,
) {
    val n = bars.size
    if (n == 0) return
    val labelStep = max(1, (n + 5) / 6)
    for (i in bars.indices) {
        if (i % labelStep != 0 && i != n - 1) continue
        val spec = specs[i]
        val measured = textMeasurer.measure(bars[i].label, style)
        drawText(
            measured,
            topLeft = Offset(
                (spec.x + spec.width / 2f - measured.size.width / 2f),
                labelsTop + 4.dp.toPx(),
            ),
        )
    }
}
