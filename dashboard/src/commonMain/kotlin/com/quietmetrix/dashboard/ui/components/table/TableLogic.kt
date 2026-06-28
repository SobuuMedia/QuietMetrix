package com.quietmetrix.dashboard.ui.components.table

// ---------------------------------------------------------------------------
// Pure sort / filter / paginate helpers for the DataTable and the screens
// that feed it. No Compose dependency — fully unit-testable on the JVM.
// ---------------------------------------------------------------------------

/** Nulls-last comparison for an optional Comparable selector value. */
fun <T : Comparable<T>> compareNullsLast(ascending: Boolean): Comparator<T?> =
    Comparator { a, b ->
        if (a == null && b == null) 0
        else if (a == null) 1          // nulls last regardless of direction
        else if (b == null) -1
        else if (ascending) a.compareTo(b)
        else b.compareTo(a)
    }

/**
 * Stable sort by [selector]. Nulls sort last. Returns a new list (does not
 * mutate the input).
 */
fun <T, R : Comparable<R>> sortItems(
    items: List<T>,
    ascending: Boolean,
    selector: (T) -> R?,
): List<T> {
    val cmp = compareNullsLast<R>(ascending)
    return items.sortedWith(Comparator { a, b -> cmp.compare(selector(a), selector(b)) })
}

/**
 * Case-insensitive substring match across any of [fields]. An empty query
 * returns the original list unchanged.
 */
fun <T> filterItems(
    items: List<T>,
    query: String,
    fields: (T) -> List<String?>,
): List<T> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return items
    return items.filter { row ->
        fields(row).any { it != null && it.lowercase().contains(q) }
    }
}

data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int, val total: Int)
/**
 * 1-based pagination. Pages beyond [totalPages] return an empty item list but
 * still report the correct [totalPages]/[total] so the UI can disable controls.
 */
fun <T> paginate(items: List<T>, page: Int, pageSize: Int): Page<T> {
    val total = items.size
    if (total == 0 || pageSize <= 0) return Page(emptyList(), 1, 0, total)
    val totalPages = (total + pageSize - 1) / pageSize
    val safePage = page.coerceAtLeast(1)
    if (safePage > totalPages) return Page(emptyList(), safePage, totalPages, total)
    val from = (safePage - 1) * pageSize
    val to = minOf(from + pageSize, total)
    return Page(items.subList(from, to), safePage, totalPages, total)
}

/**
 * Builds a [Comparator] from a selector returning `Comparable<*>?`. Nulls sort
 * last. Used by the generic DataTable whose columns don't know the comparable
 * type at compile time.
 */
@Suppress("UNCHECKED_CAST")
fun <T> comparatorFor(selector: (T) -> Comparable<*>?, ascending: Boolean): Comparator<T> =
    Comparator { a, b ->
        val ca = selector(a)
        val cb = selector(b)
        when {
            ca == null && cb == null -> 0
            ca == null -> 1
            cb == null -> -1
            else -> {
                val cmp = (ca as Comparable<Any>).compareTo(cb as Comparable<Any>)
                if (ascending) cmp else -cmp
            }
        }
    }
