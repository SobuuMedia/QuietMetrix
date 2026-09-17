package com.quietmetrix.server.routes

import com.quietmetrix.server.domain.CreateTokenRequest
import com.quietmetrix.server.domain.CreateTokenResponse
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.TokenListResponse
import com.quietmetrix.server.domain.TokenSummary
import com.quietmetrix.server.persistence.AccessTokenRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Personal access tokens (`qm_pat_…`) for agents/CLIs to call the API without a user's
 * password — see docs/agents/setup.md. Always JWT-authenticated: a token can never mint,
 * list, or revoke another token.
 */
fun Routing.configureTokenRoutes() {
    val tokenRepo by inject<AccessTokenRepository>()

    route("/api/v1/tokens") {
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>() ?: return@post
                val userId = principal.payload.getClaim("userId").asString().toLong()
                val role = principal.roleClaim()

                val request = try {
                    call.receive<CreateTokenRequest>()
                } catch (_: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_json", "Request body is not valid JSON")
                    )
                    return@post
                }

                if (request.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("schema_violation", "Field 'name' is required and must be a non-empty string")
                    )
                    return@post
                }

                val scopes = request.scopes?.toSet()?.takeIf { it.isNotEmpty() }
                    ?: setOf(TokenScopes.PROJECTS_CREATE)
                val unknownScopes = scopes - TokenScopes.ALL
                if (unknownScopes.isNotEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("schema_violation", "Unknown scope(s): ${unknownScopes.joinToString(", ")}")
                    )
                    return@post
                }

                // projects:create is the highest-privilege scope a PAT can carry — only an
                // admin session may mint one, mirroring the POST /projects admin gate.
                if (TokenScopes.PROJECTS_CREATE in scopes && role != "admin") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("forbidden", "Only admins can create tokens with the projects:create scope")
                    )
                    return@post
                }

                val created = tokenRepo.create(userId, request.name, scopes, request.expiresInDays)
                call.respond(
                    HttpStatusCode.Created,
                    CreateTokenResponse(
                        id = created.id.toString(),
                        name = created.name,
                        token = created.token,
                        last4 = created.last4,
                        scopes = created.scopes.toList(),
                        createdAt = created.createdAt.toString(),
                        expiresAt = created.expiresAt?.toString(),
                    )
                )
            }

            get {
                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString().toLong()
                val tokens = tokenRepo.list(userId)
                call.respond(TokenListResponse(
                    tokens.map {
                        @Suppress("UNCHECKED_CAST")
                        TokenSummary(
                            id = it["id"].toString(),
                            name = it["name"] as String,
                            last4 = it["last4"] as String,
                            scopes = (it["scopes"] as Set<String>).toList(),
                            createdAt = it["createdAt"].toString(),
                            expiresAt = it["expiresAt"]?.toString(),
                            lastUsedAt = it["lastUsedAt"]?.toString(),
                        )
                    }
                ))
            }

            delete("/{tokenId}") {
                val principal = call.principal<JWTPrincipal>() ?: return@delete
                val userId = principal.payload.getClaim("userId").asString().toLong()
                val id = call.parameters["tokenId"]?.toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid tokenId"))
                    return@delete
                }
                if (!tokenRepo.revoke(id, userId)) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Token not found"))
                    return@delete
                }
                call.respond(mapOf("revoked" to true))
            }
        }
    }
}
