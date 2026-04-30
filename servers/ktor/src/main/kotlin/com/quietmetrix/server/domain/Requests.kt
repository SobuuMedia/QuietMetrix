package com.quietmetrix.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class TrackEventRequest(
    val event: String,
    val screen: String? = null,
    val props: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val sid: String? = null,
    val ts: String? = null,
    val wasOffline: Boolean = false,
    val sdk: SdkInfo? = null,
    val ctx: EventContext? = null,
)

@Serializable
data class TrackBatchRequest(
    val events: List<TrackEventRequest>,
)

@Serializable
data class SdkInfo(
    val platform: String,
    val version: String,
)

@Serializable
data class EventContext(
    val referrer: String? = null,
    val language: String? = null,
    val ua: String? = null,
    val viewport: String? = null,
)

@Serializable
data class TrackEventResponse(
    val ok: Boolean = true,
    val queued: Int = 1,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserResponse,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val createdAt: String,
)

@Serializable
data class CreateProjectRequest(
    val name: String,
)

@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val writeKey: String,
    val readKey: String,
    val planId: String? = null,
    val createdAt: String,
)

@Serializable
data class ProjectListResponse(
    val projects: List<ProjectResponse>,
    val total: Int,
)