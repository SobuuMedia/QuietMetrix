package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.nav.WindowSizeClass

enum class LayoutFlow { StackedColumn, TwoColumnGrid, Row }

/** Display type that scales with available content width for phone, tablet and desktop heroes. */
@Composable
fun responsiveDisplayStyle(maxWidth: Dp) = when {
    maxWidth < 600.dp -> MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, lineHeight = 34.sp)
    maxWidth < 900.dp -> MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 46.sp)
    else -> MaterialTheme.typography.displayLarge
}

/**
 * Pure mapping: compact widths stack children vertically; medium and expanded
 * widths lay them out as a single row. Extracted so the responsive decision is
 * unit-testable without a Compose host.
 */
fun layoutFlowFor(sizeClass: WindowSizeClass): LayoutFlow =
    when (sizeClass) {
        WindowSizeClass.Compact -> LayoutFlow.StackedColumn
        WindowSizeClass.Medium -> LayoutFlow.TwoColumnGrid
        WindowSizeClass.Expanded -> LayoutFlow.Row
    }

/**
 * Four-across row on desktop, two-column grid on tablets, and a full-width
 * column on phones. Children use [ResponsiveScope.fillSlot] to share each row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResponsiveRowOrColumn(
    sizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    content: @Composable ResponsiveScope.() -> Unit,
) {
    when (layoutFlowFor(sizeClass)) {
        LayoutFlow.Row -> Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier,
        ) { ResponsiveScope(sizeClass, rowScope = this, flowRowScope = null).content() }
        LayoutFlow.TwoColumnGrid -> FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier,
        ) { ResponsiveScope(sizeClass, rowScope = null, flowRowScope = this).content() }
        LayoutFlow.StackedColumn -> Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier,
        ) {
            ResponsiveScope(sizeClass, rowScope = null, flowRowScope = null).content()
        }
    }
}

@Stable
class ResponsiveScope internal constructor(
    private val sizeClass: WindowSizeClass,
    private val rowScope: RowScope?,
    private val flowRowScope: FlowRowScope?,
) {
    /** Weighted in a row; stretches to full width when stacked in a column. */
    @Stable
    fun Modifier.fillSlot(weight: Float = 1f): Modifier = when {
        rowScope != null -> with(rowScope) { this@fillSlot.weight(weight) }
        flowRowScope != null -> with(flowRowScope) { this@fillSlot.weight(weight) }
        else -> this.fillMaxWidth()
    }
}

/**
 * Centers the content and caps its width on ultra-wide viewports so KPI rows
 * and tables don't stretch into unreadable strips on 4K monitors.
 */
@Composable
fun MaxWidthContainer(
    maxWidth: Dp = 1360.dp,
    horizontalPadding: Dp = 32.dp,
    sizeClass: WindowSizeClass? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gutter = when {
            sizeClass == WindowSizeClass.Compact || (sizeClass == null && maxWidth < 600.dp) -> 16.dp
            sizeClass == WindowSizeClass.Medium || (sizeClass == null && maxWidth < 1024.dp) -> 24.dp
            else -> horizontalPadding
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { content() }
            }
        }
    }
}
