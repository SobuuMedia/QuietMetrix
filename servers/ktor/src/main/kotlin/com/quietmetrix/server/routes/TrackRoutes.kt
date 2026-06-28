package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.TrackBatchRequest
import com.quietmetrix.server.domain.TrackEventRequest
import com.quietmetrix.server.domain.TrackEventResponse
import com.quietmetrix.server.ingest.EventNormalizer
import com.quietmetrix.server.ingest.EventValidator
import com.quietmetrix.server.ingest.IngestChannel
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
import org.koin.ktor.ext.inject

fun Routing.configureTrackRoutes() {
    val config by inject<AppConfig>()
    val eventValidator by inject<EventValidator>()
    val eventNormalizer by inject<EventNormalizer>()
    val ingestChannel by inject<IngestChannel>()
    val rateLimiter by inject<com.quietmetrix.server.ratelimit.RateLimiter>()

    route("/api/v1") {
        get("/health") {
            call.respond(mapOf("ok" to true, "version" to "0.1.0"))
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

            val clientIp = call.clientIp(config.trustedProxies)

            ingestChannel.enqueue(projectId.toString(), request, clientIp)

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

            val clientIp = call.clientIp(config.trustedProxies)

            for (event in batchRequest.events) {
                ingestChannel.enqueueBatch(projectId.toString(), event, clientIp)
            }

            call.respond(HttpStatusCode.Accepted, TrackEventResponse(ok = true, queued = batchRequest.events.size))
        }
    }
}