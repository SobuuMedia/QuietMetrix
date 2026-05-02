package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.AddMemberRequest
import com.quietmetrix.server.domain.CreateProjectRequest
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.domain.ProjectListResponse
import com.quietmetrix.server.domain.ProjectMemberListResponse
import com.quietmetrix.server.domain.ProjectMemberResponse
import com.quietmetrix.server.domain.ProjectResponse
import com.quietmetrix.server.domain.UpdateProjectRequest
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
    val config by inject<AppConfig>()
    val quotaEnforcer: QuotaEnforcer? = getKoin().getOrNull()

    route("/api/v1/projects") {
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>() ?: return@post
                val userId = principal.payload.getClaim("userId").asString()

                if (quotaEnforcer != null) {
                    val user = userRepo.findById(userId.toLong())
                    val planId = user?.get("planId") as? String
                    val projectCount = projectRepo.countByOwnerId(userId.toLong())
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

                val (apiKey, _) = projectRepo.create(request.name, userId.toLong())

                call.respond(HttpStatusCode.Created, mapOf(
                    "api_key" to apiKey,
                    "message" to "Project created. Store this key securely — it will not be shown again."
                ))
            }

            get {
                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()
                val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                val offset = call.parameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val ownerOnly = call.parameters["owner_only"]?.toBoolean() ?: false

                val projects = if (ownerOnly) {
                    projectRepo.findByOwnerId(userId.toLong(), limit, offset)
                } else {
                    projectRepo.findAccessibleByUserId(userId.toLong(), limit, offset)
                }
                val total = if (ownerOnly) {
                    projectRepo.countByOwnerId(userId.toLong())
                } else {
                    projectRepo.countAccessibleByUserId(userId.toLong())
                }

                call.respond(ProjectListResponse(
                    projects = projects.map {
                        ProjectResponse(
                            id = "proj_${it["id"]}",
                            name = it["name"] as String,
                            apiKey = "***",
                            planId = it["planId"] as? String,
                            createdAt = it["createdAt"].toString(),
                        )
                    },
                    total = total.toInt(),
                ))
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

                val isOwner = project["ownerUserId"].toString() == userId
                val membership = if (!isOwner) memberRepo.findMembership(id, userId.toLong()) else null
                if (!isOwner && membership == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                    return@get
                }

                call.respond(ProjectResponse(
                    id = "proj_${project["id"]}",
                    name = project["name"] as String,
                    apiKey = "***",
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

                val isOwner = project["ownerUserId"].toString() == userId
                val membership = if (!isOwner) memberRepo.findMembership(id, userId.toLong()) else null
                if (!isOwner && (membership == null || membership["role"] != "admin")) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You must be an owner or admin to update this project"))
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
                    apiKey = "***",
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

                val project = projectRepo.findById(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                    return@delete
                }

                if (project["ownerUserId"].toString() != userId) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Only the project owner can delete this project"))
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

                    val isOwner = project["ownerUserId"].toString() == userId
                    val membership = if (!isOwner) memberRepo.findMembership(projectId, userId.toLong()) else null
                    if (!isOwner && (membership == null || membership["role"] != "admin")) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You must be an owner or admin to manage members"))
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

                    val isOwner = project["ownerUserId"].toString() == userId
                    val membership = if (!isOwner) memberRepo.findMembership(projectId, userId.toLong()) else null
                    if (!isOwner && membership == null) {
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
                    val userId = principal.payload.getClaim("userId").asString()

                    val project = projectRepo.findById(projectId) ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found"))
                        return@delete
                    }

                    val isOwner = project["ownerUserId"].toString() == userId
                    val membership = if (!isOwner) memberRepo.findMembership(projectId, userId.toLong()) else null
                    if (!isOwner && (membership == null || membership["role"] != "admin")) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You must be an owner or admin to remove members"))
                        return@delete
                    }

                    if (memberUserId == userId.toLong()) {
                        val adminMembers = memberRepo.findByProjectId(projectId).count {
                            (it["role"] as String) == "admin"
                        }
                        if (isOwner) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("bad_request", "Owner cannot be removed from the project")
                            )
                            return@delete
                        }
                        if (adminMembers <= 1) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("bad_request", "You cannot remove yourself as you are the last admin on this project")
                            )
                            return@delete
                        }
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
