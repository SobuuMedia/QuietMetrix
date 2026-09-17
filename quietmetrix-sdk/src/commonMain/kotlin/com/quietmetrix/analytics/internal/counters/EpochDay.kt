package com.quietmetrix.analytics.internal.counters

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * ISO `yyyy-MM-dd` for [now], UTC. `kotlin.time.Instant` carries no calendar/timezone support
 * (that's what kotlinx-datetime exists for, and this SDK avoids it — see the project's own
 * migration note to `kotlin.time.Clock`), so this computes the calendar date directly from the
 * epoch using Howard Hinnant's `civil_from_days` algorithm rather than pulling in a date
 * library for one field.
 *
 * Known simplification: this is the UTC calendar day, not the device's local-timezone day. A
 * session near local midnight may land in the "wrong" day bucket relative to the device's own
 * clock. This doesn't affect k-anonymity or the wire contract — only cohort-day precision —
 * and true device-local cohorting is left for a follow-up.
 */
@OptIn(ExperimentalTime::class)
internal fun utcTodayIso(now: Instant = Clock.System.now()): String {
    val epochDay = now.toEpochMilliseconds().floorDiv(86_400_000L)
    val (year, month, day) = civilDateFromEpochDay(epochDay)
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

/**
 * Days-since-1970-01-01 -> (year, month, day). Howard Hinnant's `civil_from_days`
 * (http://howardhinnant.github.io/date_algorithms.html#civil_from_days), a well-known
 * dependency-free proleptic-Gregorian conversion valid for the entire Long range.
 */
internal fun civilDateFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719468
    val era = z.floorDiv(146097L)
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    return Triple(year.toInt(), m.toInt(), d.toInt())
}

/**
 * (year, month, day) -> days-since-1970-01-01. Howard Hinnant's `days_from_civil`
 * (http://howardhinnant.github.io/date_algorithms.html#days_from_civil) — the exact inverse
 * of [civilDateFromEpochDay], used to turn a persisted ISO date (e.g. a device's first-launch
 * day) back into an epoch day for arithmetic like "days since first launch".
 */
internal fun epochDayFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

internal fun epochDayFromIso(iso: String): Long {
    val parts = iso.split("-")
    return epochDayFromCivil(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

/**
 * A simple 7-day-bucket week label for [epochDay], e.g. `"2026-W36"` — NOT true ISO-8601
 * week numbering (which anchors to Monday and can shift a date's week-year at year
 * boundaries); just `((day-of-year - 1) / 7) + 1`, which is enough for a stable retention
 * cohort label without pulling in real ISO week arithmetic for one field.
 */
internal fun weekCohort(epochDay: Long): String {
    val (year, month, day) = civilDateFromEpochDay(epochDay)
    val dayOfYear = dayOfYear(year, month, day)
    val week = (dayOfYear - 1) / 7 + 1
    return "$year-W${week.toString().padStart(2, '0')}"
}

private val CUMULATIVE_DAYS_BEFORE_MONTH = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun dayOfYear(year: Int, month: Int, day: Int): Int {
    var doy = CUMULATIVE_DAYS_BEFORE_MONTH[month - 1] + day
    if (month > 2 && isLeapYear(year)) doy += 1
    return doy
}
