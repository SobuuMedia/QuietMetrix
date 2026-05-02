package com.quietmetrix.server.plugins

import com.quietmetrix.server.config.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.host
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureCors(config: AppConfig) {
    install(CORS) {
        val origins = config.cors.allowedOrigins
        require(origins.isNotEmpty()) {
            "QM_CORS_ALLOWED_ORIGINS must be set. Provide a comma-separated list of allowed origins."
        }
        origins.forEach { host(it) }
        allowCredentials = true
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-QM-Api-Key")
    }
}
