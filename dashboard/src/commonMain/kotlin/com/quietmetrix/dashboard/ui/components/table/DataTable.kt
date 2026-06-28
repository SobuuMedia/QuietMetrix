package com.quietmetrix.dashboard.ui.components.table

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_chevron_down
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
 * A reusable table: sticky header, zebra rows, optional per-column sorting and
 * columns that hide on compact widths. Sorting is driven by [Column.sortSelector]
 * and the pure [comparatorFor] helper. No Unicode glyphs — the sort indicator
 * is a rotated vector icon so it can't render as tofu on wasmJs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> DataTable(
    columns: List<Column<T>>,
    rows: List<T>,
    sizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    initialSort: SortColumn? = null,
    rowKey: ((T) -> Any)? = null,
) {
    val visibleCols = remember(columns, sizeClass) {
        columns.filter { !(it.hideOnCompact && sizeClass == WindowSizeClass.Compact) }
    }
    var sort by remember {
        mutableStateOf(
            initialSort?.let { SortState(it.index, it.ascending) } ?: SortState(-1, true),
        )
    }

    val sorted = remember(rows, sort, visibleCols) {
        val idx = sort.index
        if (idx in visibleCols.indices && visibleCols[idx].sortSelector != null) {
            val selector = visibleCols[idx].sortSelector!!
            rows.sortedWith(comparatorFor(selector, sort.ascending))
        } else {
            rows
        }
    }

    val headerColor = MaterialTheme.colorScheme.surface
    val zebraColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(modifier = modifier) {
        stickyHeader {
            Row(
                modifier = Modifier.fillMaxWidth().background(headerColor).padding(vertical = 8.dp),
            ) {
                visibleCols.forEachIndexed { i, col ->
                    val sortable = col.sortSelector != null
                    val active = i == sort.index && sortable
                    Row(
                        modifier = Modifier
                            .weight(col.weight)
                            .then(if (sortable) Modifier.clickable {
                                sort = if (sort.index == i) SortState(i, !sort.ascending)
                                else SortState(i, ascending = false)
                            } else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            col.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.primary else onVariant,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (sortable) {
                            Spacer(Modifier.width(2.dp))
                            if (active) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_chevron_down),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp).then(
                                        if (sort.ascending) Modifier.rotate(180f) else Modifier,
                                    ),
                                )
                            } else {
                                Spacer(Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        itemsIndexed(sorted, key = { idx, row -> rowKey?.invoke(row) ?: idx }) { idx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (idx % 2 == 1) zebraColor else Color.Transparent)
                    .padding(vertical = 8.dp),
            ) {
                visibleCols.forEach { col ->
                    Box(modifier = Modifier.weight(col.weight), contentAlignment = Alignment.CenterStart) {
                        col.cell(row)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
