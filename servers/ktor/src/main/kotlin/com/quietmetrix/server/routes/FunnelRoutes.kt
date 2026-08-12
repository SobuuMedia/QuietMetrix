package com.quietmetrix.server.routes

import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.funnels.CreateFunnelRequest
import com.quietmetrix.server.funnels.FunnelAnalyzer
import com.quietmetrix.server.funnels.FunnelBreakdownDto
import com.quietmetrix.server.funnels.FunnelBreakdownValueDto
import com.quietmetrix.server.funnels.FunnelDefinition
import com.quietmetrix.server.funnels.FunnelRangeDto
import com.quietmetrix.server.funnels.FunnelRegistrationService
import com.quietmetrix.server.funnels.FunnelResponse
import com.quietmetrix.server.funnels.FunnelResultsResponse
import com.quietmetrix.server.funnels.FunnelStepResult
import com.quietmetrix.server.funnels.FunnelStepResultDto
import com.quietmetrix.server.funnels.FunnelSummaryDto
import com.quietmetrix.server.funnels.FunnelTrendPointDto
import com.quietmetrix.server.funnels.FunnelValidation
import com.quietmetrix.server.funnels.FunnelValidationResult
import com.quietmetrix.server.funnels.FunnelsResponse
import com.quietmetrix.server.funnels.RegisterFunnelsRequest
import com.quietmetrix.server.funnels.RegisterFunnelsResponse
import com.quietmetrix.server.funnels.UpdateFunnelRequest
import com.quietmetrix.server.persistence.EventRepository
import com.quietmetrix.server.persistence.FunnelRecord
import com.quietmetrix.server.persistence.FunnelRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.ratelimit.RateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

private const val FUNNEL_RESULTS_ROW_CAP = 50_000

private fun parseRangeSeconds(range: String?): Long = when (range) {
    "1h" -> 3_600L
    "1d" -> 86_400L
    "30d" -> 2_592_000L
    "90d" -> 7_776_000L
    else -> 604_800L // "7d" and the default
}

fun Routing.configureFunnelRoutes() {
    val funnelRepo by inject<FunnelRepository>()
    val projectRepo by inject<ProjectRepository>()
    val memberRepo by inject<ProjectMemberRepository>()
    val registrationService by inject<FunnelRegistrationService>()
    val eventRepo by inject<EventRepository>()
    val rateLimiter by inject<RateLimiter>()

    /**
     * Resolves `{projectId}` (the `proj_<id>` form used everywhere else) and checks the
     * caller has access — admin, owner, or project member. Responds and returns null on any
     * failure so callers can `?: return@get` etc. Mirrors the inline checks in ProjectRoutes.kt.
     */
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
        val isAdmin = principal.roleClaim() == "admin"
        val isOwner = project["ownerUserId"].toString() == userId
        val membership = if (!isAdmin && !isOwner) memberRepo.findMembership(id, userId.toLong()) else null
        if (!isAdmin && !isOwner && membership == null) {
            respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
            return null
        }
        return id
    }

    fun FunnelStepResult.toDto() = FunnelStepResultDto(
        key = key, name = name, count = count,
        conversionFromEntry = conversionFromEntry, conversionFromPrevious = conversionFromPrevious,
        dropped = dropped, dropRate = dropRate,
        medianMsFromPrevious = medianMsFromPrevious, p90MsFromPrevious = p90MsFromPrevious,
    )

    fun FunnelRecord.toResponse() = FunnelResponse(
        funnelKey = funnelKey,
        name = name,
        description = description,
        steps = steps,
        windowSeconds = windowSeconds,
        source = source,
        locked = locked,
        countMode = countMode,
        identityScope = identityScope,
        correlationProperty = correlationProperty,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    route("/api/v1/projects/{projectId}/funnels") {
        authenticate("auth-jwt") {
            get {
                val projectId = call.resolveProjectId() ?: return@get
                call.respond(FunnelsResponse(funnelRepo.listActive(projectId).map { it.toResponse() }))
            }

            get("/{funnelKey}/results") {
                val projectId = call.resolveProjectId() ?: return@get
                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()
                if (!rateLimiter.tryConsume("dashboard:$userId")) {
                    call.response.header("Retry-After", "60")
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Dashboard rate limit exceeded"))
                    return@get
                }

                val funnelKey = call.parameters["funnelKey"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing funnelKey"))
                    return@get
                }
                val funnel = funnelRepo.findByKey(projectId, funnelKey) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Funnel not found"))
                    return@get
                }

                val rangeSeconds = parseRangeSeconds(call.parameters["range"])
                val breakdownDimension = call.parameters["breakdown"]
                    ?.takeIf { it in setOf("country", "platform", "device_class", "language") }
                val withTrend = call.parameters["trend"] == "1"

                val nowEpochSeconds = java.time.Instant.now().epochSecond
                val fromEpochSeconds = nowEpochSeconds - rangeSeconds
                val fromJava = java.time.Instant.ofEpochSecond(fromEpochSeconds)
                val toJava = java.time.Instant.ofEpochSecond(nowEpochSeconds)
                // Widened upper bound: a completion just after the requested range still
                // counts for an actor who entered inside it (see FunnelAnalyzer's entry-time
                // filter, which excludes anyone whose ENTRY falls outside [from, to)).
                val widenedToJava = java.time.Instant.ofEpochSecond(nowEpochSeconds + funnel.windowSeconds)

                val eventNames = funnel.steps.map { it.event }.toSet()
                val rows = eventRepo.findFunnelEvents(projectId, eventNames, fromJava, widenedToJava, FUNNEL_RESULTS_ROW_CAP)
                val truncated = rows.size >= FUNNEL_RESULTS_ROW_CAP

                val fromKotlin = kotlinx.datetime.Instant.fromEpochSeconds(fromEpochSeconds)
                val toKotlin = kotlinx.datetime.Instant.fromEpochSeconds(nowEpochSeconds)
                val analyzed = FunnelAnalyzer.analyze(
                    steps = funnel.steps,
                    windowSeconds = funnel.windowSeconds,
                    rows = rows,
                    from = fromKotlin,
                    to = toKotlin,
                    countMode = funnel.countMode,
                    identityScope = funnel.identityScope,
                    correlationProperty = funnel.correlationProperty,
                    breakdownDimension = breakdownDimension,
                    withTrend = withTrend,
                )

                call.respond(
                    FunnelResultsResponse(
                        funnel = FunnelSummaryDto(funnel.funnelKey, funnel.name, funnel.windowSeconds, funnel.steps, funnel.countMode, funnel.identityScope, funnel.correlationProperty),
                        range = FunnelRangeDto(fromJava.toString(), toJava.toString()),
                        countedBy = analyzed.countedBy,
                        entered = analyzed.entered,
                        converted = analyzed.converted,
                        overallConversion = analyzed.overallConversion,
                        medianTotalMs = analyzed.medianTotalMs,
                        steps = analyzed.steps.map { it.toDto() },
                        breakdown = analyzed.breakdown?.let { b ->
                            FunnelBreakdownDto(b.dimension, b.values.map {
                                FunnelBreakdownValueDto(it.value, it.entered, it.overallConversion, it.steps.map { s -> s.toDto() })
                            })
                        },
                        trend = analyzed.trend?.map { FunnelTrendPointDto(it.bucket, it.entered, it.converted, it.conversion) },
                        truncated = truncated,
                    )
                )
            }

            post {
                val projectId = call.resolveProjectId() ?: return@post
                val principal = call.principal<JWTPrincipal>() ?: return@post
                if (principal.roleClaim() !in setOf("admin", "developer")) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You do not have permission to create funnels"))
                    return@post
                }

                val request = try {
                    call.receive<CreateFunnelRequest>()
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Request body is not valid JSON"))
                    return@post
                }

                if (funnelRepo.findByKey(projectId, request.funnelKey) != null) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("funnel_exists", "A funnel with this key already exists"))
                    return@post
                }

                val definition = FunnelDefinition(
                    funnelKey = request.funnelKey,
                    name = request.name,
                    description = request.description,
                    steps = request.steps,
                    windowSeconds = request.windowSeconds,
                    source = "dashboard",
                    countMode = request.countMode,
                    identityScope = request.identityScope,
                    correlationProperty = request.correlationProperty,
                )
                val result = FunnelValidation.validate(definition, funnelRepo.countActive(projectId))
                if (result is FunnelValidationResult.Invalid) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("schema_violation", result.errors.joinToString("; ")))
                    return@post
                }

                val created = funnelRepo.create(projectId, definition)
                call.respond(HttpStatusCode.Created, created.toResponse())
            }

            patch("/{funnelKey}") {
                val projectId = call.resolveProjectId() ?: return@patch
                val principal = call.principal<JWTPrincipal>() ?: return@patch
                if (principal.roleClaim() !in setOf("admin", "developer")) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You do not have permission to edit funnels"))
                    return@patch
                }
                val funnelKey = call.parameters["funnelKey"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing funnelKey"))
                    return@patch
                }
                val existing = funnelRepo.findByKey(projectId, funnelKey) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Funnel not found"))
                    return@patch
                }

                val request = try {
                    call.receive<UpdateFunnelRequest>()
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Request body is not valid JSON"))
                    return@patch
                }

                // Editing a funnel in the dashboard locks it, so SDK auto-registration never
                // silently overwrites an analyst's edit again.
                val merged = FunnelDefinition(
                    funnelKey = funnelKey,
                    name = request.name ?: existing.name,
                    description = request.description ?: existing.description,
                    steps = request.steps ?: existing.steps,
                    windowSeconds = request.windowSeconds ?: existing.windowSeconds,
                    source = existing.source,
                    locked = true,
                    countMode = existing.countMode,
                    identityScope = existing.identityScope,
                    correlationProperty = existing.correlationProperty,
                )
                val result = FunnelValidation.validate(merged, funnelRepo.countActive(projectId) - 1)
                if (result is FunnelValidationResult.Invalid) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("schema_violation", result.errors.joinToString("; ")))
                    return@patch
                }

                funnelRepo.update(
                    projectId, funnelKey,
                    name = request.name, description = request.description,
                    steps = request.steps, windowSeconds = request.windowSeconds,
                    lock = true,
                )
                call.respond(funnelRepo.findByKey(projectId, funnelKey)!!.toResponse())
            }

            delete("/{funnelKey}") {
                val projectId = call.resolveProjectId() ?: return@delete
                val principal = call.principal<JWTPrincipal>() ?: return@delete
                if (principal.roleClaim() !in setOf("admin", "developer")) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You do not have permission to delete funnels"))
                    return@delete
                }
                val funnelKey = call.parameters["funnelKey"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing funnelKey"))
                    return@delete
                }
                if (!funnelRepo.archive(projectId, funnelKey)) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Funnel not found"))
                    return@delete
                }
                call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
            }
        }
    }

    // SDK auto-registration — API-key authenticated, like /api/v1/track. Not under
    // /projects/{projectId}/ because the project is derived from the key, matching /track.
    post("/api/v1/funnels/register") {
        val apiKey = call.request.header("X-QM-Api-Key")
        if (apiKey.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or missing API key"))
            return@post
        }
        val projectId = projectRepo.validateApiKey(apiKey)
        if (projectId == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or missing API key"))
            return@post
        }
        if (!rateLimiter.tryConsume("funnels:register:$projectId")) {
            call.response.header("Retry-After", "60")
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Rate limit exceeded. Retry after 60 seconds."))
            return@post
        }

        val request = try {
            call.receive<RegisterFunnelsRequest>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Request body is not valid JSON"))
            return@post
        }

        val namespace = request.namespace
        val revision = request.revision
        if (namespace != null && revision != null && !funnelRepo.acceptManifestRevision(projectId, namespace, revision)) {
            call.respond(RegisterFunnelsResponse(ignoredStaleManifest = true))
            return@post
        }
        val definitions = request.funnels.map {
            FunnelDefinition(
                funnelKey = it.funnelKey, name = it.name, description = it.description,
                steps = it.steps, windowSeconds = it.windowSeconds, countMode = it.countMode,
                identityScope = it.identityScope, correlationProperty = it.correlationProperty,
            )
        }
        val result = registrationService.register(projectId, definitions)
        call.respond(RegisterFunnelsResponse(result.registered, result.skippedLocked, result.rejected))
    }
}
