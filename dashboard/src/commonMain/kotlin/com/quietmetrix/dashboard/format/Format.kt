package com.quietmetrix.dashboard.format

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Locale-independent formatting helpers for the dashboard. All functions are
 * pure so they can be unit tested on the JVM without a Compose host.
 */

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** 123456 -> "123,456". Handles negatives; locale-independent grouping. */
fun formatCount(value: Long): String {
    if (value == 0L) return "0"
    val negative = value < 0
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$grouped" else grouped
}

/** Seconds -> "45s" / "5m 30s" / "1h 5m". */
fun formatDuration(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val total = if (seconds < 0) 0 else seconds
    val hours = total / 3600
    val mins = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m ${secs}s"
}

/** Float -> "3.5" with the requested number of decimals (default 1). */
fun formatFloat(value: Float, decimals: Int = 1): String {
    val scale = 10f.pow(decimals.toFloat())
    val scaled = round(value * scale).toInt()
    val negative = scaled < 0
    val absScaled = abs(scaled)
    val scaleInt = scale.toInt()
    val intPart = absScaled / scaleInt
    var frac = (absScaled % scaleInt).toString()
    while (frac.length < decimals) frac = "0$frac"
    val sign = if (negative) "-" else ""
    return if (decimals == 0) "$sign$intPart" else "$sign$intPart.$frac"
}

/**
 * Chart x-axis label for a daily/hourly bucket key. Daily buckets ("2026-06-28")
 * are shown as-is; hourly buckets ("2026-06-28T14") collapse to "14:00".
 */
fun formatBucketLabel(raw: String, hourly: Boolean): String {
    if (!hourly) return raw
    val t = raw.indexOf('T')
    val hour = if (t >= 0) raw.substring(t + 1).take(2) else raw.takeLast(2)
    return "$hour:00"
}

/** ISO date prefix -> "Jan 15, 2024". Falls back to the raw date part on failure. */
fun formatDateOnly(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    if (parts.size != 3) return datePart
    val (y, m, d) = parts
    val monthName = m.toIntOrNull()?.let { idx ->
        if (idx in 1..12) MONTHS[idx - 1] else m
    } ?: m
    val dayNum = d.toIntOrNull()?.toString() ?: d
    return "$monthName $dayNum, $y"
}
