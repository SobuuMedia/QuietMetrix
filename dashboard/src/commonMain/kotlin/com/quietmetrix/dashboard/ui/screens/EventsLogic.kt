package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.EventRow
import com.quietmetrix.dashboard.ui.components.table.comparatorFor

// ---------------------------------------------------------------------------
// Pure, Compose-free logic for the Events (Event Explorer) screen: facet
// extraction, filtering and the summary-header stats. Fully unit-testable.
// ---------------------------------------------------------------------------

/** Distinct, sorted filter options derived from the loaded events. */
data class EventFacets(
    val events: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
)

fun distinctFacets(rows: List<EventRow>): EventFacets = EventFacets(
    events = rows.map { it.eventName }.filter { it.isNotBlank() }.distinct().sorted(),
    platforms = rows.mapNotNull { it.platform?.takeIf(String::isNotBlank) }.distinct().sorted(),
    countries = rows.mapNotNull { it.country?.takeIf(String::isNotBlank) }.distinct().sorted(),
)

/** Applies the selected facet filters. A null selection means "no constraint". */
fun applyEventFilters(
    rows: List<EventRow>,
    eventName: String? = null,
    platform: String? = null,
    country: String? = null,
): List<EventRow> = rows.filter { r ->
    (eventName == null || r.eventName == eventName) &&
        (platform == null || r.platform == platform) &&
        (country == null || r.country == country)
}

/** Summary stats for the header cards, derived from the rows actually shown. */
data class EventsSummary(
    val total: Int = 0,
    val offline: Int = 0,
    val distinctEvents: Int = 0,
    val topEvent: String? = null,
    val topCountry: String? = null,
)

/**
 * Sort selectors for the Events table, in the same column order as `eventColumns()` in
 * DashboardScreen: event, screen, duration, platform, device, country, language, session, time. Kept
 * here (Compose-free) so sorting can be applied to the FULL dataset before pagination, and unit-tested.
 * A null duration sorts as the smallest value (matches the column's `it.durationMs ?: -1L`).
 */
val eventSortSelectors: List<(EventRow) -> Comparable<*>?> = listOf(
    { it.eventName },
    { it.screen },
    { it.durationMs ?: -1L },
    { it.platform },
    { it.deviceClass },
    { it.country },
    { it.language },
    { it.sessionId },
    { it.ts },
)

/**
 * Sorts the full event list by the column at [columnIndex] (index into [eventSortSelectors] / the
 * table's columns). Out-of-range indices leave the list unchanged. Nulls sort last (see
 * [comparatorFor]). This runs BEFORE pagination so sorting spans every page, not just the visible one.
 */
fun sortEvents(rows: List<EventRow>, columnIndex: Int, ascending: Boolean): List<EventRow> {
    val selector = eventSortSelectors.getOrNull(columnIndex) ?: return rows
    return rows.sortedWith(comparatorFor(selector, ascending))
}

fun eventsSummary(rows: List<EventRow>): EventsSummary {
    if (rows.isEmpty()) return EventsSummary()
    val topEvent = rows.groupingBy { it.eventName }.eachCount()
        .maxByOrNull { it.value }?.key
    val topCountry = rows.mapNotNull { it.country?.takeIf(String::isNotBlank) }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    return EventsSummary(
        total = rows.size,
        offline = rows.count { it.wasOffline },
        distinctEvents = rows.map { it.eventName }.distinct().size,
        topEvent = topEvent,
        topCountry = topCountry,
    )
}
