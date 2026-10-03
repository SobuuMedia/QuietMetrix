package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.counters.ActivationCounterAnalyzer
import com.quietmetrix.server.counters.ActivationCounterCell
import com.quietmetrix.server.counters.OverviewCounterAnalyzer
import com.quietmetrix.server.counters.FrictionCounterAnalyzer
import com.quietmetrix.server.counters.SearchCounterAnalyzer
import com.quietmetrix.server.counters.SessionCounterAnalyzer
import com.quietmetrix.server.counters.ValueCounterAnalyzer
import com.quietmetrix.server.domain.AggregatesResponse
import com.quietmetrix.server.domain.AggregatesTotals
import com.quietmetrix.server.domain.BreakdownItem
import com.quietmetrix.server.domain.DailyPointItem
import com.quietmetrix.server.domain.DailySessionItem
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.ScreenDurationItem
import com.quietmetrix.server.domain.TopEventItem
import com.quietmetrix.server.domain.TopScreenItem
import com.quietmetrix.server.domain.TopValueItem
import com.quietmetrix.server.domain.RetentionCohort
import com.quietmetrix.server.domain.RetentionResponse
import com.quietmetrix.server.domain.SessionsResponse
import com.quietmetrix.server.domain.FrictionResponse
import com.quietmetrix.server.domain.FrictionScreenItem
import com.quietmetrix.server.domain.SearchResponse
import com.quietmetrix.server.domain.SearchScreenItem
import com.quietmetrix.server.domain.TransitionItem
import com.quietmetrix.server.domain.TransitionsResponse
import com.quietmetrix.server.retention.RetentionCounterAnalyzer
import com.quietmetrix.server.retention.RetentionCounterCell
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.ratelimit.RateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.Instant

/**
 * All routes here accept either a dashboard session JWT or a `qm_pat_…` personal access
 * token carrying the `analytics:read` scope — see docs/agents/setup.md. Deliberately NOT
 * inside `authenticate("auth-jwt")`: that provider's JWT verifier would reject a PAT before
 * these handlers ever run, so each resolves its principal via [resolveApiPrincipal] instead.
 */
fun Routing.configureDashboardRoutes() {
    val projectRepo by inject<com.quietmetrix.server.persistence.ProjectRepository>()
    val memberRepo by inject<com.quietmetrix.server.persistence.ProjectMemberRepository>()
    val counterRepo by inject<CounterRepository>()
    val rateLimiter by inject<RateLimiter>()
    val accessTokenRepo by inject<AccessTokenRepository>()
    val config by inject<AppConfig>()

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

            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid Authorization header"))
                return@get
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics"))
                return@get
            }
            val project = projectRepo.findById(id) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                return@get
            }
            val owner = (project["ownerUserId"] as? Number)?.toLong() == principal.userId
            if (!principal.isAdmin() && !owner && memberRepo.findMembership(id, principal.userId) == null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project"))
                return@get
            }
            val userId = principal.userId.toString()
            if (!rateLimiter.tryConsume("dashboard:$userId")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                return@get
            }

            // Either explicit `from`/`to` (ISO-8601 instants), or a `range` token
            // (1h|1d|7d|30d|90d, default 7d) like every other analytics endpoint — the
            // dashboard client only ever sends `range`, so `from`/`to` alone must not be
            // required. Explicit `from`/`to` still wins when both are given.
            val window = if (call.parameters["from"] == null && call.parameters["to"] == null) {
                call.requestAnalyticsWindow(parseRangeSeconds(call.parameters["range"])) ?: return@get
            } else {
                val explicit = runCatching {
                    require(call.parameters["period"] == null && call.parameters["anchor"] == null) { "Explicit dates cannot be combined with period/anchor" }
                    val to = call.parameters["to"]?.let { Instant.parse(it) } ?: Instant.now()
                    val from = call.parameters["from"]?.let { Instant.parse(it) } ?: to.minusSeconds(parseRangeSeconds(call.parameters["range"]))
                    require(from <= to) { "from must precede to" }
                    val first = from.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    val last = to.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    val days = java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1
                    require(days in 1..90) { "Aggregate date range cannot exceed 90 UTC dates" }
                    AnalyticsWindow(first, last, days)
                }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid aggregate dates"))
                    return@get
                }
                explicit
            }
            val rangeDays = window.days
            val fromDay = window.fromDay
            val toDay = window.toDay

            // "Ship what maps cleanly": top_events, screen_durations (bucket-approximated),
            // platforms, and daily totals all read from counters. top_screens is approximated
            // from screen_transition (no standalone "screen viewed" counter exists).
            // countries/browsers/os/device_classes/languages are not tracked as counter
            // dimensions yet, so those fields are empty rather than silently faked; offline is
            // always 0 for the same reason. See OverviewCounterAnalyzer's doc comment.
            val eventCells = counterRepo.readCells(id, "event", fromDay, toDay)
            val topEvents = eventCells.sortedByDescending { it.n }.take(10)
                .map { TopEventItem(eventName = it.dims["name"], count = it.n) }

            val transitionCells = counterRepo.readCells(id, "screen_transition", fromDay, toDay)
            val topScreens = OverviewCounterAnalyzer.topScreens(transitionCells, limit = 10)
                .map { (screen, count) -> TopScreenItem(screen = screen, count = count) }

            val dwellCells = counterRepo.readCells(id, "screen_dwell", fromDay, toDay)
            val screenDurations = OverviewCounterAnalyzer.screenDurations(dwellCells).take(10)
                .map { ScreenDurationItem(screen = it.screen, count = it.count, avgMs = it.avgMs, totalMs = it.totalMs) }

            val dailyTotals = counterRepo.dailyTotals(id, "event", fromDay, toDay)
                .map { DailyPointItem(day = it.day.toString(), total = it.n) }

            val platforms = counterRepo.totalsByPlatform(id, "event", fromDay, toDay)
                .map { BreakdownItem(name = it.platform, count = it.n) }

            val valueCells = counterRepo.readCells(id, "value", fromDay, toDay)
            val topValues = ValueCounterAnalyzer.analyze(valueCells).take(10)
                .map { TopValueItem(name = it.name, totalMinorUnits = it.totalMinorUnits) }

            val totalEvents = eventCells.sumOf { it.n }
            val errorCount = eventCells
                .filter { it.dims["name"]?.lowercase() in setOf("error", "crash", "exception") }
                .sumOf { it.n }

            call.respond(
                AggregatesResponse(
                    windowDays = rangeDays.toInt().coerceAtLeast(1),
                    totals = AggregatesTotals(events = totalEvents, offline = 0, errors = errorCount),
                    topEvents = topEvents,
                    topScreens = topScreens,
                    screenDurations = screenDurations,
                    topValues = topValues,
                    daily = dailyTotals,
                    countries = emptyList(),
                    platforms = platforms,
                    deviceClasses = emptyList(),
                ),
            )
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

            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid Authorization header"))
                return@get
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics"))
                return@get
            }
            val project = projectRepo.findById(id) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                return@get
            }
            val owner = (project["ownerUserId"] as? Number)?.toLong() == principal.userId
            if (!principal.isAdmin() && !owner && memberRepo.findMembership(id, principal.userId) == null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project"))
                return@get
            }
            val userId = principal.userId.toString()
            if (!rateLimiter.tryConsume("dashboard:$userId")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                return@get
            }

            val windowSeconds = parseRangeWindowSeconds(call.parameters["range"], call.parameters["days"])
            val window = call.requestAnalyticsWindow(windowSeconds) ?: return@get
            val fromDay = window.fromDay
            val toDay = window.toDay

            val transitions = counterRepo.readCells(id, "screen_transition", fromDay, toDay)
                .mapNotNull { cell ->
                    val from = cell.dims["from"] ?: return@mapNotNull null
                    val to = cell.dims["to"] ?: return@mapNotNull null
                    TransitionItem(fromScreen = from, toScreen = to, count = cell.n.toInt())
                }
                .sortedByDescending { it.count }
                .take(50)
            call.respond(TransitionsResponse(transitions))
        }
    }

    route("/api/v1/projects/{projectId}/search") {
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

            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid Authorization header"))
                return@get
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics"))
                return@get
            }
            val project = projectRepo.findById(id) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                return@get
            }
            val owner = (project["ownerUserId"] as? Number)?.toLong() == principal.userId
            if (!principal.isAdmin() && !owner && memberRepo.findMembership(id, principal.userId) == null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project"))
                return@get
            }
            val userId = principal.userId.toString()
            if (!rateLimiter.tryConsume("dashboard:$userId")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                return@get
            }

            val windowSeconds = parseRangeWindowSeconds(call.parameters["range"], call.parameters["days"])
            val window = call.requestAnalyticsWindow(windowSeconds) ?: return@get
            val fromDay = window.fromDay
            val toDay = window.toDay

            val searchCells = counterRepo.readCells(id, "search", fromDay, toDay)
            val zeroResultCells = counterRepo.readCells(id, "search_zero_result", fromDay, toDay)
            val screens = SearchCounterAnalyzer.analyze(searchCells, zeroResultCells)
                .map { SearchScreenItem(screen = it.screen, total = it.total.toInt(), zeroResult = it.zeroResult.toInt(), rate = it.rate) }
            call.respond(SearchResponse(screens))
        }
    }

    route("/api/v1/projects/{projectId}/friction") {
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

            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid Authorization header"))
                return@get
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics"))
                return@get
            }
            val project = projectRepo.findById(id) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                return@get
            }
            val owner = (project["ownerUserId"] as? Number)?.toLong() == principal.userId
            if (!principal.isAdmin() && !owner && memberRepo.findMembership(id, principal.userId) == null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project"))
                return@get
            }
            val userId = principal.userId.toString()
            if (!rateLimiter.tryConsume("dashboard:$userId")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                return@get
            }

            val windowSeconds = parseRangeWindowSeconds(call.parameters["range"], call.parameters["days"])
            val window = call.requestAnalyticsWindow(windowSeconds) ?: return@get
            val fromDay = window.fromDay
            val toDay = window.toDay

            val frictionCells = counterRepo.readCells(id, "friction", fromDay, toDay)
            val screens = FrictionCounterAnalyzer.analyze(frictionCells)
                .map { FrictionScreenItem(screen = it.screen, rageTaps = it.rageTaps.toInt()) }
            call.respond(FrictionResponse(screens))
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

            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid Authorization header"))
                return@get
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics"))
                return@get
            }
            val project = projectRepo.findById(id) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                return@get
            }
            val owner = (project["ownerUserId"] as? Number)?.toLong() == principal.userId
            if (!principal.isAdmin() && !owner && memberRepo.findMembership(id, principal.userId) == null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project"))
                return@get
            }
            val userId = principal.userId.toString()
            if (!rateLimiter.tryConsume("dashboard:$userId")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                return@get
            }

            val windowSeconds = parseRangeWindowSeconds(call.parameters["range"], call.parameters["days"])
            val window = call.requestAnalyticsWindow(windowSeconds) ?: return@get
            val fromDay = window.fromDay
            val toDay = window.toDay

            // `session{bucket}` cells (see the SDK's SessionTracker) carry no per-session
            // duration, so avgDurationSec is a bucket-midpoint approximation — see
            // SessionCounterAnalyzer. There is no counter tying an event count to a session, so
            // unlike the pre-counters version this response has no avg_events field at all.
            val cells = counterRepo.readCells(id, "session", fromDay, toDay)
            val analysis = SessionCounterAnalyzer.analyze(cells)
            val daily = counterRepo.dailyTotals(id, "session", fromDay, toDay)
                .map { DailySessionItem(day = it.day.toString(), total = it.n.toInt()) }

            call.respond(SessionsResponse(
                totalSessions = analysis.totalSessions.toInt(),
                avgDurationSec = analysis.avgDurationSec,
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

            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid Authorization header"))
                return@get
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics"))
                return@get
            }
            val project = projectRepo.findById(id) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                return@get
            }
            val owner = (project["ownerUserId"] as? Number)?.toLong() == principal.userId
            if (!principal.isAdmin() && !owner && memberRepo.findMembership(id, principal.userId) == null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project"))
                return@get
            }
            val userId = principal.userId.toString()
            if (!rateLimiter.tryConsume("dashboard:$userId")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                return@get
            }

            val windowSeconds = parseRangeWindowSeconds(call.parameters["range"], call.parameters["days"])
            val window = call.requestAnalyticsWindow(windowSeconds) ?: return@get
            val fromDay = window.fromDay
            val toDay = window.toDay

            val cells = counterRepo.readCells(id, "retention", fromDay, toDay)
                .mapNotNull { cell ->
                    val cohort = cell.dims["cohort"] ?: return@mapNotNull null
                    val started = reportedCohortStart(cohort) ?: return@mapNotNull null
                    if (started < fromDay || started > toDay) return@mapNotNull null
                    val day = cell.dims["day"]?.toIntOrNull() ?: return@mapNotNull null
                    RetentionCounterCell(cohort, day, cell.n)
                }
            val cohorts = RetentionCounterAnalyzer.analyze(cells)

            // Null (not 0.0) means "this project hasn't configured activationEvent" — distinct
            // from a real 0% rate. The server can't see the SDK's config, so it approximates:
            // no activation{cohort} cells anywhere in the queried window is read as "not
            // tracked" for every cohort in that response, rather than a suspicious all-zero row.
            val activationCells = counterRepo.readCells(id, "activation", fromDay, toDay)
                .mapNotNull { cell -> cell.dims["cohort"]?.let { ActivationCounterCell(it, cell.n) } }
            val activationRateByCohort = if (activationCells.isEmpty()) {
                emptyMap()
            } else {
                ActivationCounterAnalyzer.analyze(activationCells, cohorts.associate { it.cohort to it.size })
                    .associate { it.cohort to it.rate }
            }

            call.respond(RetentionResponse(
                cohorts.map {
                    RetentionCohort(
                        cohortDate = it.cohort,
                        size = it.size,
                        day1 = it.day1,
                        day3 = it.day3,
                        day7 = it.day7,
                        day14 = it.day14,
                        day30 = it.day30,
                        activationRate = activationRateByCohort[it.cohort],
                    )
                }
            ))
        }
    }
}
