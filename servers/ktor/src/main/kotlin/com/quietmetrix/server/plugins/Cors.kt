package com.quietmetrix.server.plugins

import com.quietmetrix.server.config.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureCors(config: AppConfig) {
    install(CORS) {
        val origins = config.cors.allowedOrigins
        require(origins.isNotEmpty()) {
            "QM_CORS_ALLOWED_ORIGINS must be set. Provide a comma-separated list of allowed origins."
        }
        // Ktor's allowHost expects a bare host (no scheme). Accept full origins
        // like "http://localhost:8080" and split off the scheme.
        origins.forEach { origin ->
            val trimmed = origin.trim()
            when {
                trimmed.startsWith("http://") ->
                    allowHost(trimmed.removePrefix("http://"), schemes = listOf("http"))
                trimmed.startsWith("https://") ->
                    allowHost(trimmed.removePrefix("https://"), schemes = listOf("https"))
                else ->
                    allowHost(trimmed, schemes = listOf("http", "https"))
            }
        }
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
