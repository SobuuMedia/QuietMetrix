package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.counters.CounterIngestProcessor
import com.quietmetrix.server.domain.CounterBatchRequest
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.ratelimit.IpRateLimiter
import com.quietmetrix.server.ratelimit.RateLimiter
import com.quietmetrix.server.util.clientIp
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.LocalDate

/**
 * `POST /api/v1/counters` — the sole ingest path. No install identifier ever reaches this
 * route: [com.quietmetrix.server.domain.CounterItem.u] is the SDK's own "first flush of this
 * cell today" signal, so there is nothing here to hash or throttle per-install.
 */
fun Routing.configureCounterRoutes() {
    val config by inject<AppConfig>()
    val projectRepo by inject<ProjectRepository>()
    val counterRepo by inject<CounterRepository>()
    val rateLimiter by inject<RateLimiter>()
    val ipRateLimiter by inject<IpRateLimiter>()

    route("/api/v1/counters") {
        post {
            val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0
            if (contentLength > 1_048_576) {
                call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Request body exceeds 1 MB"))
                return@post
            }

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

            val clientIp = call.clientIp(config.trustedProxies)

            if (config.rateLimit.enabled && !rateLimiter.tryConsume("counters:$projectId")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "Rate limit exceeded. Retry after 60 seconds."))
                return@post
            }
            if (config.ipRateLimit.enabled && !ipRateLimiter.tryConsume("counters:ip:$clientIp")) {
                call.response.header("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit_exceeded", "IP rate limit exceeded. Retry after 60 seconds."))
                return@post
            }

            val request = try {
                call.receive<CounterBatchRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Request body is not valid JSON"))
                return@post
            }
            if (request.counters.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("empty_batch", "counters must be non-empty"))
                return@post
            }

            val platform = request.sdk?.platform.orEmpty()
            val response = CounterIngestProcessor.process(
                projectId = projectId,
                request = request,
                today = LocalDate.now(),
                platform = platform,
                repository = counterRepo,
            )
            call.respond(HttpStatusCode.Accepted, response)
        }
    }
}
