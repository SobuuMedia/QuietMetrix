package com.quietmetrix.server.funnels

import kotlinx.datetime.Instant

/**
 * One event row relevant to matching a funnel — already filtered by the caller to only events
 * whose name matches some step of the funnel being matched. [props] carries only the string
 * values needed for a step's exact-match prop filters (not the full event payload).
 */
data class FunnelEvent(
    val eventName: String,
    val ts: Instant,
    val screen: String? = null,
    val props: Map<String, String> = emptyMap(),
)

data class FunnelStepMatch(
    val stepKey: String,
    val ts: Instant,
)

/** [reached] is always non-empty when non-null and starts with the entry step. */
data class FunnelMatchResult(
    val reached: List<FunnelStepMatch>,
    val completed: Boolean,
)

/**
 * Matches one actor's events against a funnel definition. Strict order: the window starts at
 * entry (the first event matching step 0) and every subsequent step must occur, in order,
 * after the previous match and no later than `entry.ts + windowSeconds`. An actor's entry is
 * fixed at their FIRST step-0 match — a later, independent attempt at the funnel (e.g. the
 * first attempt expired) does not create a second entry or resurrect a dropped actor.
 *
 * Returns null if the actor never matched the entry step at all (not in the funnel).
 */
object FunnelMatcher {

    fun match(steps: List<FunnelStepDefinition>, windowSeconds: Long, events: List<FunnelEvent>): FunnelMatchResult? {
        if (steps.isEmpty()) return null
        val sorted = events.sortedBy { it.ts }

        val reached = mutableListOf<FunnelStepMatch>()
        var cursor = 0
        var deadline: Instant? = null
        var previousTs: Instant? = null

        for (step in steps) {
            val match = findNextMatch(sorted, cursor, step, deadline, previousTs) ?: break
            reached += FunnelStepMatch(step.key, match.ts)
            cursor = match.index + 1
            if (deadline == null) {
                deadline = Instant.fromEpochSeconds(match.ts.epochSeconds + windowSeconds, match.ts.nanosecondsOfSecond)
            }
            previousTs = match.ts
        }

        if (reached.isEmpty()) return null
        return FunnelMatchResult(reached, completed = reached.size == steps.size)
    }

    private data class Match(val index: Int, val ts: Instant)

    private fun findNextMatch(events: List<FunnelEvent>, fromIndex: Int, step: FunnelStepDefinition, deadline: Instant?, previousTs: Instant?): Match? {
        for (i in fromIndex until events.size) {
            val event = events[i]
            // Events are sorted by ts, so once we're past the deadline nothing further can match.
            if (deadline != null && event.ts > deadline) return null
            // Equal client timestamps have no causal ordering. Treating row insertion order
            // as a conversion let batched/offline events manufacture a funnel completion.
            if (previousTs != null && event.ts <= previousTs) continue
            if (matchesStep(event, step)) return Match(i, event.ts)
        }
        return null
    }

    private fun matchesStep(event: FunnelEvent, step: FunnelStepDefinition): Boolean {
        if (event.eventName != step.event) return false
        if (step.screen != null && event.screen != step.screen) return false
        // Both sides are already Map<String, String> here, so this is a plain string
        // comparison — mirrors php-hosting/src/funnelMatch.php's funnelPropToString()
        // comparison, which exists only because PHP's decoded event props are untyped.
        for ((key, value) in step.props) {
            if (event.props[key] != value) return false
        }
        return true
    }
}
