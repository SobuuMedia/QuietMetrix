package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.HealthResponse
import com.quietmetrix.server.domain.TrackBatchRequest
import com.quietmetrix.server.domain.TrackEventRequest
import com.quietmetrix.server.domain.TrackEventResponse
import com.quietmetrix.server.ingest.EventNormalizer
import com.quietmetrix.server.ingest.EventValidator
import com.quietmetrix.server.ingest.IngestChannel
import com.quietmetrix.server.ingest.InstallIdHasher
import com.quietmetrix.server.ingest.ValidationResult
import com.quietmetrix.server.util.clientIp
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

fun Routing.configureTrackRoutes() {
    val config by inject<AppConfig>()
    val eventValidator by inject<EventValidator>()
    val eventNormalizer by inject<EventNormalizer>()
    val ingestChannel by inject<IngestChannel>()
    val rateLimiter by inject<com.quietmetrix.server.ratelimit.RateLimiter>()
    val ipRateLimiter by inject<com.quietmetrix.server.ratelimit.IpRateLimiter>()
    val installRateLimiter by inject<com.quietmetrix.server.ratelimit.InstallRateLimiter>()
    val installRepo by inject<com.quietmetrix.server.persistence.InstallRepository>()
    val quarantineRepo by inject<com.quietmetrix.server.persistence.QuarantineRepository>()
    val auditRepo by inject<com.quietmetrix.server.persistence.IngestAuditRepository>()
    val json = Json { encodeDefaults = true }

    route("/api/v1") {
        get("/health") {
            call.respond(HealthResponse(ok = true, version = "0.2.0"))
        }

        post("/track") {
            val contentLength = call.request.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull() ?: 0
            if (contentLength > 1_048_576) {
                call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    ErrorResponse("payload_too_large", "Request body exceeds 1 MB")
                )
                return@post
            }

            val apiKey = call.request.header("X-QM-Api-Key")
            if (apiKey.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid or missing API key")
                )
                return@post
            }

            val projectRepo by inject<com.quietmetrix.server.persistence.ProjectRepository>()
            val projectId = projectRepo.validateApiKey(apiKey)
            if (projectId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid or missing API key")
                )
                return@post
            }

            val clientIp = call.clientIp(config.trustedProxies)

            if (config.rateLimit.enabled) {
                val key = "wm:$projectId"
                if (!rateLimiter.tryConsume(key)) {
                    call.response.header("Retry-After", "60")
                    call.response.header("X-RateLimit-Limit", "60")
                    call.response.header("X-RateLimit-Remaining", "0")
                    call.response.header("X-RateLimit-Reset", (System.currentTimeMillis() / 1000 + 60).toString())
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("rate_limit_exceeded", "Rate limit exceeded. Retry after 60 seconds.")
                    )
                    return@post
                }
            }

            if (config.ipRateLimit.enabled) {
                if (!ipRateLimiter.tryConsume("wm:ip:$clientIp")) {
                    call.response.header("Retry-After", "60")
                    call.response.header("X-RateLimit-Limit", config.ipRateLimit.burstPerMinute.toString())
                    call.response.header("X-RateLimit-Remaining", "0")
                    call.response.header("X-RateLimit-Reset", (System.currentTimeMillis() / 1000 + 60).toString())
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("rate_limit_exceeded", "IP rate limit exceeded. Retry after 60 seconds.")
                    )
                    return@post
                }
            }

            val request = try {
                call.receive<TrackEventRequest>()
            } catch (_: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("invalid_json", "Request body is not valid JSON")
                )
                return@post
            }

            val result = eventValidator.validate(request)
            if (result is ValidationResult.Invalid) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("schema_violation", result.errors.joinToString("; "))
                )
                return@post
            }

            val strict = projectRepo.getStrictSchema(projectId)
            if (strict.enabled && strict.allowedEvents.isNotEmpty()) {
                val strictResult = eventValidator.validateStrict(request, strict.allowedEvents)
                if (strictResult is ValidationResult.Invalid) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("unknown_event", strictResult.errors.joinToString("; "))
                    )
                    return@post
                }
            }

            val anonymousId = request.ctx?.anonymousId
            var installHash: String? = null
            if (!anonymousId.isNullOrBlank()) {
                val salt = projectRepo.getInstallSalt(projectId)
                if (!salt.isNullOrBlank()) {
                    installHash = InstallIdHasher.hash(salt, anonymousId)
                    val install = installRepo.upsert(projectId, installHash!!)
                    if (install.revoked) {
                        // Quarantine the event for review + audit the attempt.
                        quarantineRepo.insert(projectId, json.encodeToString(TrackEventRequest.serializer(), request), "ramp-up", "auto-revoked", clientIp, installHash!!)
                        auditRepo.log(projectId, apiKey.takeLast(4), clientIp, installHash!!, request.event, "quarantine", "auto-revoked ramp-up")
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse("install_revoked", "Install has been revoked due to anomalous activity.")
                        )
                        return@post
                    }
                    if (!installRateLimiter.tryConsume("${projectId}:${installHash!!}")) {
                        call.response.header("Retry-After", "60")
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            ErrorResponse("rate_limit_exceeded", "Install rate limit exceeded. Retry after 60 seconds.")
                        )
                        return@post
                    }
                }
            }

            ingestChannel.enqueue(projectId.toString(), request, clientIp)

            auditRepo.log(projectId, apiKey.takeLast(4), clientIp, installHash, request.event, "accepted", null)

            call.respond(HttpStatusCode.Accepted, TrackEventResponse(ok = true, queued = 1))
        }

        post("/track/batch") {
            val contentLength = call.request.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull() ?: 0
            if (contentLength > 1_048_576) {
                call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    ErrorResponse("payload_too_large", "Request body exceeds 1 MB")
                )
                return@post
            }

            val apiKey = call.request.header("X-QM-Api-Key")
            if (apiKey.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid or missing API key")
                )
                return@post
            }

            val projectRepo by inject<com.quietmetrix.server.persistence.ProjectRepository>()
            val projectId = projectRepo.validateApiKey(apiKey)
            if (projectId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid or missing API key")
                )
                return@post
            }

            val clientIp = call.clientIp(config.trustedProxies)

            if (config.ipRateLimit.enabled) {
                if (!ipRateLimiter.tryConsume("wm:ip:$clientIp")) {
                    call.response.header("Retry-After", "60")
                    call.response.header("X-RateLimit-Limit", config.ipRateLimit.burstPerMinute.toString())
                    call.response.header("X-RateLimit-Remaining", "0")
                    call.response.header("X-RateLimit-Reset", (System.currentTimeMillis() / 1000 + 60).toString())
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("rate_limit_exceeded", "IP rate limit exceeded. Retry after 60 seconds.")
                    )
                    return@post
                }
            }

            val batchRequest = try {
                call.receive<TrackBatchRequest>()
            } catch (_: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("invalid_json", "Request body is not valid JSON")
                )
                return@post
            }

            val result = eventValidator.validateBatch(batchRequest.events)
            if (result is ValidationResult.Invalid) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("schema_violation", result.errors.joinToString("; "))
                )
                return@post
            }

            val strict = projectRepo.getStrictSchema(projectId)
            if (strict.enabled && strict.allowedEvents.isNotEmpty()) {
                val strictResult = eventValidator.validateBatchStrict(batchRequest.events, strict.allowedEvents)
                if (strictResult is ValidationResult.Invalid) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("schema_violation", strictResult.errors.joinToString("; "))
                    )
                    return@post
                }
            }

            if (config.rateLimit.enabled) {
                val key = "wm:$projectId"
                if (!rateLimiter.tryConsume(key)) {
                    call.response.header("Retry-After", "60")
                    call.response.header("X-RateLimit-Limit", "60")
                    call.response.header("X-RateLimit-Remaining", "0")
                    call.response.header("X-RateLimit-Reset", (System.currentTimeMillis() / 1000 + 60).toString())
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("rate_limit_exceeded", "Rate limit exceeded. Retry after 60 seconds.")
                    )
                    return@post
                }
            }

            // Per-install (anonymousId) abuse defense — apply per distinct install in the batch.
            val installCounts = batchRequest.events
                .mapNotNull { it.ctx?.anonymousId?.takeIf { id -> id.isNotBlank() } }
                .groupingBy { it }.eachCount()
            if (installCounts.isNotEmpty()) {
                val salt = projectRepo.getInstallSalt(projectId)
                if (!salt.isNullOrBlank()) {
                    for ((rawAnonymousId, n) in installCounts) {
                        val installHash = InstallIdHasher.hash(salt, rawAnonymousId)
                        val install = installRepo.upsertCount(projectId, installHash, n.toLong())
                        if (install.revoked) {
                            // Quarantine events from this install + audit.
                            val badEvents = batchRequest.events.filter { it.ctx?.anonymousId == rawAnonymousId }
                            for (ev in badEvents) {
                                quarantineRepo.insert(projectId, json.encodeToString(TrackEventRequest.serializer(), ev), "ramp-up", "auto-revoked", clientIp, installHash)
                            }
                            auditRepo.log(projectId, apiKey.takeLast(4), clientIp, installHash, batchRequest.events.first().event, "quarantine", "auto-revoked ramp-up batch")
                            call.respond(
                                HttpStatusCode.Forbidden,
                                ErrorResponse("install_revoked", "Install has been revoked due to anomalous activity.")
                            )
                            return@post
                        }
                        if (!installRateLimiter.tryConsume("$projectId:$installHash", n)) {
                            call.response.header("Retry-After", "60")
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                ErrorResponse("rate_limit_exceeded", "Install rate limit exceeded. Retry after 60 seconds.")
                            )
                            return@post
                        }
                    }
                }
            }

            for (event in batchRequest.events) {
                ingestChannel.enqueueBatch(projectId.toString(), event, clientIp)
            }

            auditRepo.log(projectId, apiKey.takeLast(4), clientIp, null, "batch(${batchRequest.events.size})", "accepted", null)

            call.respond(HttpStatusCode.Accepted, TrackEventResponse(ok = true, queued = batchRequest.events.size))
        }
    }
}