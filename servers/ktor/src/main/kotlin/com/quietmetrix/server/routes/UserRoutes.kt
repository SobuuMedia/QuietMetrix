package com.quietmetrix.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.AcceptInviteRequest
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.InvitePreviewResponse
import com.quietmetrix.server.domain.InviteResponse
import com.quietmetrix.server.domain.InviteUserRequest
import com.quietmetrix.server.domain.LoginResponse
import com.quietmetrix.server.domain.UpdateUserRoleRequest
import com.quietmetrix.server.domain.UserListItem
import com.quietmetrix.server.domain.UserListResponse
import com.quietmetrix.server.domain.UserResponse
import com.quietmetrix.server.persistence.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.util.Date

private val VALID_ROLES = setOf("admin", "developer", "reviewer")

fun Routing.configureUserRoutes(config: AppConfig) {
    val userRepo by inject<UserRepository>()

    // ---- User management (admin only) ----
    route("/api/v1/users") {
        authenticate("auth-jwt") {
            get {
                val principal = call.principal<JWTPrincipal>() ?: return@get
                if (principal.roleClaim() != "admin") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Admins only"))
                    return@get
                }
                call.respond(UserListResponse(userRepo.listAll().map {
                    UserListItem(
                        id = it["id"].toString(),
                        email = it["email"] as String,
                        role = it["role"] as? String ?: "admin",
                        status = it["status"] as? String ?: "active",
                    )
                }))
            }

            post("/invite") {
                val principal = call.principal<JWTPrincipal>() ?: return@post
                if (principal.roleClaim() != "admin") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Admins only"))
                    return@post
                }
                val request = try {
                    call.receive<InviteUserRequest>()
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Invalid JSON"))
                    return@post
                }
                val email = request.email.trim().lowercase()
                if (!email.matches(Regex("^[^@]+@[^@]+\\.[^@]+$"))) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", "A valid email is required"))
                    return@post
                }
                if (request.role !in VALID_ROLES) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", "role must be one of $VALID_ROLES"))
                    return@post
                }
                if (userRepo.findByEmail(email) != null) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("already_exists", "A user with that email already exists"))
                    return@post
                }
                val token = userRepo.createInvite(email, request.role)
                val origin = call.request.origin
                val link = "${origin.scheme}://${origin.serverHost}:${origin.serverPort}/?invite=$token"
                val created = userRepo.findByEmail(email)
                call.respond(HttpStatusCode.Created, InviteResponse(
                    inviteLink = link,
                    user = UserListItem(
                        id = created?.get("id").toString(),
                        email = email,
                        role = request.role,
                        status = "invited",
                    ),
                ))
            }

            patch("/{id}") {
                val principal = call.principal<JWTPrincipal>() ?: return@patch
                if (principal.roleClaim() != "admin") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Admins only"))
                    return@patch
                }
                val id = call.parameters["id"]?.toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid user id"))
                    return@patch
                }
                val request = try {
                    call.receive<UpdateUserRoleRequest>()
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Invalid JSON"))
                    return@patch
                }
                if (request.role !in VALID_ROLES) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", "role must be one of $VALID_ROLES"))
                    return@patch
                }
                val target = userRepo.findById(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "User not found"))
                    return@patch
                }
                if (target["role"] == "admin" && request.role != "admin" && userRepo.countAdmins() <= 1) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("last_admin", "Cannot remove the last admin"))
                    return@patch
                }
                userRepo.updateRole(id, request.role)
                call.respond(mapOf("ok" to true))
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>() ?: return@delete
                if (principal.roleClaim() != "admin") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Admins only"))
                    return@delete
                }
                val id = call.parameters["id"]?.toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid user id"))
                    return@delete
                }
                if (id.toString() == principal.payload.getClaim("userId").asString()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", "You cannot delete your own account"))
                    return@delete
                }
                val target = userRepo.findById(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "User not found"))
                    return@delete
                }
                if (target["role"] == "admin" && userRepo.countAdmins() <= 1) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("last_admin", "Cannot delete the last admin"))
                    return@delete
                }
                userRepo.delete(id)
                call.respond(mapOf("ok" to true))
            }
        }
    }

    // ---- Invitation acceptance (public) ----
    route("/api/v1/invites") {
        get("/{token}") {
            val token = call.parameters["token"] ?: return@get
            val user = userRepo.findByInviteToken(token) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("invalid_invite", "This invitation is invalid or has expired"))
                return@get
            }
            call.respond(InvitePreviewResponse(email = user["email"] as String))
        }

        post("/{token}/accept") {
            val token = call.parameters["token"] ?: return@post
            val request = try {
                call.receive<AcceptInviteRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_json", "Invalid JSON"))
                return@post
            }
            if (request.password.length < 8) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", "Password must be at least 8 characters"))
                return@post
            }
            val user = userRepo.findByInviteToken(token) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("invalid_invite", "This invitation is invalid or has expired"))
                return@post
            }
            val userId = user["id"].toString()
            userRepo.acceptInvite(userId.toLong(), request.password)

            val role = user["role"] as? String ?: "reviewer"
            val email = user["email"] as String
            val accessToken = JWT.create()
                .withAudience(config.auth.jwtAudience)
                .withIssuer(config.auth.jwtIssuer)
                .withClaim("userId", userId)
                .withClaim("email", email)
                .withClaim("role", role)
                .withExpiresAt(Date(System.currentTimeMillis() + config.auth.sessionTtlHours * 3600_000L))
                .sign(Algorithm.HMAC256(config.auth.jwtSecret))
            val refreshToken = JWT.create()
                .withAudience(config.auth.jwtAudience)
                .withIssuer(config.auth.jwtIssuer)
                .withClaim("userId", userId)
                .withClaim("type", "refresh")
                .withExpiresAt(Date(System.currentTimeMillis() + 2L * 24 * 3600_000L))
                .sign(Algorithm.HMAC256(config.auth.jwtSecret))

            call.respond(LoginResponse(
                token = accessToken,
                refreshToken = refreshToken,
                user = UserResponse(id = userId, email = email, role = role, createdAt = ""),
            ))
        }
    }
}
