package com.quietmetrix.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

fun Application.configureSecurity(config: AppConfig) {
    val jwtSecret = config.auth.jwtSecret
    val jwtIssuer = config.auth.jwtIssuer
    val jwtAudience = config.auth.jwtAudience

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience) &&
                    credential.payload.issuer == jwtIssuer) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    com.quietmetrix.server.domain.ErrorResponse("unauthorized", "Invalid or missing session token")
                )
            }
        }
    }
}