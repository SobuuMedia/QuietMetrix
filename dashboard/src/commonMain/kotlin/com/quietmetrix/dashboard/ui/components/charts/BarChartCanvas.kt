package com.quietmetrix.dashboard.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
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
 *
 * [highlightIndex] (Task 20) paints exactly one bar in [highlightColor] instead of [barColor] —
 * used by the Overview daily-volume chart card to call out the real "record day" (see
 * `OverviewChartLogic.kt`'s `recordDayIndex`) with a lime bar, per the design handoff's "record day
 * ... a lime bar" (the literal floating "🚀 v2.3 shipped" caption text is deliberately NOT part of
 * this component — see `DailyVolumeSection`'s KDoc). `null` (the default) highlights nothing,
 * so existing callers are unaffected.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BarChartCanvas(
    bars: List<BarData>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: androidx.compose.ui.unit.Dp = 220.dp,
    highlightIndex: Int? = null,
    highlightColor: Color = LocalExtendedColors.current.lime,
    previousValues: List<Float?> = emptyList(),
    previousColor: Color = LocalExtendedColors.current.lavender,
    onBarClick: ((Int) -> Unit)? = null,
) {
    if (bars.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val gridColor = LocalExtendedColors.current.chartGrid
    val tickStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
    )

    val hoverRects = remember(bars, previousValues) { arrayOf(emptyList<BarRect>()) }
    var hovered by remember(bars, previousValues) { mutableStateOf<Int?>(null) }
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val tooltipStyle = TextStyle(color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp)
    val interactive = if (onBarClick == null) modifier else modifier.pointerInput(bars, previousValues, onBarClick) {
        detectTapGestures { position ->
            val left = 44.dp.toPx()
            val top = 8.dp.toPx()
            val areaWidth = size.width - left - 8.dp.toPx()
            val areaHeight = size.height - top - 22.dp.toPx()
            if (areaWidth > 0 && areaHeight > 0) {
                val specs = barRects(bars.map { it.value }, left, top, areaWidth, areaHeight,
                    max = (bars.map { it.value } + previousValues.filterNotNull()).maxOrNull())
                val index = specs.indexOfFirst { position.x >= it.x && position.x <= it.x + it.width &&
                    position.y >= it.y && position.y <= it.y + it.height.coerceAtLeast(1f) }
                if (index >= 0) onBarClick(index)
            }
        }
    }
    val hoverable = if (onBarClick == null) interactive else interactive
        .onPointerEvent(PointerEventType.Move) { event ->
            val position = event.changes.firstOrNull()?.position
            hovered = position?.let { point ->
                hoverRects[0].indexOfFirst { point.x >= it.x && point.x <= it.x + it.width && point.y >= it.y && point.y <= it.y + it.height.coerceAtLeast(1f) }.takeIf { it >= 0 }
            }
        }
        .onPointerEvent(PointerEventType.Exit) { hovered = null }
    Canvas(hoverable.fillMaxWidth().height(chartHeight)) {
        val leftPad = 44.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val areaX = leftPad
        val areaY = topPad
        val areaW = size.width - leftPad - rightPad
        val areaH = size.height - topPad - bottomPad
        if (areaW <= 0f || areaH <= 0f) return@Canvas

        val maxV = (bars.map { it.value } + previousValues.filterNotNull()).maxOrNull()?.coerceAtLeast(1f) ?: 1f
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
        hoverRects[0] = specs
        val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        specs.forEachIndexed { i, spec ->
            previousValues.getOrNull(i)?.let { previous ->
                val height = previous / maxV * areaH
                drawRoundRect(previousColor, topLeft = Offset(spec.x, areaY + areaH - height),
                    size = Size(spec.width, height.coerceAtLeast(1f)), cornerRadius = corner,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))))
            }
            drawRoundRect(
                color = if (i == highlightIndex) highlightColor else barColor,
                topLeft = Offset(spec.x, spec.y),
                size = Size(if (previousValues.isEmpty()) spec.width else spec.width * 0.65f, spec.height.coerceAtLeast(1f)),
                cornerRadius = corner,
            )
        }

        drawXLabels(textMeasurer, tickStyle, bars, specs, areaY + areaH)
        hovered?.let { index ->
            val bar = bars[index]
            val label = textMeasurer.measure("${bar.label}: ${bar.value.toLong()}", tooltipStyle)
            val padding = 6.dp.toPx()
            val width = label.size.width + padding * 2
            val height = label.size.height + padding * 2
            val x = (specs[index].x + specs[index].width / 2 - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
            val y = (specs[index].y - height - padding).coerceAtLeast(0f)
            drawRoundRect(tooltipBackground, Offset(x, y), Size(width, height), corner)
            drawText(label, topLeft = Offset(x + padding, y + padding))
        }
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
