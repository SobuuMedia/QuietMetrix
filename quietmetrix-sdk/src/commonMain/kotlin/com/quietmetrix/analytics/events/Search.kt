package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.counters.recordSearchCounter

/**
 * Reports that a search ran on [screen] (defaulting to the current screen reported via
 * [trackScreen], if any) and returned [resultCount] results.
 *
 * The search query itself is never sent — free-form text is often sensitive and would blow up
 * counter cardinality — only *whether* it returned nothing. This tells you what fraction of
 * searches on a screen return zero results, which is actionable on its own (poor indexing, a
 * common typo, a missing synonym) even without knowing what anyone actually typed.
 */
suspend fun trackSearch(screen: String? = null, resultCount: Int) {
    if (!Gate.shouldTrack()) return
    recordSearchCounter(screen ?: ScreenTracker.currentScreenName(), resultCount)
}
