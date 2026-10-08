package com.quietmetrix.server.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectMember(
    val id: String,
    val projectId: String,
    val userId: String,
    val role: String,
    val createdAt: String,
)

@Serializable
data class AddMemberRequest(
    val email: String,
    val role: String = "viewer",
)

@Serializable
data class UpdateProjectRequest(
    val name: String,
)

@Serializable
data class ProjectMemberResponse(
    val id: String,
    @SerialName("project_id")
    val projectId: String,
    @SerialName("user_id")
    val userId: String,
    val email: String,
    @SerialName("user_role")
    val userRole: String,
    @SerialName("member_role")
    val memberRole: String,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class ProjectMemberListResponse(
    val members: List<ProjectMemberResponse>,
    @SerialName("total")
    val total: Int,
)

@Serializable
data class ProjectLimitsResponse(
    val projectsUsed: Int,
    val projectsLimit: Int,
    val eventsUsed: Long,
    val eventsLimit: Long,
)
