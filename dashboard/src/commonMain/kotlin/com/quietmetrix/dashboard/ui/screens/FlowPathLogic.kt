package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.Transition

// ---------------------------------------------------------------------------
// Task 22: Flow breadcrumb — pure logic. See `FlowScreen.kt`'s KDoc for how this feeds the
// composables, and this file's own KDocs for the data-honesty findings behind the "platform
// filter" and the 👋 exit option.
//
// **Data-honesty investigation (this task's main event):** the design handoff
// (design_handoff_dashboard_redesign/README.md > Flow) describes a breadcrumb that extends up to
// 5 screens deep and a platform filter row that "shifts the exit-vs-continue ratio". The only
// real data this dashboard has for "where people go next" is `Transition`
// (api/Models.kt:170 — `fromScreen`/`toScreen`/`count`, fetched by `ApiClient.transitions`
// from `GET /projects/{id}/transitions`, server-side in
// servers/ktor/.../DashboardRoutes.kt:190-198). That endpoint aggregates the `screen_transition`
// counter into a flat list of (from, to, count) edges over the whole requested window — a
// single-hop graph, not recorded multi-step user paths. Two gaps follow from that:
//  1. **No platform dimension.** `TransitionItem`/`Transition` carry no platform field — the
//     `screen_transition` counter cells were never tagged with platform, so there is no honest
//     per-platform split to compute. (Telling sign: even the design prototype's own demo data
//     doesn't compute this from real per-platform data either — `QuietMetrix Prototype.dc.html`'s
//     `exitOff = { all: 0, android: 2, ios: -3, web: 6 }[s.flowPlat]` is a flat, hardcoded nudge
//     applied for the demo, not a real breakdown.) [filterTransitionsByPlatform] is therefore the
//     identity function for every platform — see its own KDoc — and `FlowScreen`'s platform pill
//     row renders the other 3 pills visibly (matching the screenshot) but disabled rather than
//     silently doing nothing when tapped.
//  2. **No real "people" simulation across hops.** The prototype narrates each breadcrumb column
//     as "After {screen} · {N} people" by multiplying a hardcoded per-start "people" count through
//     the path's percentages — invented demo arithmetic, not a thing the backend tracks (it has
//     no notion of a person following a path; only aggregate edge counts). This logic instead
//     reports each column's own real total outgoing transition count ([nextStepOptions]'
//     denominator) and never claims a cross-hop "people" figure.
//
// What IS honestly buildable: since `Transition` is a complete (from, to, count) edge list,
// repeatedly looking up "what are the real recorded next-screens from X" and letting the caller
// walk that lookup up to 5 times is a faithful use of the real data — it's an aggregate-graph
// walk (the same one a user re-picking "where do people go from here" at each step would do by
// hand), not a claim about any specific person's 5-screen journey. That's what
// [buildFlowBreadcrumb]/[extendFlowPath] below do.
// ---------------------------------------------------------------------------

/** A breadcrumb path may hold at most this many screens (design handoff README > Flow: "max 5 deep"). */
const val FLOW_PATH_MAX_DEPTH = 5

/**
 * Platform filter options for the Flow screen's filter row (design handoff README > Flow:
 * "🌈 All / 🤖 Android / 🍎 iOS / 🌐 Web"). See [filterTransitionsByPlatform]'s KDoc: only [All]
 * does anything real today — the backend has no platform dimension on transitions, so the other
 * 3 exist here purely so `FlowScreen` can render them (disabled) to match the design's pill row.
 */
enum class FlowPlatform(val emoji: String) {
    All("🌈"),
    Android("🤖"),
    Ios("🍎"),
    Web("🌐"),
}

/**
 * One destination option in a breadcrumb column: a real next screen ([screen] non-null,
 * clickable, extends the path) or the 👋 "left the app" exit ([screen] null, via [isExit] —
 * never clickable). [count] is the real transition count backing this option; [percent] (0..100)
 * is that count's share of its *own* screen's total outgoing transitions (see [nextStepOptions]),
 * not a share of some larger "people" figure no single-hop dataset can honestly produce.
 */
data class FlowStepOption(val screen: String?, val count: Long, val percent: Double) {
    val isExit: Boolean get() = screen == null
}

/** One column of the breadcrumb: the screen reached at this step, and its real [options] (see [buildFlowBreadcrumb]). */
data class FlowBreadcrumbColumn(val screen: String, val options: List<FlowStepOption>)

/**
 * Ranks "Start from" candidate screens by their total real outgoing transition volume
 * (descending), for the "Start from" pill row. A screen with no outgoing transitions in
 * [transitions] never appears — there would be nothing to show in its breadcrumb column anyway.
 */
fun topStartScreens(transitions: List<Transition>, limit: Int = 6): List<String> =
    transitions
        .filter { !it.fromScreen.isNullOrBlank() }
        .groupBy { it.fromScreen!! }
        .mapValues { (_, edges) -> edges.sumOf { it.count } }
        .entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }

/**
 * The real destination options recorded from [fromScreen] in [transitions], sorted by count
 * descending. [FlowStepOption.percent] is each option's share of [fromScreen]'s own total
 * outgoing count — the only honest denominator a single-hop edge list supports (see this file's
 * top-of-file KDoc for why no larger "visited this screen at all" or cross-hop "people" figure is
 * used). A `toScreen == null` edge (see [Transition]'s own nullable field) becomes the 👋 exit
 * option; empty when [fromScreen] has no outgoing transitions.
 */
fun nextStepOptions(transitions: List<Transition>, fromScreen: String): List<FlowStepOption> {
    val edges = transitions.filter { it.fromScreen == fromScreen }
    if (edges.isEmpty()) return emptyList()
    val total = edges.sumOf { it.count }.toDouble()
    if (total <= 0.0) return emptyList()
    return edges
        .sortedByDescending { it.count }
        .map { FlowStepOption(screen = it.toScreen, count = it.count, percent = it.count / total * 100.0) }
}

/** True while [path] can still grow by one more screen without exceeding [FLOW_PATH_MAX_DEPTH]. */
fun canExtendFlowPath(path: List<String>): Boolean = path.size < FLOW_PATH_MAX_DEPTH

/**
 * Appends [option]'s screen to [path], or returns null if it can't be extended: [option] is the
 * 👋 exit (never clickable — design handoff README > Flow), or [path] is already at
 * [FLOW_PATH_MAX_DEPTH].
 */
fun extendFlowPath(path: List<String>, option: FlowStepOption): List<String>? {
    val screen = option.screen ?: return null
    if (!canExtendFlowPath(path)) return null
    return path + screen
}

/**
 * Builds one [FlowBreadcrumbColumn] per screen in [path], each carrying that screen's own real
 * [nextStepOptions] from [transitions]. Empty when [path] is empty (nothing picked from "Start
 * from" yet).
 */
fun buildFlowBreadcrumb(path: List<String>, transitions: List<Transition>): List<FlowBreadcrumbColumn> =
    path.map { screen -> FlowBreadcrumbColumn(screen, nextStepOptions(transitions, screen)) }

/**
 * Reshapes [transitions] for [platform] (design handoff README > Flow: "Platform filter row ...
 * shifts the exit-vs-continue ratio"). **Data-honesty finding (see this file's top-of-file
 * KDoc):** `Transition` (api/Models.kt:170) and the server's `TransitionItem`
 * (servers/ktor/.../Requests.kt:167, populated at
 * servers/ktor/.../DashboardRoutes.kt:190-198) carry no platform dimension — the
 * `screen_transition` counter was never tagged with platform, so there is nothing real to split
 * on. This function is therefore the identity function for every [FlowPlatform], [All] included:
 * it returns [transitions] unchanged rather than fabricating a breakdown. `FlowScreen`'s platform
 * pill row reflects this honestly — only [FlowPlatform.All] is selectable; the other 3 render
 * (to match the design's screenshot) but disabled.
 */
fun filterTransitionsByPlatform(transitions: List<Transition>, platform: FlowPlatform): List<Transition> = transitions

// ---------------------------------------------------------------------------
// Task 23: Flow 3-card row (Countries/Platforms/Device types) — pure logic.
//
// **Data-honesty investigation.** `AggregatesResponse.countries`/`.platforms`/`.deviceClasses`
// (api/Models.kt:155-157) are all `List<BreakdownItem>`, the same shape Overview's "What they
// use"/"Where they are" cards already consume via [computeBreakdownRows] (`OverviewTopListsLogic.kt`).
// Checking what each one actually carries server-side (servers/ktor/.../DashboardRoutes.kt:106-150,
// mirrored in php-hosting/src/routes/dashboard.php):
//  - **Platforms is real.** `platforms = counterRepo.totalsByPlatform(id, "event", ...)` — the
//    `Counters` table's `platform` column (`persistence/Tables.kt`) is a real per-cell dimension
//    every event counter carries, so this is genuine aggregate data, not a placeholder.
//  - **Countries and device classes are NOT tracked, anywhere.** Both routes hard-code
//    `countries = emptyList()` / `device_classes = emptyList()` — the Ktor comment is explicit:
//    "countries/browsers/os/device_classes/languages are not tracked as counter dimensions yet, so
//    those fields are empty rather than silently faked". For countries specifically, the actual
//    chain (corrected after review — see task-23-report.md's "Fix: country-data finding" section)
//    is two separate gaps, not one: (1) the SDK never sends a country value in the first place —
//    `CounterFlusher.buildBatches` (quietmetrix-sdk/.../counters/CounterFlusher.kt) builds every
//    `CounterAppInfoDto(version = appVersion)` with no `country` argument, so it's always null on
//    the wire, even though `CounterIngestProcessor.process` (servers/ktor/.../CounterIngestProcessor.kt)
//    *does* write whatever it receives into the `Counters.country` column
//    (`country = request.app?.country?.uppercase()?.take(2).orEmpty()`) — ingest isn't the gap, the
//    SDK payload is. (There's a separate, unrelated `deviceCountry()` in the SDK
//    (quietmetrix-sdk/.../experiments/DeviceCountry.kt) that reads the device's locale region for
//    on-device experiment-country-eligibility checks — its own KDoc states it's "never transmitted
//    in any network payload"; it has nothing to do with counters.) (2) Separately, even though the
//    column exists and ingest writes to it, no dashboard route ever queries `Counters.country` to
//    build a response — `DashboardRoutes.kt`'s aggregates handler hard-codes `countries =
//    emptyList()` rather than reading it. Both gaps would need fixing for countries to ever be
//    real; today the column is always empty string "" in practice regardless.
//  - **"Top OS versions" has NO backing data at all, anywhere in the pipeline.** There is no
//    `os_version` counter dimension, column, or `dims` key in any metric (`event`, `screen_transition`,
//    `screen_dwell`, `session`, `value`, `friction`, `search`, `retention`, `activation`,
//    `funnel_step`) — unlike countries/device-classes, which at least have an emptied-out response
//    field reserved for them, OS version isn't modeled anywhere. [computePlatformStatTiles] below
//    therefore only builds the 3 stat tiles; the "top OS versions" sub-list the design handoff
//    README describes is honestly omitted — `FlowScreen.kt`'s Platforms card shows a plain caption
//    disclosing this instead of rendering an empty or fabricated list.
//
// Consequence for the 3 cards (`FlowScreen.kt`): Countries and Device types both render their real
// [computeBreakdownRows] output and fall back to the shared empty state when it's empty (true for
// every real deployment today, since the backend always returns `[]` for both) — the bar-list/
// progress-bar UI itself is still genuinely built against [BreakdownItem], ready for the day either
// field is populated. Platforms is the one card with real content today.
// ---------------------------------------------------------------------------

/**
 * The Flow "Platforms" card's 3 stat tiles (design handoff README > Flow: "Platforms (3 stat tiles
 * + top OS versions)") — the top 3 real, non-zero platforms by count, reusing
 * [computeBreakdownRows]'s ranking/ratio computation verbatim (same real `totalsByPlatform` data
 * Overview's "What they use" card already uses). Never pads to 3 — fewer than 3 real platforms
 * yields fewer tiles, same no-padding precedent as [computeWorthALookObservations]. See this file's
 * section KDoc above for why "top OS versions" has no equivalent function: there is no real data to
 * compute it from.
 */
fun computePlatformStatTiles(platforms: List<BreakdownItem>): List<BreakdownRow> =
    computeBreakdownRows(platforms).take(3)

/** Known device-class names (design handoff README > Flow: "Device types (phone/tablet/desktop)"),
 *  lowercased, matching whatever casing a future real `device_classes` breakdown might use. */
private val DEVICE_CLASS_EMOJI = mapOf(
    "phone" to "📱",
    "tablet" to "📟",
    "desktop" to "🖥️",
)

/**
 * Emoji glyph for one Device types progress-bar row (see this file's section KDoc: `deviceClasses`
 * is always empty today, but the row UI is built against the real [BreakdownItem] shape for when it
 * isn't). Matches [name] case-insensitively against the known phone/tablet/desktop classes the
 * design calls out; any other value (including [BreakdownItem.displayName]'s `"(unknown)"`
 * fallback) gets a generic glyph rather than a guessed one.
 */
fun deviceClassEmoji(name: String): String = DEVICE_CLASS_EMOJI[name.lowercase()] ?: "📦"
