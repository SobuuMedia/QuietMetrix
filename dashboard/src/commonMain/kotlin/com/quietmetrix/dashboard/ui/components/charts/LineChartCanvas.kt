package com.quietmetrix.dashboard.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import kotlin.math.max

data class LineData(val label: String, val value: Float)

/**
 * Line chart with translucent area fill, y-axis gridlines and thinned x labels.
 * Used for daily session counts.
 */
@Composable
fun LineChartCanvas(
    points: List<LineData>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: androidx.compose.ui.unit.Dp = 160.dp,
) {
    if (points.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val gridColor = LocalExtendedColors.current.chartGrid
    val tickStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)

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

        val maxV = points.maxOf { it.value }.coerceAtLeast(1f)
        yTicks(maxV).forEach { t ->
            val y = areaY + areaH - (t / maxV) * areaH
            drawLine(gridColor, Offset(areaX, y), Offset(areaX + areaW, y), strokeWidth = 1f)
            val measured = textMeasurer.measure(formatCount(t.toLong()), tickStyle)
            drawText(
                measured,
                topLeft = Offset(
                    (areaX - measured.size.width - 4.dp.toPx()).coerceAtLeast(0f),
                    y - measured.size.height / 2f,
                ),
            )
        }

        val pts = linePoints(points.map { it.value }, areaX, areaY, areaW, areaH, max = maxV)
        if (pts.size > 1) {
            val areaPath = Path().apply {
                moveTo(pts.first().x, areaY + areaH)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, areaY + areaH)
                close()
            }
            drawPath(areaPath, lineColor.copy(alpha = 0.15f))
            val linePath = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            drawPath(linePath, lineColor, style = Stroke(width = 2.dp.toPx()))
        }
        pts.forEach { p ->
            drawCircle(lineColor, radius = 2.dp.toPx(), center = Offset(p.x, p.y))
        }

        drawXLabels(textMeasurer, tickStyle, points, pts, areaY + areaH)
    }
}

private fun DrawScope.drawXLabels(
    textMeasurer: TextMeasurer,
    style: TextStyle,
    points: List<LineData>,
    pts: List<LinePoint>,
    labelsTop: Float,
) {
    val n = points.size
    if (n == 0) return
    val labelStep = max(1, (n + 5) / 6)
    for (i in points.indices) {
        if (i % labelStep != 0 && i != n - 1) continue
        val measured = textMeasurer.measure(points[i].label, style)
        drawText(
            measured,
            topLeft = Offset(
                (pts[i].x - measured.size.width / 2f),
                labelsTop + 4.dp.toPx(),
            ),
        )
    }
}
