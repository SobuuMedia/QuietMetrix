package com.quietmetrix.server.routes

import com.quietmetrix.server.ask.AskAnalyticsAnalyzer
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.AskDataRequest
import com.quietmetrix.server.domain.AskDataResponse
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.ratelimit.RateLimiter
import com.quietmetrix.server.retention.RetentionCounterCell
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.Instant
import java.time.ZoneOffset

/** Authenticated, project-scoped computed analytics answers; no model or causal inference is used. */
fun Routing.configureAskRoutes() {
    val counterRepo by inject<CounterRepository>()
    val tokenRepo by inject<AccessTokenRepository>()
    val projectRepo by inject<ProjectRepository>()
    val memberRepo by inject<ProjectMemberRepository>()
    val rateLimiter by inject<RateLimiter>()
    val config by inject<AppConfig>()

    route("/api/v1/projects/{projectId}/ask") {
        post {
            val projectId = call.parameters["projectId"]?.removePrefix("proj_")?.toLongOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid project id")); return@post
            }
            val principal = resolveApiPrincipal(call, config, tokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid authorization")); return@post
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics")); return@post
            }
            val project = projectRepo.findById(projectId) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found")); return@post
            }
            val isProjectOwner = (project["ownerUserId"] as? Number)?.toLong() == principal.userId
            if (!principal.isAdmin() && !isProjectOwner && memberRepo.findMembership(projectId, principal.userId) == null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project")); return@post
            }
            if (!rateLimiter.tryConsume("dashboard:${principal.userId}")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Question rate limit exceeded")); return@post
            }
            val request = runCatching { call.receive<AskDataRequest>() }.getOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid question body")); return@post
            }
            if (request.question.isBlank() || request.question.length > 500 || request.range !in setOf("1h", "1d", "7d", "30d", "90d")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Question or range is invalid")); return@post
            }
            val seconds = parseRangeSeconds(request.range)
            val window = analyticsWindow(seconds, null, null)
            val fromDay = window.fromDay
            val toDay = window.toDay
            val sessions = counterRepo.readCells(projectId, "session", fromDay, toDay)
            val transitions = counterRepo.readCells(projectId, "screen_transition", fromDay, toDay)
            val searches = counterRepo.readCells(projectId, "search", fromDay, toDay)
            val zeroResults = counterRepo.readCells(projectId, "search_zero_result", fromDay, toDay)
            val retention = counterRepo.readCells(projectId, "retention", fromDay, toDay).mapNotNull { cell ->
                val cohort = cell.dims["cohort"] ?: return@mapNotNull null
                val started = reportedCohortStart(cohort) ?: return@mapNotNull null
                if (started < fromDay || started > toDay) return@mapNotNull null
                val day = cell.dims["day"]?.toIntOrNull() ?: return@mapNotNull null
                RetentionCounterCell(cohort, day, cell.n)
            }
            val result = AskAnalyticsAnalyzer.answer(request.question, sessions, transitions, searches, zeroResults, retention, request.range)
            call.respond(AskDataResponse(result.text, result.source))
        }
    }
}
