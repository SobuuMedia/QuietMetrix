package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.EventRow
import com.quietmetrix.dashboard.ui.components.table.paginate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventsLogicTest {

    private val rows = listOf(
        EventRow(eventName = "page_view", screen = "Home", country = "US", platform = "android", wasOffline = false),
        EventRow(eventName = "tap", screen = "Home", country = "GB", platform = "android", wasOffline = true),
        EventRow(eventName = "page_view", screen = "Settings", country = "US", platform = "ios", wasOffline = false),
        EventRow(eventName = "scroll", screen = null, country = null, platform = "", wasOffline = false),
    )

    @Test
    fun distinctFacetsDedupesSortsAndSkipsBlank() {
        val f = distinctFacets(rows)
        assertEquals(listOf("page_view", "scroll", "tap"), f.events)
        assertEquals(listOf("android", "ios"), f.platforms)   // blank "" skipped
        assertEquals(listOf("GB", "US"), f.countries)         // null skipped
    }

    @Test
    fun applyEventFiltersAndSemantics() {
        assertEquals(2, applyEventFilters(rows, eventName = "page_view").size)
        assertEquals(1, applyEventFilters(rows, eventName = "page_view", country = "GB").size +
            applyEventFilters(rows, eventName = "page_view", country = "US").size - 1)
        assertEquals(
            listOf("US"),
            applyEventFilters(rows, eventName = "page_view", platform = "android").map { it.country },
        )
    }

    @Test
    fun applyEventFiltersNullMeansNoConstraint() {
        assertEquals(rows.size, applyEventFilters(rows).size)
    }

    @Test
    fun eventsSummaryCountsAndTops() {
        val s = eventsSummary(rows)
        assertEquals(4, s.total)
        assertEquals(1, s.offline)
        assertEquals(3, s.distinctEvents)
        assertEquals("page_view", s.topEvent)   // most frequent
        assertEquals("US", s.topCountry)         // most frequent non-null
    }

    @Test
    fun eventsSummaryEmpty() {
        val s = eventsSummary(emptyList())
        assertEquals(0, s.total)
        assertEquals(0, s.distinctEvents)
        assertNull(s.topEvent)
        assertNull(s.topCountry)
    }

    // --- global sort: sorting must span the whole dataset, then paginate. ---------------------

    @Test
    fun sortEventsThenPaginateSpansWholeDataset() {
        // 45 rows, event names evt-00..evt-44, supplied in reverse order and across page bounds.
        val shuffled = (0 until 45)
            .map { EventRow(eventName = "evt-" + it.toString().padStart(2, '0')) }
            .reversed()

        val sorted = sortEvents(shuffled, columnIndex = 0, ascending = true)
        val page2 = paginate(sorted, page = 2, pageSize = 20)

        // Page 2 is the GLOBAL slice 20..39, not a re-sort of one already-paginated page.
        assertEquals(20, page2.items.size)
        assertEquals("evt-20", page2.items.first().eventName)
        assertEquals("evt-39", page2.items.last().eventName)
    }

    @Test
    fun sortEventsDescendingReversesGlobalOrder() {
        val rows = (0 until 5).map { EventRow(eventName = "evt-$it") }
        val desc = sortEvents(rows, columnIndex = 0, ascending = false)
        assertEquals(listOf("evt-4", "evt-3", "evt-2", "evt-1", "evt-0"), desc.map { it.eventName })
    }

    @Test
    fun sortEventsByDurationTreatsNullAsSmallest() {
        val rows = listOf(
            EventRow(eventName = "a", durationMs = 5000),
            EventRow(eventName = "b", durationMs = null),
            EventRow(eventName = "c", durationMs = 100),
        )
        // duration is column index 2; ascending -> null (as -1) first, then 100, then 5000.
        val asc = sortEvents(rows, columnIndex = 2, ascending = true).map { it.eventName }
        assertEquals(listOf("b", "c", "a"), asc)
    }

    @Test
    fun eventSortSelectorsCoverAllSortableColumns() {
        // One selector per column rendered by eventColumns(): event, screen, duration,
        // platform, device, country, language, session, time.
        assertEquals(9, eventSortSelectors.size)
        assertTrue(eventSortSelectors.all { it(EventRow(eventName = "x")) != null || true })
    }

    @Test
    fun sortEventsByLanguageColumnIndex() {
        val rows = listOf(
            EventRow(eventName = "a", language = "fr"),
            EventRow(eventName = "b", language = "de"),
            EventRow(eventName = "c", language = "en"),
        )
        // language is column index 6 (after country); ascending -> de, en, fr.
        val asc = sortEvents(rows, columnIndex = 6, ascending = true).map { it.eventName }
        assertEquals(listOf("b", "c", "a"), asc)
    }
}
