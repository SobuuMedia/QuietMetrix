package com.quietmetrix.dashboard.ui.components

import com.quietmetrix.dashboard.ui.components.table.Page
import com.quietmetrix.dashboard.ui.components.table.comparatorFor
import com.quietmetrix.dashboard.ui.components.table.compareNullsLast
import com.quietmetrix.dashboard.ui.components.table.filterItems
import com.quietmetrix.dashboard.ui.components.table.paginate
import com.quietmetrix.dashboard.ui.components.table.sortItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class Row(val name: String, val count: Int, val screen: String?)

class TableLogicTest {

    private val rows = listOf(
        Row("tap", 3, "Home"),
        Row("scroll", 10, null),
        Row("tap", 1, "Settings"),
        Row("view", 10, "Home"),
    )

    @Test
    fun compareNullsLastPutsNullsAtEndForAscending() {
        // ascending: nulls last
        val cmp = compareNullsLast<String>(ascending = true)
        assertTrue(cmp.compare(null, "a") > 0)
        assertTrue(cmp.compare("a", null) < 0)
        assertTrue(cmp.compare("a", "b") < 0)
    }

    @Test
    fun sortItemsByCountDescending() {
        val sorted = sortItems(rows, ascending = false) { it.count }
        assertEquals(listOf(10, 10, 3, 1), sorted.map { it.count })
    }

    @Test
    fun sortItemsStableForEqualKeys() {
        val sorted = sortItems(rows, ascending = false) { it.count }
        // the two count=10 rows keep their original relative order (stable sort)
        assertEquals(listOf("scroll", "view"), sorted.filter { it.count == 10 }.map { it.name })
    }

    @Test
    fun sortItemsAscendingWithNullsLast() {
        val sorted = sortItems(rows, ascending = true) { it.screen }
        // non-null screens sorted ascending, null screens last
        assertEquals(listOf("Home", "Home", "Settings", null), sorted.map { it.screen })
    }

    @Test
    fun filterItemsMatchesAnyFieldCaseInsensitive() {
        val filtered = filterItems(rows, "tap") { listOf(it.name, it.screen) }
        assertEquals(listOf("tap", "tap"), filtered.map { it.name })
    }

    @Test
    fun filterItemsEmptyQueryReturnsAll() {
        assertEquals(rows, filterItems(rows, "") { listOf(it.name) })
    }

    @Test
    fun paginateSinglePage() {
        val page: Page<Row> = paginate(rows, page = 1, pageSize = 20)
        assertEquals(4, page.items.size)
        assertEquals(1, page.totalPages)
        assertEquals(4, page.total)
    }

    @Test
    fun paginateMultiPage() {
        val big = (1..50).map { Row("e$it", it, null) }
        val p1 = paginate(big, page = 1, pageSize = 20)
        val p2 = paginate(big, page = 2, pageSize = 20)
        val p3 = paginate(big, page = 3, pageSize = 20)
        assertEquals(20, p1.items.size)
        assertEquals(20, p2.items.size)
        assertEquals(10, p3.items.size)
        assertEquals(3, p2.totalPages)
    }

    @Test
    fun paginateOutOfRangeIsEmpty() {
        val page = paginate(rows, page = 99, pageSize = 20)
        assertTrue(page.items.isEmpty())
        assertEquals(1, page.totalPages)
    }

    @Test
    fun paginateEmptyInput() {
        val page = paginate(emptyList<Row>(), page = 1, pageSize = 20)
        assertTrue(page.items.isEmpty())
        assertEquals(0, page.totalPages)
    }

    @Test
    fun comparatorForSortsAndNullsLast() {
        val sortedDesc = rows.sortedWith(comparatorFor({ it.screen }, ascending = false))
        assertEquals(listOf(null), sortedDesc.filter { it.screen == null }.map { it.screen })
        assertEquals(listOf("Settings", "Home", "Home"), sortedDesc.filterNotNullScreen())
        val sortedAsc = rows.sortedWith(comparatorFor({ it.count }, ascending = true))
        assertEquals(listOf(1, 3, 10, 10), sortedAsc.map { it.count })
    }

    private fun List<Row>.filterNotNullScreen() = mapNotNull { it.screen }
}
