package com.quietmetrix.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.LoginRequest
import com.quietmetrix.server.domain.LoginResponse
import com.quietmetrix.server.domain.RefreshRequest
import com.quietmetrix.server.domain.UserResponse
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.util.clientIp
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val loginAttempts = ConcurrentHashMap<String, AtomicInteger>()
private val loginLockouts = ConcurrentHashMap<String, Long>()

fun Routing.configureAuthRoutes(config: AppConfig) {
    val userRepo by inject<UserRepository>()

    route("/api/v1/auth") {
        post("/login") {
            val request = try {
                call.receive<LoginRequest>()
            } catch (_: Exception) {
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
            val lockoutUntil = loginLockouts[ip]
            if (lockoutUntil != null && System.currentTimeMillis() < lockoutUntil) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("too_many_attempts", "Too many login attempts. Try again in a few minutes.")
                )
                return@post
            }

            val user = userRepo.validatePassword(request.email, request.password)
            if (user == null) {
                val attempts = loginAttempts.computeIfAbsent(ip) { AtomicInteger(0) }
                if (attempts.incrementAndGet() >= 5) {
                    loginLockouts[ip] = System.currentTimeMillis() + 300_000
                }
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid email or password")
                )
                return@post
            }

            loginAttempts.remove(ip)
            loginLockouts.remove(ip)

            val role = user["role"] as? String ?: "admin"
            val token = JWT.create()
                .withAudience(config.auth.jwtAudience)
                .withIssuer(config.auth.jwtIssuer)
                .withClaim("userId", user["id"].toString())
                .withClaim("email", user["email"] as String)
                .withClaim("role", role)
                .withExpiresAt(Date(System.currentTimeMillis() + config.auth.sessionTtlHours * 3600_000L))
                .sign(Algorithm.HMAC256(config.auth.jwtSecret))

            val refreshToken = JWT.create()
                .withAudience(config.auth.jwtAudience)
                .withIssuer(config.auth.jwtIssuer)
                .withClaim("userId", user["id"].toString())
                .withClaim("type", "refresh")
                .withExpiresAt(Date(System.currentTimeMillis() + 2L * 24 * 3600_000L))
                .sign(Algorithm.HMAC256(config.auth.jwtSecret))

            call.respond(LoginResponse(
                token = token,
                refreshToken = refreshToken,
                user = UserResponse(
                    id = user["id"].toString(),
                    email = user["email"] as String,
                    role = role,
                    createdAt = user["createdAt"].toString(),
                )
            ))
        }

        post("/refresh") {
            val request = try {
                call.receive<RefreshRequest>()
            } catch (_: Exception) {
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
            } catch (_: Exception) {
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

            val userId = decoded.getClaim("userId").asString()
            val user = userRepo.findById(userId.toLong())
            if (user == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "User no longer exists")
                )
                return@post
            }

            // Rotate both tokens
            val role = user["role"] as? String ?: "admin"
            val newToken = JWT.create()
                .withAudience(config.auth.jwtAudience)
                .withIssuer(config.auth.jwtIssuer)
                .withClaim("userId", userId)
                .withClaim("email", user["email"] as String)
                .withClaim("role", role)
                .withExpiresAt(Date(System.currentTimeMillis() + config.auth.sessionTtlHours * 3600_000L))
                .sign(Algorithm.HMAC256(config.auth.jwtSecret))

            val newRefreshToken = JWT.create()
                .withAudience(config.auth.jwtAudience)
                .withIssuer(config.auth.jwtIssuer)
                .withClaim("userId", userId)
                .withClaim("type", "refresh")
                .withExpiresAt(Date(System.currentTimeMillis() + 2L * 24 * 3600_000L))
                .sign(Algorithm.HMAC256(config.auth.jwtSecret))

            call.respond(LoginResponse(
                token = newToken,
                refreshToken = newRefreshToken,
                user = UserResponse(
                    id = userId,
                    email = user["email"] as String,
                    role = role,
                    createdAt = user["createdAt"].toString(),
                )
            ))
        }
    }
}