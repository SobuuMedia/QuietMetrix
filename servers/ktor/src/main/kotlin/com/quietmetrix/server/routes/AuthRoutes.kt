package com.quietmetrix.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.auth.accessToken
import com.quietmetrix.server.auth.refreshToken
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.LoginRequest
import com.quietmetrix.server.domain.LoginResponse
import com.quietmetrix.server.domain.RefreshRequest
import com.quietmetrix.server.domain.UserResponse
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.util.clientIp
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import com.quietmetrix.server.util.receiveBounded as receive

fun Routing.configureAuthRoutes(config: AppConfig) {
    val userRepo by inject<UserRepository>()

    route("/api/v1/auth") {
        post("/login") {
            val request = try {
                call.receive<LoginRequest>()
            } catch (exception: Exception) {
                if (exception is com.quietmetrix.server.util.RequestTooLarge || exception is com.quietmetrix.server.util.RequestBodyTimedOut) throw exception
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("invalid_json", "Request body is not valid JSON")
                )
                return@post
            }

            if (!request.email.matches(Regex("^[^@]+@[^@]+\\.[^@]+$"))) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("schema_violation", "Invalid email format")
                )
                return@post
            }

            val ip = call.clientIp(config.trustedProxies)
            if (!userRepo.allowAuthAttempt("${config.auth.jwtSecret}:login-ip:$ip", 20) ||
                !userRepo.allowAuthAttempt("${config.auth.jwtSecret}:login-account:${request.email.trim().lowercase()}", 5)) {
                call.response.headers.append("Retry-After", "60")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("too_many_attempts", "Retry after 60 seconds"))
                return@post
            }
            val user = userRepo.validatePassword(request.email.trim().lowercase(), request.password)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid email or password"))
                return@post
            }

            val role = user["role"] as? String ?: "reviewer"
            val sessionId = java.util.UUID.randomUUID().toString()
            val token = accessToken(config, user, sessionId)
            val refreshToken = refreshToken(config, user["id"].toString(), sessionId)
            if (!userRepo.createSession((user["id"] as Number).toLong(), sessionId, refreshToken, user["passwordHash"] as? String)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Account changed; sign in again"))
                return@post
            }

            call.respond(LoginResponse(
                token = token,
                refreshToken = refreshToken,
                user = UserResponse(
                    id = user["id"].toString(),
                    email = user["email"] as String,
                    role = role,
                    createdAt = user["createdAt"].toString(),
                    platformOperator = user["platformOperator"] == true,
                )
            ))
        }

        post("/logout") {
            val request = call.receive<RefreshRequest>()
            val decoded = runCatching {
                JWT.require(Algorithm.HMAC256(config.auth.jwtSecret)).withAudience(config.auth.jwtAudience)
                    .withIssuer(config.auth.jwtIssuer).build().verify(request.refreshToken)
            }.getOrNull()
            if (decoded?.getClaim("type")?.asString() == "refresh") {
                decoded.getClaim("sessionId").asString()?.let { userRepo.revokeSession(it, request.refreshToken) }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        post("/refresh") {
            val request = try {
                call.receive<RefreshRequest>()
            } catch (exception: Exception) {
                if (exception is com.quietmetrix.server.util.RequestTooLarge || exception is com.quietmetrix.server.util.RequestBodyTimedOut) throw exception
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("invalid_json", "Request body is not valid JSON")
                )
                return@post
            }

            val verifier = JWT.require(Algorithm.HMAC256(config.auth.jwtSecret))
                .withAudience(config.auth.jwtAudience)
                .withIssuer(config.auth.jwtIssuer)
                .build()

            val decoded = try {
                verifier.verify(request.refreshToken)
            } catch (exception: Exception) {
                if (exception is com.quietmetrix.server.util.RequestTooLarge || exception is com.quietmetrix.server.util.RequestBodyTimedOut) throw exception
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid or expired refresh token")
                )
                return@post
            }

            if (decoded.getClaim("type").asString() != "refresh") {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Not a refresh token")
                )
                return@post
            }

            val userId = decoded.getClaim("userId").asString()?.toLongOrNull()
            val user = userId?.let(userRepo::findById)
            if (user == null || user["status"] != "active") {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "User no longer exists")
                )
                return@post
            }

            // Rotate both tokens
            val role = user["role"] as? String ?: "reviewer"
            val sessionId = decoded.getClaim("sessionId").asString()
            if (sessionId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Sign in again"))
                return@post
            }
            val newRefreshToken = refreshToken(config, userId.toString(), sessionId)
            if (!userRepo.rotateSession(userId!!, sessionId, request.refreshToken, newRefreshToken)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Refresh token was revoked or already used"))
                return@post
            }
            val newToken = accessToken(config, user, sessionId)

            call.respond(LoginResponse(
                token = newToken,
                refreshToken = newRefreshToken,
                user = UserResponse(
                    id = userId.toString(),
                    email = user["email"] as String,
                    role = role,
                    createdAt = user["createdAt"].toString(),
                    platformOperator = user["platformOperator"] == true,
                )
            ))
        }
    }
}