package com.quietmetrix.analytics.internal.counters

/**
 * The counter-native backing for [com.quietmetrix.analytics.trackSearch]. Search query text is
 * never sent — it's free-form, often sensitive, and would blow up cell cardinality — so this
 * records only a per-screen rate: `search{screen}` (every search) and `search_zero_result
 * {screen}` (only when [resultCount] is `0`). A dashboard reading both can show *what fraction*
 * of searches on a screen return nothing, without ever seeing *what* anyone searched for.
 */
internal suspend fun recordSearchCounter(
    screen: String?,
    resultCount: Int,
    debug: Boolean = com.quietmetrix.analytics.internal.ConfigHolder.configOrNull?.debug == true,
) {
    if (resultCount < 0) {
        if (debug) println("[QuietMetrix] trackSearch rejected: resultCount must not be negative (was $resultCount)")
        return
    }
    val dims = mapOf("screen" to (screen ?: "unknown"))
    MetricGateway.record("search", dims)
    if (resultCount == 0) {
        MetricGateway.record("search_zero_result", dims)
    }
}
