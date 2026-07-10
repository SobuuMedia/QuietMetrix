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
    @SerialName("strict_schema") val strictSchema: Boolean? = null,
    @SerialName("allowed_events") val allowedEvents: List<String>? = null,
)

@Serializable
data class ProjectMemberResponse(
    val id: String,
    val projectId: String,
    val userId: String,
    val email: String,
    val role: String,
    val createdAt: String,
)

@Serializable
data class ProjectMemberListResponse(
    val members: List<ProjectMemberResponse>,
    val total: Int,
)

@Serializable
data class ProjectLimitsResponse(
    val projectsUsed: Int,
    val projectsLimit: Int,
    val eventsUsed: Long,
    val eventsLimit: Long,
)
