package com.quietmetrix.server.routes

import com.quietmetrix.server.domain.DailySessionItem
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.Event
import com.quietmetrix.server.domain.RetentionCohort
import com.quietmetrix.server.domain.RetentionResponse
import com.quietmetrix.server.domain.SessionsResponse
import com.quietmetrix.server.domain.TransitionItem
import com.quietmetrix.server.domain.TransitionsResponse
import com.quietmetrix.server.persistence.EventRepository
import com.quietmetrix.server.ratelimit.RateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.Duration
import java.time.Instant

fun Routing.configureDashboardRoutes() {
    val eventRepo by inject<EventRepository>()
    val rateLimiter by inject<RateLimiter>()

    authenticate("auth-jwt") {
        route("/api/v1/projects/{projectId}/events") {
            get {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@get
                }
                if (!projectIdStr.startsWith("proj_")) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }

                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()
                if (!rateLimiter.tryConsume("dashboard:$userId")) {
                    call.response.header("Retry-After", "60")
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                    return@get
                }

                val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
                val offset = call.parameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val eventName = call.parameters["event"]
                val screen = call.parameters["screen"]
                val from = call.parameters["from"]?.let { Instant.parse(it) }
                val to = call.parameters["to"]?.let { Instant.parse(it) }

                val events = eventRepo.findByProjectId(id, limit, offset, eventName, screen, from, to)
                val total = eventRepo.countByProjectId(id)

                call.respond(mapOf(
                    "events" to events.map { eventToEventsResponseMap(it) },
                    "total" to total,
                    "limit" to limit,
                    "offset" to offset,
                ))
            }
        }

        route("/api/v1/projects/{projectId}/aggregates") {
            get {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@get
                }
                if (!projectIdStr.startsWith("proj_")) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }

                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()
                if (!rateLimiter.tryConsume("dashboard:$userId")) {
                    call.response.header("Retry-After", "60")
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                    return@get
                }

                val from = call.parameters["from"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Parameter 'from' is required"))
                    return@get
                }
                val to = call.parameters["to"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Parameter 'to' is required"))
                    return@get
                }
                val granularity = call.parameters["granularity"] ?: "day"

                val fromInstant = Instant.parse(from)
                val toInstant = Instant.parse(to)

                val rangeDays = Duration.between(fromInstant, toInstant).toDays()
                if (rangeDays > 90) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Aggregate date range cannot exceed 90 days"))
                    return@get
                }

                val topEvents = eventRepo.findTopEvents(id, fromInstant, toInstant, 10)
                val topScreens = eventRepo.findTopScreens(id, fromInstant, toInstant, 10)
                val screenDurations = eventRepo.findScreenDurations(id, fromInstant, toInstant, 10)
                val dailyTotals = eventRepo.findDailyTotals(id, fromInstant, toInstant)
                val countries = eventRepo.findCountriesBreakdown(id, fromInstant, toInstant)
                val browsers = eventRepo.findBrowsersBreakdown(id, fromInstant, toInstant)
                val osBreakdown = eventRepo.findOSBreakdown(id, fromInstant, toInstant)
                val platforms = eventRepo.findPlatformsBreakdown(id, fromInstant, toInstant)
                val deviceClasses = eventRepo.findDeviceClassBreakdown(id, fromInstant, toInstant)
                val languages = eventRepo.findLanguagesBreakdown(id, fromInstant, toInstant)
                val sessionStats = eventRepo.findSessionStats(id, fromInstant, toInstant)
                val offlineStats = eventRepo.findOfflineStats(id, fromInstant, toInstant)
                val totalEvents = eventRepo.countByProjectIdAndRange(id, fromInstant, toInstant)
                val errorCount = eventRepo.countErrorsByProjectIdAndRange(id, fromInstant, toInstant)

                call.respond(mapOf(
                    "project_id" to "proj_$id",
                    "from" to from,
                    "to" to to,
                    "granularity" to granularity,
                    "top_events" to topEvents,
                    "top_screens" to topScreens,
                    "screen_durations" to screenDurations,
                    "dau" to dailyTotals,
                    "event_counts" to dailyTotals,
                    "countries" to countries,
                    "browsers" to browsers,
                    "os" to osBreakdown,
                    "platforms" to platforms,
                    "device_classes" to deviceClasses,
                    "languages" to languages,
                    "session_stats" to sessionStats,
                    "offline_stats" to offlineStats,
                    "total_events" to totalEvents,
                    "errors" to errorCount,
                ))
            }
            }
        }

        route("/api/v1/projects/{projectId}/transitions") {
            get {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@get
                }
                if (!projectIdStr.startsWith("proj_")) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }

                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()
                if (!rateLimiter.tryConsume("dashboard:$userId")) {
                    call.response.header("Retry-After", "60")
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                    return@get
                }

                val days = call.parameters["days"]?.toIntOrNull()?.coerceIn(1, 90) ?: 30
                val now = Instant.now()
                val from = now.minusSeconds(days.toLong() * 86400)

                val transitions = eventRepo.findTransitions(id, from, now)
                call.respond(TransitionsResponse(
                    transitions.map { TransitionItem(
                        fromScreen = it["from_screen"] as String,
                        toScreen = it["to_screen"] as String,
                        count = it["count"] as Int
                    ) }
                ))
            }
        }

        route("/api/v1/projects/{projectId}/sessions") {
            get {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@get
                }
                if (!projectIdStr.startsWith("proj_")) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }

                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()
                if (!rateLimiter.tryConsume("dashboard:$userId")) {
                    call.response.header("Retry-After", "60")
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                    return@get
                }

                val days = call.parameters["days"]?.toIntOrNull()?.coerceIn(1, 90) ?: 30
                val now = Instant.now()
                val from = now.minusSeconds(days.toLong() * 86400)

                val dailySessions = eventRepo.findDailySessions(id, from, now)
                val daily = dailySessions.map {
                    DailySessionItem(day = it["day"] as String, total = it["total"] as Int)
                }

                val totalSessions = daily.sumOf { it.total }
                val avgEvents = if (totalSessions > 0) {
                    val totalEvents = eventRepo.countByProjectIdAndRange(id, from, now)
                    totalEvents.toDouble() / totalSessions
                } else 0.0

                // Approximate avg duration from session stats if available
                val sessionStats = eventRepo.findSessionStats(id, from, now)
                val avgDurationSec = (sessionStats["avg_duration_sec"] as? Int) ?: 0

                call.respond(SessionsResponse(
                    totalSessions = totalSessions,
                    avgEvents = Math.round(avgEvents * 10.0) / 10.0,
                    avgDurationSec = avgDurationSec,
                    dailySessions = daily,
                ))
            }
        }

        route("/api/v1/projects/{projectId}/retention") {
            get {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@get
                }
                if (!projectIdStr.startsWith("proj_")) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }

                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()
                if (!rateLimiter.tryConsume("dashboard:$userId")) {
                    call.response.header("Retry-After", "60")
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                    return@get
                }

                val days = call.parameters["days"]?.toIntOrNull()?.coerceIn(1, 90) ?: 30
                val now = Instant.now()
                val from = now.minusSeconds(days.toLong() * 86400)

                val cohorts = eventRepo.findRetention(id, from, now)
                call.respond(RetentionResponse(
                    cohorts.map {
                        RetentionCohort(
                            cohortDate = it["cohort_date"] as String,
                            size = it["size"] as Int,
                            day1 = it["day1"] as? Double,
                            day3 = it["day3"] as? Double,
                            day7 = it["day7"] as? Double,
                            day14 = it["day14"] as? Double,
                            day30 = it["day30"] as? Double,
                        )
                    }
                ))
            }
        }
}

/**
 * Maps a stored [Event] to the dashboard `/events` JSON shape. Keys mirror the PHP
 * backend's response so the dashboard's `EventRow` model deserializes identically
 * against either backend (numeric `id`, `event_name`, `session_id`).
 */
internal fun eventToEventsResponseMap(event: Event): Map<String, Any?> = mapOf(
    "id" to event.id?.toLongOrNull(),
    "project_id" to "proj_${event.projectId}",
    "event_name" to event.eventName,
    "screen" to event.screen,
    "props" to event.props?.let {
        try {
            kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), it)
        } catch (_: Exception) {
            it
        }
    },
    "session_id" to event.sid,
    "duration_ms" to event.durationMs,
    "ts" to event.ts.toString(),
    "was_offline" to event.wasOffline,
    "country" to event.country,
    "device_class" to event.deviceClass,
    "language" to event.language,
    "platform" to event.platform,
    "sdk_version" to event.sdkVersion,
    "received_at" to event.receivedAt?.toString(),
)