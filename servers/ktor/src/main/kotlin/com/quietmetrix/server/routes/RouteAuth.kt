package com.quietmetrix.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.persistence.AccessTokenRepository
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal

/**
 * Global role carried in the JWT (admin | developer | reviewer). Legacy tokens
 * issued before roles existed default to admin (tokens are short-lived and all
 * pre-existing users were admins).
 */
internal fun JWTPrincipal.roleClaim(): String =
    payload.getClaim("role").asString() ?: "admin"

/**
 * Scope slugs a personal access token (`qm_pat_…`) can carry. Deliberately least-privilege:
 * a PAT can create/list projects and read analytics (with the matching scope) but can never
 * delete a project, regenerate a key, or manage users — those stay dashboard-session (JWT) only.
 */
object TokenScopes {
    const val PROJECTS_CREATE = "projects:create"
    const val PROJECTS_READ = "projects:read"
    const val ANALYTICS_READ = "analytics:read"
    val ALL = setOf(PROJECTS_CREATE, PROJECTS_READ, ANALYTICS_READ)
}

/**
 * Unifies a dashboard session (JWT) and an agent/CLI credential (personal access token)
 * behind one shape, so routes that must accept either don't need two code paths.
 */
sealed class ApiPrincipal {
    abstract val userId: Long

    data class Session(override val userId: Long, val role: String) : ApiPrincipal()
    data class Token(override val userId: Long, val scopes: Set<String>) : ApiPrincipal()
}

/** True only for an admin dashboard session — a PAT is never treated as admin. */
fun ApiPrincipal.isAdmin(): Boolean = this is ApiPrincipal.Session && role == "admin"

/** Mirrors the "only admins can create projects" rule, extended to a scoped PAT. */
fun ApiPrincipal.canCreateProjects(): Boolean = when (this) {
    is ApiPrincipal.Session -> role == "admin"
    is ApiPrincipal.Token -> TokenScopes.PROJECTS_CREATE in scopes
}

/** Listing was previously open to any authenticated role; a PAT needs an explicit read/create scope. */
fun ApiPrincipal.canReadProjects(): Boolean = when (this) {
    is ApiPrincipal.Session -> true
    is ApiPrincipal.Token -> TokenScopes.PROJECTS_READ in scopes || TokenScopes.PROJECTS_CREATE in scopes
}

/** Gates read access to event/funnel analytics — a PAT needs the explicit analytics:read scope. */
fun ApiPrincipal.canReadAnalytics(): Boolean = when (this) {
    is ApiPrincipal.Session -> true
    is ApiPrincipal.Token -> TokenScopes.ANALYTICS_READ in scopes
}

/**
 * Resolves the caller of a request that may be authenticated with either a dashboard JWT
 * or a `qm_pat_…` personal access token. Returns null if the Authorization header is
 * missing, malformed, or the credential does not validate — callers should respond 401.
 *
 * Deliberately bypasses the Ktor `authenticate("auth-jwt")` plugin: that provider's JWT
 * verifier rejects a non-JWT bearer token (a PAT) before a route handler ever runs, so
 * routes accepting both credential types must resolve the principal themselves.
 */
fun resolveApiPrincipal(
    call: ApplicationCall,
    config: AppConfig,
    accessTokenRepo: AccessTokenRepository,
): ApiPrincipal? {
    val header = call.request.headers["Authorization"] ?: return null
    if (!header.startsWith("Bearer ")) return null
    val raw = header.removePrefix("Bearer ").trim()
    if (raw.isEmpty()) return null

    if (raw.startsWith("qm_pat_")) {
        val validated = accessTokenRepo.validate(raw) ?: return null
        return ApiPrincipal.Token(validated.userId, validated.scopes)
    }

    return try {
        val verifier = JWT.require(Algorithm.HMAC256(config.auth.jwtSecret))
            .withAudience(config.auth.jwtAudience)
            .withIssuer(config.auth.jwtIssuer)
            .build()
        val decoded = verifier.verify(raw)
        val userId = decoded.getClaim("userId").asString()?.toLongOrNull() ?: return null
        val role = decoded.getClaim("role").asString() ?: "admin"
        ApiPrincipal.Session(userId, role)
    } catch (_: Exception) {
        null
    }
}
