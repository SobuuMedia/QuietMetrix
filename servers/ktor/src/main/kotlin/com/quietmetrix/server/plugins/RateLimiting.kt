package com.quietmetrix.server.plugins

import com.quietmetrix.server.config.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimiting(config: AppConfig) {
    if (!config.rateLimit.enabled) return

    install(RateLimit) {
        global {
            rateLimiter(config.rateLimit.requestsPerSecond, 1.seconds)
        }
        register(RateLimitName("write-key")) {
            rateLimiter(config.rateLimit.burstPerMinute, 1.minutes)
        }
    }
}