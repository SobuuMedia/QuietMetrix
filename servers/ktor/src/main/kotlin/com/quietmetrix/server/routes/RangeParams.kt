package com.quietmetrix.server.routes

/** Parses a dashboard `range` query param (`1h`, `1d`, `30d`, `90d`, default `7d`) into seconds. */
internal fun parseRangeSeconds(range: String?): Long = when (range) {
    "1h" -> 3_600L
    "1d" -> 86_400L
    "30d" -> 2_592_000L
    "90d" -> 7_776_000L
    else -> 604_800L // "7d" and the default
}
