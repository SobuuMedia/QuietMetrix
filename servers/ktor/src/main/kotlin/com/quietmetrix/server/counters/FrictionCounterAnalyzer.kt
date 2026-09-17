package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell

data class FrictionScreenResult(val screen: String, val rageTaps: Long)

/**
 * Sums `friction{screen, kind}` cells' `n` per screen — an absolute count, like `top_events`/
 * `top_screens`, not a per-view rate (a screen with more traffic naturally has more rage taps;
 * normalizing against `screen_transition` volume is a reasonable future refinement, not done
 * in this first version). `kind` is always `"rage_tap"` today, but summed regardless of value
 * so a future `dead_tap`/`error_state` doesn't silently disappear from this total. Mirrors
 * php-hosting's `frictionCounterAnalyze()` — keep the two in sync.
 */
object FrictionCounterAnalyzer {

    fun analyze(cells: List<CounterCell>): List<FrictionScreenResult> =
        cells.groupBy { it.dims["screen"] }
            .mapNotNull { (screen, group) -> screen?.let { FrictionScreenResult(it, group.sumOf { c -> c.n }) } }
            .sortedByDescending { it.rageTaps }
}
