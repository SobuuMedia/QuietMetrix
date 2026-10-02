package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.SessionsResponse

// ---------------------------------------------------------------------------
// Task 21: 3-card row (screens/platforms/countries) + "Worth a look" + key card — pure logic.
// See OverviewScreen.kt's ScreensPlatformsCountriesSection/WorthALookSection/KeyCard KDoc for how
// each of these feeds the composables, and WorthALookObservation's KDoc for the data-honesty
// investigation behind computeWorthALookObservations.
// ---------------------------------------------------------------------------

/**
 * One ranked row in the "📍 Where people hang out" card: a real screen name, its visit count, its
 * average dwell time (0 when no [com.quietmetrix.dashboard.api.ScreenDuration] entry matches), and
 * [barFraction] — this row's visit count relative to the list's own top row's (always `1.0` for
 * the top row), the same "bar relative to the list's own max" scale the design handoff's country
 * list literally uses (`QuietMetrix Prototype.dc.html`'s `countriesBase` bars:
 * `width: Math.round(p / 41 * 100) + '%'`, 41 being that list's own max share) — reused for all 3
 * cards here rather than inventing a per-card scale.
 */
data class ScreenUsageRow(val screen: String, val visits: Long, val avgDwellMs: Long, val barFraction: Double)

/**
 * Builds the "📍 Where people hang out" ranked list from [AggregatesResponse.topScreens] (rank +
 * visit count, already server-ranked) joined with [AggregatesResponse.screenDurations] (average
 * dwell time) by screen name — both real, already-fetched fields; no synthetic combination of
 * data that wasn't already there. Screens with a null/blank name are dropped: there's nothing
 * useful to rank, filter by, or show a dwell time for on an unlabeled screen.
 *
 * Returns the rows in [AggregatesResponse.topScreens]' own order (already rank-sorted by the
 * server) rather than re-sorting — [barFraction] is still computed relative to the actual max
 * visit count in the (filtered) list, so it stays correct even if a caller's fixture isn't
 * pre-sorted.
 */
fun computeScreenUsage(agg: AggregatesResponse?): List<ScreenUsageRow> {
    val screens = agg?.topScreens.orEmpty().filter { !it.screen.isNullOrBlank() }
    if (screens.isEmpty()) return emptyList()
    val durationByScreen = agg?.screenDurations.orEmpty().associateBy { it.screen }
    val maxVisits = screens.maxOf { it.count }.toDouble().coerceAtLeast(1.0)
    return screens.map { s ->
        ScreenUsageRow(
            screen = s.screen!!,
            visits = s.count,
            avgDwellMs = durationByScreen[s.screen]?.avgMs ?: 0L,
            barFraction = s.count / maxVisits,
        )
    }
}

/**
 * One ranked row in the "📱 What they use"/"🌍 Where they are" cards: a real
 * [BreakdownItem.displayName] + count, [barFraction] relative to the list's own top row (see
 * [ScreenUsageRow]'s KDoc for why this scale, not share-of-total, drives the inline bar), and
 * [shareOfTotal] (0..1, relative to the sum of all non-zero rows) for the row's "% of traffic"
 * label.
 */
data class BreakdownRow(val name: String, val count: Long, val barFraction: Double, val shareOfTotal: Double)

/**
 * Builds a ranked, zero-filtered breakdown list (platforms or countries) from a real
 * [BreakdownItem] list ([AggregatesResponse.platforms]/[AggregatesResponse.countries], already
 * fetched) — ranking + the two display fractions are the only computation added; no new data.
 */
fun computeBreakdownRows(items: List<BreakdownItem>): List<BreakdownRow> {
    val nonZero = items.filter { it.count > 0 }.sortedByDescending { it.count }
    if (nonZero.isEmpty()) return emptyList()
    val total = nonZero.sumOf { it.count }.toDouble()
    val maxCount = nonZero.first().count.toDouble()
    return nonZero.map { BreakdownRow(it.displayName, it.count, it.count / maxCount, it.count / total) }
}

/**
 * One "Worth a look" observation.
 *
 * **Data-honesty investigation (this task's main event):** the design handoff describes "👀 Worth
 * a look" as "3 auto-generated observations" (design_handoff_dashboard_redesign/README.md >
 * Overview), and the prototype's own demo data (`QuietMetrix Prototype.dc.html`'s `worthAll`)
 * shows exactly what that phrase means in practice: an offline-sync note, an A/B-experiment
 * "winning" claim, and a "people give up on Search fast" drop-off claim — all 3 hardcoded, none
 * computed from the prototype's own mock state. Checked whether any of the 3 is honestly
 * reproducible from REAL data already fetched by this dashboard:
 * - The A/B-experiment claim needs a real experiment comparison (z-score/p-value, [kind of
 *   significance test `ExperimentComparisonDto`](com.quietmetrix.dashboard.api.ExperimentComparisonDto)
 *   already computes server-side) tied to a SPECIFIC experiment the caller would have to pick —
 *   doable in principle, but Experiments is its own screen (not yet visited by a page that's just
 *   rendering Overview) and picking "the" experiment to surface here is itself a judgment call
 *   this task has no honest rule for (which one? the newest? Overview doesn't know). Not built.
 * - The Search-drop-off claim needs a per-screen abandonment/bounce rate. `SearchResponse`/
 *   `FrictionResponse` exist but are Flow-screen data this Overview screen doesn't load, and even
 *   there, "gives up fast" is an interpretive judgment ("fast" compared to what baseline?), not a
 *   plain fact read off one field. Not built.
 * - Trend-style claims in general ("X changed", "Y is up") need a previous-period comparison —
 *   the exact gap Tasks 18-20 already found and refused to fabricate for the hero/vitals/chart.
 *
 * Conclusion: there is no honest way to reproduce the design handoff's literal 3 observations
 * without either (a) real backend-side anomaly/trend detection this project doesn't have, or (b)
 * an LLM call, which this task is explicitly not meant to build. Rather than fabricate 3
 * plausible-looking insights (the Task 18 precedent this task's brief names directly), each
 * variant below is instead a single PLAIN FACT read straight off data this screen has already
 * fetched, with zero interpretation or trend-claiming:
 * - [OfflineSyncObservation]: a literal count ([AggregatesResponse.totals]`.offline`) — the one
 *   piece of the prototype's offline-sync observation that IS real data, kept; the "✈️ offline"
 *   framing is reused, the trend-free literal number is not fabricated.
 *   this dashboard tracks.
 * - [TopScreenObservation]: the real top row of [computeScreenUsage] — literally the same fact
 *   the 3-card row's own first card shows, surfaced here too as a plain highlight (this task's
 *   brief's own example of an honest alternative: "Screen X is now your most-used").
 * - [DominantPlatformObservation]: a real majority share (>= 50%) of [computeBreakdownRows]
 *   applied to [AggregatesResponse.platforms] — a plain "most of your traffic" fact, not a change
 *   claim.
 *
 * [computeWorthALookObservations] emits an observation only when its real condition actually
 * holds — never padded to reach 3 just because the design shows 3 — and shows the honest
 * "🎈 All caught up!" empty placeholder (`WorthALookSection`) when none do.
 */
sealed interface WorthALookObservation {
    val id: String
}

/** See [WorthALookObservation]'s KDoc. [count] is [AggregatesResponse.totals]`.offline`, verbatim. */
data class OfflineSyncObservation(val count: Long) : WorthALookObservation {
    override val id: String = "offline_sync"
}

/** See [WorthALookObservation]'s KDoc. [screen]/[visits] are the real top row of [computeScreenUsage]. */
data class TopScreenObservation(val screen: String, val visits: Long) : WorthALookObservation {
    override val id: String = "top_screen"
}

/** See [WorthALookObservation]'s KDoc. [share] (0..1) is >= [DOMINANT_PLATFORM_THRESHOLD]. */
data class DominantPlatformObservation(val platform: String, val share: Double) : WorthALookObservation {
    override val id: String = "dominant_platform"
}

/** The minimum real share of total platform traffic a single platform must hold to be called
 *  "dominant" — see [DominantPlatformObservation]'s KDoc. A plain majority threshold, not a
 *  statistically-derived one; there is no judgment call finer than "more than half" being made. */
private const val DOMINANT_PLATFORM_THRESHOLD = 0.5

/**
 * See [WorthALookObservation]'s KDoc for the full data-honesty investigation. Returns at most 3
 * observations, each present only when its underlying real condition holds, in a fixed order
 * (offline sync, top screen, dominant platform) so the list doesn't reorder itself between
 * renders of the same data.
 */
fun computeWorthALookObservations(agg: AggregatesResponse?): List<WorthALookObservation> {
    if (agg == null) return emptyList()
    val observations = mutableListOf<WorthALookObservation>()

    val offline = agg.totals.offline
    if (offline > 0) observations += OfflineSyncObservation(offline)

    val topScreen = computeScreenUsage(agg).maxByOrNull { it.visits }
    if (topScreen != null) observations += TopScreenObservation(topScreen.screen, topScreen.visits)

    val topPlatform = computeBreakdownRows(agg.platforms).firstOrNull()
    if (topPlatform != null && topPlatform.shareOfTotal >= DOMINANT_PLATFORM_THRESHOLD) {
        observations += DominantPlatformObservation(topPlatform.name, topPlatform.shareOfTotal)
    }

    return observations
}

/**
 * True when there is no real session/event data at all yet — the same "nothing to show" signal
 * [OverviewHero]'s own `isEmpty` check uses (`OverviewScreen.kt`), reused here so the 3-card row/
 * "Worth a look"/key card region agrees with the hero on when to swap to the first-run
 * setup-instructions treatment ([SetupInstructionsSection]).
 */
fun isOverviewEmpty(agg: AggregatesResponse?, sess: SessionsResponse?): Boolean =
    (sess?.totalSessions ?: 0) == 0 && (agg?.totals?.events ?: 0L) == 0L
