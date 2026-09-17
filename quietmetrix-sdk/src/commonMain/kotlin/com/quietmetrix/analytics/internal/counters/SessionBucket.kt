package com.quietmetrix.analytics.internal.counters

/**
 * Coarsens a session length into one of a small, fixed set of bucket labels — the `session`
 * metric's `bucket` dim (see `CounterRegistry.kt` / `counterRegistry.php`). A session tends to
 * run longer than a single screen dwell, so this top bucket is wider than [dwellBucket]'s.
 */
internal fun sessionBucket(durationMs: Long): String = when {
    durationMs < 10_000L -> "0_10s"
    durationMs < 30_000L -> "10_30s"
    durationMs < 60_000L -> "30_60s"
    durationMs < 300_000L -> "60_300s"
    durationMs < 900_000L -> "300_900s"
    else -> "900s_plus"
}
