package com.quietmetrix.server.routes

import com.quietmetrix.server.domain.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Inclusive counter dates, with an exclusive instant upper bound for response metadata. */
internal data class AnalyticsWindow(val fromDay: LocalDate, val toDay: LocalDate, val days: Long) {
    val fromInstant: Instant get() = fromDay.atStartOfDay(ZoneOffset.UTC).toInstant()
    val toInstant: Instant get() = toDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
}

/** Resolves equal, adjacent windows at the daily resolution of persisted counters. */
internal fun analyticsWindow(seconds: Long, period: String?, anchor: String?, now: Instant = Instant.now()): AnalyticsWindow {
    require(period == null || period == "current" || period == "previous") { "period must be current or previous" }
    require(seconds in 1..31_536_000L) { "Unsupported window length" }
    val today = now.atZone(ZoneOffset.UTC).toLocalDate()
    val end = if (anchor == null) today else runCatching { LocalDate.parse(anchor) }
        .getOrElse { throw IllegalArgumentException("anchor must be a UTC date") }
    require(end <= today && end >= today.minusDays(365)) { "anchor must be within the past year" }
    val days = ((seconds + 86_399) / 86_400).coerceAtLeast(1)
    val toDay = if (period == "previous") end.minusDays(days) else end
    return AnalyticsWindow(toDay.minusDays(days - 1), toDay, days)
}

/** Validates the optional period/anchor contract before an analytics route reads counters. */
internal suspend fun ApplicationCall.requestAnalyticsWindow(seconds: Long): AnalyticsWindow? {
    return try {
        analyticsWindow(seconds, request.queryParameters["period"], request.queryParameters["anchor"])
    } catch (error: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", error.message ?: "Invalid analytics window"))
        null
    }
}

/** SDK week labels are seven-day bins from January 1, rather than ISO calendar weeks. */
internal fun reportedCohortStart(label: String): LocalDate? = runCatching {
    val match = Regex("(\\d{4})-W(\\d{2})").matchEntire(label)
    if (match == null) LocalDate.parse(label) else {
        val year = match.groupValues[1].toInt()
        val week = match.groupValues[2].toInt()
        require(week in 1..53)
        LocalDate.of(year, 1, 1).plusDays((week - 1) * 7L)
    }
}.getOrNull()
