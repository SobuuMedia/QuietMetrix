package com.quietmetrix.server.plugins

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.util.clientIp
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.koin.ktor.ext.inject

fun Application.configurePublicRequestLimits(config: AppConfig) {
    val users by inject<UserRepository>()
    val projects by inject<com.quietmetrix.server.persistence.ProjectRepository>()
    intercept(ApplicationCallPipeline.Call) {
        val ingestLimit = config.counterRequestsPerDay ?: if (config.isCloud) 10_000 else 0
        if (ingestLimit > 0 && call.request.path() in setOf("/api/v1/counters", "/api/v2/counters")) {
            val project = call.request.header("X-QM-Api-Key")?.let(projects::validateApiKey)
            if (project != null && !users.allowAuthAttempt("${config.auth.jwtSecret}:ingest-daily:$project", ingestLimit, 86_400)) {
                call.response.headers.append("Retry-After", "86400")
                call.respond(HttpStatusCode.TooManyRequests, com.quietmetrix.server.domain.ErrorResponse("ingest_quota_exceeded", "Daily ingestion request budget exhausted"))
                finish(); return@intercept
            }
        }

        // Probes must reach their own bounded database check when the database is down.
        if (config.ipRateLimit.enabled && call.request.path().startsWith("/api/") && call.request.path() !in setOf("/api/v1/health", "/api/v1/ready")) {
            val key = "${config.auth.jwtSecret}:preauth:${call.clientIp(config.trustedProxies)}"
            if (!users.allowAuthAttempt(key, maxOf(120, config.ipRateLimit.burstPerMinute))) {
                call.response.headers.append("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, com.quietmetrix.server.domain.ErrorResponse("rate_limit_exceeded", "Retry after 60 seconds"))
                finish()
            }
        }
    }
}
