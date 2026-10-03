package com.quietmetrix.server.routes

/** Parses a dashboard `range` query param (`1h`, `1d`, `30d`, `90d`, default `7d`) into seconds. */
internal fun parseRangeSeconds(range: String?): Long = when (range) {
    "1h" -> 3_600L
    "1d" -> 86_400L
    "30d" -> 2_592_000L
    "90d" -> 7_776_000L
    else -> 604_800L // "7d" and the default
}

/** Resolves a dashboard range token, retaining the legacy days parameter as a fallback. */
internal fun parseRangeWindowSeconds(range: String?, days: String?, maxDays: Int = 90): Long {
    val maxSeconds = maxDays.coerceAtLeast(1).toLong() * 86_400L
    if (range != null) return parseRangeSeconds(range).coerceAtMost(maxSeconds)
    val legacyDays = days?.toIntOrNull()?.coerceIn(1, maxDays.coerceAtLeast(1)) ?: 30
    return legacyDays * 86_400L
}
