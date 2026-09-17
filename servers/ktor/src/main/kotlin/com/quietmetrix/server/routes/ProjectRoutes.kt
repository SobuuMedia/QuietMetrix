package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.AddMemberRequest
import com.quietmetrix.server.domain.CreateProjectRequest
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.ProjectListResponse
import com.quietmetrix.server.domain.ProjectMemberListResponse
import com.quietmetrix.server.domain.ProjectMemberResponse
import com.quietmetrix.server.domain.ProjectResponse
import com.quietmetrix.server.domain.RegenerateKeyResponse
import com.quietmetrix.server.domain.UpdateProjectRequest
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.ratelimit.QuotaEnforcer
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject

fun Routing.configureProjectRoutes() {
    val projectRepo by inject<ProjectRepository>()
    val userRepo by inject<UserRepository>()
    val memberRepo by inject<ProjectMemberRepository>()
    val accessTokenRepo by inject<AccessTokenRepository>()
    val config by inject<AppConfig>()
    val quotaEnforcer: QuotaEnforcer? = getKoin().getOrNull()

    route("/api/v1/projects") {
        // Create and list accept either a dashboard JWT or a scoped `qm_pat_…` personal
        // access token, so an agent/CLI can provision a project without a user's password —
        // see docs/agents/setup.md. Deliberately NOT inside authenticate("auth-jwt"): that
        // provider's JWT verifier would reject a PAT before this code ever runs.
        post {
            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Missing or invalid Authorization header")
                )
                return@post
            }

            if (!principal.canCreateProjects()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("forbidden", "Only admins can create projects")
                )
                return@post
            }
            val userId = principal.userId

            if (quotaEnforcer != null) {
                val user = userRepo.findById(userId)
                val planId = user?.get("planId") as? String
                val projectCount = projectRepo.countByOwnerId(userId)
                if (!quotaEnforcer.checkProjectLimit(planId, projectCount)) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("project_limit_reached", "You have reached the maximum number of projects allowed for your plan.")
                    )
                    return@post
                }
            }

            val request = try {
                call.receive<CreateProjectRequest>()
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

            // A retried request (e.g. an agent that never saw the first response) must not
            // mint a second project/key. The plaintext key is never stored, so a replay
            // cannot re-show it — it returns the earlier project's public info instead.
            val idempotencyKey = call.request.headers["Idempotency-Key"]?.trim()?.takeIf { it.isNotEmpty() }
            if (idempotencyKey != null) {
                val existing = projectRepo.findByOwnerAndIdempotencyKey(userId, idempotencyKey)
                if (existing != null) {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "id" to "proj_${existing["id"]}",
                        "name" to existing["name"],
                        "api_key_last4" to existing["apiKeyLast4"],
                        "message" to "A project already exists for this Idempotency-Key. Its API key was shown once, at creation, and cannot be retrieved again — use POST /projects/{id}/regenerate-key for a new one."
                    ))
                    return@post
                }
            }

            val apiKey = projectRepo.create(request.name, request.description, userId, idempotencyKey)

            call.respond(HttpStatusCode.Created, mapOf(
                "api_key" to apiKey,
                "api_key_last4" to apiKey.takeLast(4),
                "message" to "Project created. Store this key securely — it will not be shown again."
            ))
        }

        get {
            val principal = resolveApiPrincipal(call, config, accessTokenRepo) ?: run {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Missing or invalid Authorization header")
                )
                return@get
            }
            if (!principal.canReadProjects()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("forbidden", "This token cannot list projects")
                )
                return@get
            }
            val userId = principal.userId
            val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            val offset = call.parameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val ownerOnly = call.parameters["owner_only"]?.toBoolean() ?: false
            val isAdmin = principal.isAdmin()

            val projects = when {
                isAdmin -> projectRepo.findAll(limit, offset)
                ownerOnly -> projectRepo.findByOwnerId(userId, limit, offset)
                else -> projectRepo.findAccessibleByUserId(userId, limit, offset)
            }
            val total = when {
                isAdmin -> projectRepo.countAll()
                ownerOnly -> projectRepo.countByOwnerId(userId)
                else -> projectRepo.countAccessibleByUserId(userId)
            }

            call.respond(ProjectListResponse(
                projects = projects.map {
                    ProjectResponse(
                        id = "proj_${it["id"]}",
                        name = it["name"] as String,
                        description = it["description"] as? String,
                        apiKey = "***",
                        apiKeyLast4 = it["apiKeyLast4"] as? String,
                        planId = it["planId"] as? String,
                        createdAt = it["createdAt"].toString(),
                    )
                },
                total = total.toInt(),
            ))
        }

        authenticate("auth-jwt") {
            post("/{projectId}/regenerate-key") {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@post
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@post
                }

                val principal = call.principal<JWTPrincipal>() ?: return@post
                val userId = principal.payload.getClaim("userId").asString()
                val role = principal.roleClaim()

                if (role != "admin" && role != "developer") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You do not have permission to regenerate API keys"))
                    return@post
                }

                val project = projectRepo.findById(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                    return@post
                }

                // Non-admins must own or be a member of the project.
                if (role != "admin") {
                    val isOwner = project["ownerUserId"].toString() == userId
                    val membership = if (!isOwner) memberRepo.findMembership(id, userId.toLong()) else null
                    if (!isOwner && membership == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                        return@post
                    }
                }

                val newKey = projectRepo.regenerateApiKey(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                    return@post
                }
                call.respond(RegenerateKeyResponse(apiKey = newKey, apiKeyLast4 = newKey.takeLast(4)))
            }
        }

        authenticate("auth-jwt") {
            get("/{projectId}") {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@get
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@get
                }

                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()

                val project = projectRepo.findById(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                    return@get
                }

                val isAdmin = principal.roleClaim() == "admin"
                val isOwner = project["ownerUserId"].toString() == userId
                val membership = if (!isAdmin && !isOwner) memberRepo.findMembership(id, userId.toLong()) else null
                if (!isAdmin && !isOwner && membership == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                    return@get
                }

                call.respond(ProjectResponse(
                    id = "proj_${project["id"]}",
                    name = project["name"] as String,
                    description = project["description"] as? String,
                    apiKey = "***",
                    apiKeyLast4 = project["apiKeyLast4"] as? String,
                    planId = project["planId"] as? String,
                    createdAt = project["createdAt"].toString(),
                ))
            }

            patch("/{projectId}") {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@patch
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@patch
                }

                val principal = call.principal<JWTPrincipal>() ?: return@patch
                val userId = principal.payload.getClaim("userId").asString()

                val project = projectRepo.findById(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                    return@patch
                }

                if (principal.roleClaim() != "admin") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Only admins can update projects"))
                    return@patch
                }

                val request = try {
                    call.receive<UpdateProjectRequest>()
                } catch (_: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_json", "Request body is not valid JSON")
                    )
                    return@patch
                }

                if (request.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("schema_violation", "Field 'name' is required and must be a non-empty string")
                    )
                    return@patch
                }

                projectRepo.updateName(id, request.name)

                call.respond(ProjectResponse(
                    id = "proj_${project["id"]}",
                    name = request.name,
                    description = project["description"] as? String,
                    apiKey = "***",
                    apiKeyLast4 = project["apiKeyLast4"] as? String,
                    planId = project["planId"] as? String,
                    createdAt = project["createdAt"].toString(),
                ))
            }

            delete("/{projectId}") {
                val projectIdStr = call.parameters["projectId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                    return@delete
                }
                val id = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                    return@delete
                }

                val principal = call.principal<JWTPrincipal>() ?: return@delete
                val userId = principal.payload.getClaim("userId").asString()

                if (principal.roleClaim() != "admin") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Only admins can delete projects"))
                    return@delete
                }

                projectRepo.findById(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                    return@delete
                }

                projectRepo.softDelete(id)

                call.respond(mapOf(
                    "deleted" to true,
                    "project_id" to "proj_$id"
                ))
            }
        }

        authenticate("auth-jwt") {
            route("/{projectId}/members") {
                post {
                    val projectIdStr = call.parameters["projectId"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                        return@post
                    }
                    val projectId = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                        return@post
                    }

                    val principal = call.principal<JWTPrincipal>() ?: return@post
                    val userId = principal.payload.getClaim("userId").asString()

                    val project = projectRepo.findById(projectId) ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                        return@post
                    }

                    if (principal.roleClaim() != "admin") {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Only admins can manage members"))
                        return@post
                    }

                    val request = try {
                        call.receive<AddMemberRequest>()
                    } catch (_: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("invalid_json", "Request body is not valid JSON")
                        )
                        return@post
                    }

                    if (request.role != "viewer" && request.role != "admin") {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("schema_violation", "Role must be 'viewer' or 'admin'")
                        )
                        return@post
                    }

                    val memberUser = userRepo.findByEmail(request.email) ?: run {
                        call.application.environment.log.info("Add member failed: user with email ${request.email} not found")
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("unable_to_add_member", "Could not add member to project"))
                        return@post
                    }

                    val memberUserId = memberUser["id"] as Long

                    if (memberUserId == userId.toLong()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("bad_request", "You cannot add yourself as a member")
                        )
                        return@post
                    }

                    val existingMembership = memberRepo.findMembership(projectId, memberUserId)
                    if (existingMembership != null) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ErrorResponse("already_exists", "User is already a member of this project")
                        )
                        return@post
                    }

                    val memberId = memberRepo.add(projectId, memberUserId, request.role)

                    call.respond(HttpStatusCode.Created, ProjectMemberResponse(
                        id = memberId.toString(),
                        projectId = "proj_$projectId",
                        userId = memberUserId.toString(),
                        email = memberUser["email"] as String,
                        role = request.role,
                        createdAt = java.time.LocalDateTime.now().toString(),
                    ))
                }

                get {
                    val projectIdStr = call.parameters["projectId"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                        return@get
                    }
                    val projectId = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                        return@get
                    }

                    val principal = call.principal<JWTPrincipal>() ?: return@get
                    val userId = principal.payload.getClaim("userId").asString()

                    val project = projectRepo.findById(projectId) ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                        return@get
                    }

                    val isAdmin = principal.roleClaim() == "admin"
                    val isOwner = project["ownerUserId"].toString() == userId
                    val membership = if (!isAdmin && !isOwner) memberRepo.findMembership(projectId, userId.toLong()) else null
                    if (!isAdmin && !isOwner && membership == null) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You are not a member of this project"))
                        return@get
                    }

                    val members = memberRepo.findByProjectId(projectId)
                    val memberResponses = members.map { member ->
                        val memberUserId = member["userId"] as Long
                        val memberUserRow = userRepo.findById(memberUserId)
                        ProjectMemberResponse(
                            id = member["id"].toString(),
                            projectId = "proj_$projectId",
                            userId = memberUserId.toString(),
                            email = memberUserRow?.get("email") as? String ?: "unknown",
                            role = member["role"] as String,
                            createdAt = member["createdAt"].toString(),
                        )
                    }

                    call.respond(ProjectMemberListResponse(
                        members = memberResponses,
                        total = memberResponses.size,
                    ))
                }

                delete("/{memberUserId}") {
                    val projectIdStr = call.parameters["projectId"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing projectId"))
                        return@delete
                    }
                    val projectId = projectIdStr.removePrefix("proj_").toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid projectId format"))
                        return@delete
                    }

                    val memberUserIdStr = call.parameters["memberUserId"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing memberUserId"))
                        return@delete
                    }
                    val memberUserId = memberUserIdStr.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid memberUserId format"))
                        return@delete
                    }

                    val principal = call.principal<JWTPrincipal>() ?: return@delete

                    if (principal.roleClaim() != "admin") {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Only admins can remove members"))
                        return@delete
                    }

                    projectRepo.findById(projectId) ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                        return@delete
                    }

                    val removed = memberRepo.remove(projectId, memberUserId)
                    if (!removed) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Member not found"))
                        return@delete
                    }

                    call.respond(mapOf("deleted" to true))
                }
            }
        }
    }
}
