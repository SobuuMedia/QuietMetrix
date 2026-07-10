package com.quietmetrix.dashboard.ui.components.table

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_chevron_down
import com.quietmetrix.dashboard.ui.components.handCursor
import org.jetbrains.compose.resources.painterResource

data class Column<T>(
    val title: String,
    val weight: Float,
    val cell: @Composable (T) -> Unit,
    val sortSelector: ((T) -> Comparable<*>?)? = null,
    val hideOnCompact: Boolean = false,
)

private data class SortState(val index: Int, val ascending: Boolean)

data class SortColumn(val index: Int, val ascending: Boolean)

/**
 * A reusable table: sticky header, padded rows with soft zebra striping, hover and selection
 * highlights, optional per-column sorting and columns that hide on compact widths. Sorting is
 * driven by [Column.sortSelector] and the pure [comparatorFor] helper; the row background comes
 * from the pure [rowShade]. No Unicode glyphs — the sort indicator is a rotated vector icon so it
 * can't render as tofu on wasmJs. Pass [selectedRowKey] (compared against [rowKey]) to keep the
 * open/active row highlighted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> DataTable(
    columns: List<Column<T>>,
    rows: List<T>,
    sizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    initialSort: SortColumn? = null,
    sort: SortColumn? = null,
    onSortChange: ((SortColumn) -> Unit)? = null,
    rowKey: ((T) -> Any)? = null,
    selectedRowKey: Any? = null,
    onRowClick: ((T) -> Unit)? = null,
) {
    // Controlled-sort mode: when the caller passes onSortChange it owns the sort state and has
    // already sorted `rows` (e.g. across the whole dataset before pagination). Otherwise the table
    // sorts its own rows internally. Sort indices refer to the FULL `columns` list (not the
    // compact-filtered set) so caller and table agree regardless of which columns are hidden.
    val controlled = onSortChange != null
    val visibleCols = remember(columns, sizeClass) {
        columns.withIndex().filter { !(it.value.hideOnCompact && sizeClass == WindowSizeClass.Compact) }
    }
    var internalSort by remember {
        mutableStateOf(
            initialSort?.let { SortState(it.index, it.ascending) } ?: SortState(-1, true),
        )
    }
    val activeSort: SortState =
        if (controlled) sort?.let { SortState(it.index, it.ascending) } ?: SortState(-1, true)
        else internalSort

    val sorted = remember(rows, activeSort, controlled, columns) {
        if (controlled) {
            rows
        } else {
            val col = columns.getOrNull(activeSort.index)
            if (col?.sortSelector != null) rows.sortedWith(comparatorFor(col.sortSelector, activeSort.ascending))
            else rows
        }
    }

    fun toggleSort(originalIndex: Int) {
        val next = if (activeSort.index == originalIndex) SortState(originalIndex, !activeSort.ascending)
        else SortState(originalIndex, ascending = false)
        if (controlled) onSortChange?.invoke(SortColumn(next.index, next.ascending))
        else internalSort = next
    }

    val scheme = MaterialTheme.colorScheme
    val onVariant = scheme.onSurfaceVariant
    // Soft, single separation: a light zebra plus hover/selection tints. No per-row divider.
    fun shadeColor(shade: RowShade): Color = when (shade) {
        RowShade.Default -> Color.Transparent
        RowShade.Zebra -> scheme.surfaceVariant.copy(alpha = 0.25f)
        RowShade.Hovered -> scheme.onSurface.copy(alpha = 0.05f)
        RowShade.Selected -> scheme.primary.copy(alpha = 0.12f)
    }

    LazyColumn(modifier = modifier) {
        stickyHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleCols.forEach { (originalIndex, col) ->
                    val sortable = col.sortSelector != null
                    val active = originalIndex == activeSort.index && sortable
                    Row(
                        modifier = Modifier
                            .weight(col.weight)
                            .then(if (sortable) Modifier.handCursor().clickable { toggleSort(originalIndex) } else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            col.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) scheme.primary else onVariant,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (sortable) {
                            Spacer(Modifier.width(3.dp))
                            if (active) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_chevron_down),
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(14.dp).then(
                                        if (activeSort.ascending) Modifier.rotate(180f) else Modifier,
                                    ),
                                )
                            } else {
                                Spacer(Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = scheme.outline)
        }

        itemsIndexed(sorted, key = { idx, row -> rowKey?.invoke(row) ?: idx }) { idx, row ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val selected = rowKey != null && selectedRowKey != null && rowKey(row) == selectedRowKey
            val shade = rowShade(idx, selected, hovered)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .hoverable(interaction)
                    .then(if (onRowClick != null) Modifier.handCursor().clickable { onRowClick(row) } else Modifier)
                    .background(shadeColor(shade))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleCols.forEach { (_, col) ->
                    Box(modifier = Modifier.weight(col.weight), contentAlignment = Alignment.CenterStart) {
                        col.cell(row)
                    }
                }
            }
        }
    }
}
