package com.quietmetrix.dashboard.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.donut_total
import com.quietmetrix.dashboard.resources.ic_overview
import com.quietmetrix.dashboard.resources.state_no_data
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.ResponsiveScope
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

data class DonutData(val label: String, val value: Float)

/**
 * Donut chart with a responsive legend: donut + legend side by side on
 * desktop, stacked on phone. Total is drawn in the centre hole. Shows an empty
 * state when there is nothing to plot.
 */
@Composable
fun DonutChart(
    data: List<DonutData>,
    sizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) {
        EmptyState(Res.drawable.ic_overview, Res.string.state_no_data, modifier)
        return
    }
    val series = LocalExtendedColors.current.chartSeries
    val arcs = donutArcs(data.map { it.value }, data.map { it.label })
    val total = data.sumOf { it.value.toDouble() }.toLong()

    ResponsiveRowOrColumn(sizeClass, modifier, spacing = 16.dp) {
        DonutSlice(sizeClass, arcs, series, total, Modifier.fillSlot())
        DonutLegend(arcs, series, Modifier.fillSlot())
    }
}

@Composable
private fun ResponsiveScope.DonutSlice(
    sizeClass: WindowSizeClass,
    arcs: List<DonutSlice>,
    series: List<androidx.compose.ui.graphics.Color>,
    total: Long,
    modifier: Modifier,
) {
    val boxHeight = if (sizeClass == WindowSizeClass.Compact) 180.dp else 160.dp
    Box(modifier.height(boxHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(boxHeight)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outer = min(cx, cy) - 2.dp.toPx()
            val thickness = outer * 0.34f
            arcs.forEachIndexed { i, slice ->
                if (slice.sweepAngle <= 0f) return@forEachIndexed
                drawArc(
                    color = series[i % series.size],
                    startAngle = slice.startAngle,
                    sweepAngle = slice.sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - outer, cy - outer),
                    size = Size(outer * 2f, outer * 2f),
                    style = Stroke(width = thickness, cap = StrokeCap.Butt),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatCount(total), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.donut_total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DonutLegend(
    arcs: List<DonutSlice>,
    series: List<androidx.compose.ui.graphics.Color>,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val none = stringResource(Res.string.value_none)
        arcs.forEachIndexed { i, slice ->
            val color = series[i % series.size]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                Spacer(Modifier.width(8.dp))
                Text(
                    slice.label.ifBlank { none },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(slice.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    formatCount(slice.value.toLong()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
