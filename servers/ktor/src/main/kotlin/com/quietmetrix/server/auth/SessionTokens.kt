package com.quietmetrix.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.config.AppConfig
import java.util.Date

fun accessToken(config: AppConfig, user: Map<String, Any?>, sessionId: String): String = JWT.create()
    .withAudience(config.auth.jwtAudience).withIssuer(config.auth.jwtIssuer)
    .withClaim("userId", user["id"].toString()).withClaim("email", user["email"].toString())
    .withClaim("role", user["role"].toString()).withClaim("type", "access").withClaim("sessionId", sessionId)
    .withExpiresAt(Date(System.currentTimeMillis() + config.auth.sessionTtlHours * 3600_000L))
    .sign(Algorithm.HMAC256(config.auth.jwtSecret))

fun refreshToken(config: AppConfig, userId: String, sessionId: String): String = JWT.create()
    .withAudience(config.auth.jwtAudience).withIssuer(config.auth.jwtIssuer)
    .withClaim("userId", userId).withClaim("type", "refresh").withClaim("sessionId", sessionId)
    .withJWTId(java.util.UUID.randomUUID().toString())
    .withExpiresAt(Date(System.currentTimeMillis() + 2L * 24 * 3600_000L))
    .sign(Algorithm.HMAC256(config.auth.jwtSecret))
