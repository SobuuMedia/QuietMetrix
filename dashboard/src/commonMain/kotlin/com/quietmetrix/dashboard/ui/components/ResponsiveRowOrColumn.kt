package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.nav.WindowSizeClass

enum class LayoutFlow { StackedColumn, Row }

/**
 * Pure mapping: compact widths stack children vertically; medium and expanded
 * widths lay them out as a single row. Extracted so the responsive decision is
 * unit-testable without a Compose host.
 */
fun layoutFlowFor(sizeClass: WindowSizeClass): LayoutFlow =
    if (sizeClass == WindowSizeClass.Compact) LayoutFlow.StackedColumn else LayoutFlow.Row

/**
 * A row on desktop/tablet, a column on phone. Children call [ResponsiveScope.fillSlot]
 * on their modifier — weighted in a row, full-width when stacked — so the same
 * content reads correctly at every breakpoint.
 */
@Composable
fun ResponsiveRowOrColumn(
    sizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    content: @Composable ResponsiveScope.() -> Unit,
) {
    if (layoutFlowFor(sizeClass) == LayoutFlow.Row) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier,
        ) {
            ResponsiveScope(sizeClass, rowScope = this).content()
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier,
        ) {
            ResponsiveScope(sizeClass, rowScope = null).content()
        }
    }
}

@Stable
class ResponsiveScope internal constructor(
    private val sizeClass: WindowSizeClass,
    private val rowScope: RowScope?,
) {
    /** Weighted in a row; stretches to full width when stacked in a column. */
    @Stable
    fun Modifier.fillSlot(): Modifier =
        if (rowScope != null) with(rowScope) { this@fillSlot.weight(1f) }
        else this.fillMaxWidth()
}

/**
 * Centers the content and caps its width on ultra-wide viewports so KPI rows
 * and tables don't stretch into unreadable strips on 4K monitors.
 */
@Composable
fun MaxWidthContainer(
    maxWidth: Dp = 1200.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
            content()
        }
    }
}
