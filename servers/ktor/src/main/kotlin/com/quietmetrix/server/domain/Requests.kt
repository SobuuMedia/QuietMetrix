package com.quietmetrix.server.domain

import kotlinx.serialization.SerialName
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
    val anonymousId: String? = null,
    val country: String? = null,
    val sessionNumber: Int? = null,
    val isSessionStart: Boolean? = null,
    val isSessionEnd: Boolean? = null,
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
    @SerialName("refresh_token") val refreshToken: String,
    val user: UserResponse,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val role: String = "admin",
    val createdAt: String,
)

@Serializable
data class CreateProjectRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class UserListItem(
    val id: String,
    val email: String,
    val role: String,
    val status: String,
)

@Serializable
data class UserListResponse(
    val users: List<UserListItem>,
)

@Serializable
data class InviteUserRequest(
    val email: String,
    val role: String,
)

@Serializable
data class InviteResponse(
    @SerialName("invite_link") val inviteLink: String,
    val user: UserListItem,
)

@Serializable
data class InvitePreviewResponse(
    val email: String,
)

@Serializable
data class AcceptInviteRequest(
    val password: String,
)

@Serializable
data class UpdateUserRoleRequest(
    val role: String,
)

@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    val apiKey: String,
    @SerialName("api_key_last4") val apiKeyLast4: String? = null,
    val planId: String? = null,
    val createdAt: String,
)

@Serializable
data class RegenerateKeyResponse(
    @SerialName("api_key") val apiKey: String,
    @SerialName("api_key_last4") val apiKeyLast4: String,
)

@Serializable
data class ProjectListResponse(
    val projects: List<ProjectResponse>,
    val total: Int,
)

@Serializable
data class TransitionsResponse(
    val transitions: List<TransitionItem>,
)

@Serializable
data class TransitionItem(
    @SerialName("from_screen") val fromScreen: String,
    @SerialName("to_screen") val toScreen: String,
    val count: Int,
)

@Serializable
data class SessionsResponse(
    @SerialName("total_sessions") val totalSessions: Int,
    @SerialName("avg_events") val avgEvents: Double,
    @SerialName("avg_duration_sec") val avgDurationSec: Int,
    @SerialName("daily_sessions") val dailySessions: List<DailySessionItem>,
)

@Serializable
data class DailySessionItem(
    val day: String,
    val total: Int,
)

@Serializable
data class RetentionResponse(
    val cohorts: List<RetentionCohort>,
)

@Serializable
data class RetentionCohort(
    @SerialName("cohort_date") val cohortDate: String,
    val size: Int,
    val day1: Double? = null,
    val day3: Double? = null,
    val day7: Double? = null,
    val day14: Double? = null,
    val day30: Double? = null,
)