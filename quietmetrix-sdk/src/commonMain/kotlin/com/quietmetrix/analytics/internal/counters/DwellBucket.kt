package com.quietmetrix.analytics.internal.counters

/**
 * Coarsens a dwell time into one of a small, fixed set of bucket labels — the
 * `screen_dwell` metric's `bucket` dim (see `CounterRegistry.kt` / `counterRegistry.php`).
 * Buckets, not raw milliseconds, are what keep this metric's cardinality bounded: a handful
 * of buckets per screen, not one cell per distinct duration ever observed.
 */
internal fun dwellBucket(durationMs: Long): String = when {
    durationMs < 5_000L -> "0_5s"
    durationMs < 10_000L -> "5_10s"
    durationMs < 30_000L -> "10_30s"
    durationMs < 60_000L -> "30_60s"
    durationMs < 300_000L -> "60_300s"
    else -> "300s_plus"
}
