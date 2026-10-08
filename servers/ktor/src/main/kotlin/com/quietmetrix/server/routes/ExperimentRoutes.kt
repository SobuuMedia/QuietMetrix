package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.experiments.CreateExperimentRequest
import com.quietmetrix.server.experiments.ExperimentConfigResponse
import com.quietmetrix.server.experiments.ExperimentConfigV2Response
import com.quietmetrix.server.experiments.ExperimentCounterAnalyzer
import com.quietmetrix.server.experiments.ExperimentOutcome
import com.quietmetrix.server.experiments.ExperimentRangeDto
import com.quietmetrix.server.experiments.ExperimentResultsResponse
import com.quietmetrix.server.experiments.ExperimentService
import com.quietmetrix.server.experiments.ExperimentsResponse
import com.quietmetrix.server.experiments.UpdateExperimentRequest
import com.quietmetrix.server.experiments.toConfigDto
import com.quietmetrix.server.experiments.toConfigV2Dto
import com.quietmetrix.server.experiments.toDto
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.CounterV2Repository
import com.quietmetrix.server.persistence.ExperimentRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.ratelimit.IpRateLimiter
import com.quietmetrix.server.ratelimit.QuotaEnforcer
import com.quietmetrix.server.ratelimit.RateLimiter
import com.quietmetrix.server.util.clientIp
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.ZoneOffset
import com.quietmetrix.server.util.receiveBounded as receive

/**
 * A/B testing experiment routes: dashboard CRUD + results (`/api/v1/projects/{id}/experiments…`,
 * JWT session or a `analytics:read`-scoped PAT for reads, JWT `admin`/`developer` for writes —
 * mirrors [configureFunnelRoutes] exactly) and the SDK-facing config fetch
 * (`GET /api/v1/experiments/config`, `X-QM-Api-Key` authenticated like `/api/v1/counters`).
 */
fun Routing.configureExperimentRoutes() {
    val experimentRepo by inject<ExperimentRepository>()
    val projectRepo by inject<ProjectRepository>()
    val memberRepo by inject<ProjectMemberRepository>()
    val counterRepo by inject<CounterRepository>()
    val counterV2Repo by inject<CounterV2Repository>()
    val accessTokenRepo by inject<AccessTokenRepository>()
    val config by inject<AppConfig>()
    val rateLimiter by inject<RateLimiter>()
    val ipRateLimiter by inject<IpRateLimiter>()
    val quotaEnforcer: QuotaEnforcer? = getKoin().getOrNull()

    val service = ExperimentService(experimentRepo, projectRepo, quotaEnforcer, config.isCloud)

    /** Resolves `{projectId}` and checks JWT-session access. Mirrors FunnelRoutes' resolveProjectId(). */
    suspend fun ApplicationCall.resolveProjectId(): Long? {
        val projectIdStr = parameters["projectId"] ?: run {
            respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
            return null
        }
        val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
            respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
            return null
        }
        val principal = principal<JWTPrincipal>() ?: return null
        val userId = principal.payload.getClaim("userId").asString()
        val project = projectRepo.findById(id) ?: run {
            respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
            return null
        }
        val isAdminRole = principal.roleClaim() == "admin"
        val isOwner = project["ownerUserId"].toString() == userId
        val membership = if (!isAdminRole && !isOwner) memberRepo.findMembership(id, userId.toLong()) else null
        if (!isAdminRole && !isOwner && membership == null) {
            respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
            return null
        }
        return id
    }

    /** Same resolution as [resolveProjectId] but generalized to an already-resolved [ApiPrincipal]
     *  (session or PAT), for read-only endpoints. Mirrors FunnelRoutes' resolveProjectIdForRead(). */
    suspend fun ApplicationCall.resolveProjectIdForRead(principal: ApiPrincipal): Long? {
        val projectIdStr = parameters["projectId"] ?: run {
            respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
            return null
        }
        val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
            respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
            return null
        }
        val project = projectRepo.findById(id) ?: run {
            respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
            return null
        }
        val isAdminRole = principal.isAdmin()
        val isOwner = project["ownerUserId"].toString() == principal.userId.toString()
        val membership = if (!isAdminRole && !isOwner) memberRepo.findMembership(id, principal.userId) else null
        if (!isAdminRole && !isOwner && membership == null) {
            respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
            return null
        }
        return id
    }

    fun ExperimentOutcome.Error.toStatus() = HttpStatusCode.fromValue(status)

    route("/api/v1/projects/{projectId}/experiments") {
        // Read-only: dashboard session JWT or a `analytics:read`-scoped PAT. Write endpoints
        // (create/edit/delete) below stay JWT-only.
        get {
            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid Authorization header"))
                return@get
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics"))
                return@get
            }
            val projectId = call.resolveProjectIdForRead(principal) ?: return@get
            val result = service.list(projectId)
            call.respond(ExperimentsResponse(result.experiments.map { it.toDto() }, result.limit))
        }

        get("/{key}/results") {
            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid Authorization header"))
                return@get
            }
            if (!principal.canReadAnalytics()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics"))
                return@get
            }
            val projectId = call.resolveProjectIdForRead(principal) ?: return@get
            val userId = principal.userId.toString()
            if (!rateLimiter.tryConsume("dashboard:$userId")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                return@get
            }

            val key = call.parameters["key"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing key"))
                return@get
            }
            val experiment = experimentRepo.findByKey(projectId, key) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Experiment not found"))
                return@get
            }

            val rangeSeconds = parseRangeSeconds(call.parameters["range"])
            val window = call.requestAnalyticsWindow(rangeSeconds) ?: return@get
            val fromJava = window.fromInstant
            val toJava = window.toInstant
            val fromDay = window.fromDay
            val toDay = window.toDay

            val v2 = experiment.mode in setOf("visibility", "variants")
            val resultFrom = if (v2) experiment.startedAt?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: fromDay else fromDay
            val resultTo = if (v2) LocalDate.now(ZoneOffset.UTC) else toDay
            val analysis = if (v2) {
                ExperimentCounterAnalyzer.analyzeV2(
                    experiment,
                    counterV2Repo.readExperimentCells(projectId, "experiment_exposure_v2", resultFrom, resultTo, experiment.experimentKey, experiment.revision),
                    counterV2Repo.readExperimentCells(projectId, "experiment_goal_v2", resultFrom, resultTo, experiment.experimentKey, experiment.revision),
                )
            } else {
                ExperimentCounterAnalyzer.analyze(experiment, counterRepo.readCells(projectId, "experiment", fromDay, toDay))
            }

            call.respond(
                ExperimentResultsResponse(
                    experiment = experiment.toDto(),
                    range = ExperimentRangeDto(
                        if (v2) (experiment.startedAt ?: fromJava.toString()) else fromJava.toString(),
                        if (v2) resultTo.atStartOfDay().toInstant(ZoneOffset.UTC).toString() else toJava.toString(),
                    ),
                    unit = if (v2) "installations" else "sessions",
                    basis = if (v2) "since_revision_started" else "selected_range",
                    variants = analysis.variants.map { it.toDto() },
                    comparison = analysis.comparison?.toDto(),
                ),
            )
        }

        // Write endpoints stay JWT-only — a PAT can never modify an experiment.
        authenticate("auth-jwt") {
            post {
                val projectId = call.resolveProjectId() ?: return@post
                val principal = call.principal<JWTPrincipal>() ?: return@post
                if (principal.roleClaim() !in setOf("admin", "developer")) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You do not have permission to create experiments"))
                    return@post
                }
                val request = try {
                    call.receive<CreateExperimentRequest>()
                } catch (exception: Exception) {
                if (exception is com.quietmetrix.server.util.RequestTooLarge || exception is com.quietmetrix.server.util.RequestBodyTimedOut) throw exception
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Request body is not valid JSON"))
                    return@post
                }
                when (val outcome = service.create(projectId, request)) {
                    is ExperimentOutcome.Ok -> call.respond(HttpStatusCode.Created, outcome.value.toDto())
                    is ExperimentOutcome.Error -> call.respond(outcome.toStatus(), ErrorResponse(outcome.code, outcome.message))
                }
            }

            patch("/{key}") {
                val projectId = call.resolveProjectId() ?: return@patch
                val principal = call.principal<JWTPrincipal>() ?: return@patch
                if (principal.roleClaim() !in setOf("admin", "developer")) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You do not have permission to edit experiments"))
                    return@patch
                }
                val key = call.parameters["key"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing key"))
                    return@patch
                }
                val request = try {
                    call.receive<UpdateExperimentRequest>()
                } catch (exception: Exception) {
                if (exception is com.quietmetrix.server.util.RequestTooLarge || exception is com.quietmetrix.server.util.RequestBodyTimedOut) throw exception
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Request body is not valid JSON"))
                    return@patch
                }
                when (val outcome = service.update(projectId, key, request)) {
                    is ExperimentOutcome.Ok -> call.respond(outcome.value.toDto())
                    is ExperimentOutcome.Error -> call.respond(outcome.toStatus(), ErrorResponse(outcome.code, outcome.message))
                }
            }

            delete("/{key}") {
                val projectId = call.resolveProjectId() ?: return@delete
                val principal = call.principal<JWTPrincipal>() ?: return@delete
                if (principal.roleClaim() !in setOf("admin", "developer")) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You do not have permission to delete experiments"))
                    return@delete
                }
                val key = call.parameters["key"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing key"))
                    return@delete
                }
                when (val outcome = service.delete(projectId, key)) {
                    is ExperimentOutcome.Ok -> call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                    is ExperimentOutcome.Error -> call.respond(outcome.toStatus(), ErrorResponse(outcome.code, outcome.message))
                }
            }
        }
    }

    // SDK-facing config fetch — API-key authenticated, like /api/v1/counters and
    // /api/v1/funnels/register. Not under /projects/{projectId}/ because the project is
    // derived from the key, matching /track's old layout.
    get("/api/v1/experiments/config") {
        val apiKey = call.request.header("X-QM-Api-Key")
        if (apiKey.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or missing API key"))
            return@get
        }
        val projectId = projectRepo.validateApiKey(apiKey)
        if (projectId == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or missing API key"))
            return@get
        }

        val clientIp = call.clientIp(config.trustedProxies)
        if (config.ipRateLimit.enabled && !ipRateLimiter.tryConsume("experiments:config:ip:$clientIp")) {
            call.response.header("Retry-After", "60")
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "IP rate limit exceeded. Retry after 60 seconds."))
            return@get
        }

        call.response.header("Cache-Control", "public, max-age=300")
        val active = service.activeConfig(projectId)
        call.respond(ExperimentConfigResponse(active.filter { it.mode == "legacy" }.map { it.toConfigDto() }))
    }

    // Versioned config for app-element experiments. Kept separate from v1 so an older SDK
    // cannot mistake direct B visibility for the legacy enrollment percentage.
    get("/api/v2/sdk/experiments") {
        val apiKey = call.request.header("X-QM-Api-Key")
        if (apiKey.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or missing API key"))
            return@get
        }
        val projectId = projectRepo.validateApiKey(apiKey)
        if (projectId == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or missing API key"))
            return@get
        }
        val clientIp = call.clientIp(config.trustedProxies)
        if (config.ipRateLimit.enabled && !ipRateLimiter.tryConsume("experiments:config:ip:$clientIp")) {
            call.response.header("Retry-After", "60")
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "IP rate limit exceeded. Retry after 60 seconds."))
            return@get
        }
        call.response.header("Cache-Control", "public, max-age=300")
        val active = service.activeConfig(projectId).filter { it.mode in setOf("visibility", "variants") }
        call.respond(ExperimentConfigV2Response(experiments = active.map { it.toConfigV2Dto() }))
    }
}
