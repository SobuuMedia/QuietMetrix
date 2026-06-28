package com.quietmetrix.dashboard.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: ApiUser,
)

@Serializable
data class ApiUser(val id: String, val email: String, val role: String = "admin")

/** Global user roles. Drives what the dashboard shows and allows. */
object UserRole {
    const val ADMIN = "admin"
    const val DEVELOPER = "developer"
    const val REVIEWER = "reviewer"
}

/** A user as shown in the admin Users screen. */
@Serializable
data class ApiManagedUser(
    val id: String,
    val email: String,
    val role: String = UserRole.REVIEWER,
    val status: String = "active",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class UsersResponse(val users: List<ApiManagedUser> = emptyList())

@Serializable
data class InviteUserRequest(val email: String, val role: String)

@Serializable
data class InviteResponse(
    @SerialName("invite_link") val inviteLink: String,
    val user: ApiManagedUser? = null,
)

@Serializable
data class InvitePreview(val email: String)

@Serializable
data class AcceptInviteRequest(val password: String)

@Serializable
data class UpdateRoleRequest(val role: String)

@Serializable
data class AddMemberRequest(val email: String)

/** A user assigned to a project (project_members join). */
@Serializable
data class ApiMember(
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("user_role") val userRole: String? = null,
)

@Serializable
data class MembersResponse(val members: List<ApiMember> = emptyList())

@Serializable
data class RegenerateKeyResponse(
    @SerialName("api_key") val apiKey: String,
    @SerialName("api_key_last4") val apiKeyLast4: String,
)

@Serializable
data class MetaResponse(val version: String = "0.0.0", val debug: Boolean = false)

@Serializable
data class ProjectsResponse(val projects: List<ApiProject> = emptyList())

@Serializable
data class ApiProject(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("api_key_last4") val apiKeyLast4: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateProjectRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class CreateProjectResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("api_key") val apiKey: String,
    @SerialName("api_key_last4") val apiKeyLast4: String? = null,
)

@Serializable
data class AggregatesResponse(
    val demo: Boolean = false,
    @SerialName("window_days") val windowDays: Int = 30,
    val totals: Totals = Totals(),
    @SerialName("top_events")  val topEvents:  List<TopEvent>  = emptyList(),
    @SerialName("top_screens") val topScreens: List<TopScreen> = emptyList(),
    val daily: List<DailyPoint> = emptyList(),
    val countries: List<BreakdownItem> = emptyList(),
    val platforms: List<BreakdownItem> = emptyList(),
    @SerialName("device_classes") val deviceClasses: List<BreakdownItem> = emptyList(),
)

@Serializable
data class BreakdownItem(val name: String? = null, val count: Long = 0) {
    val displayName: String get() = name ?: "(unknown)"
}

@Serializable
data class TransitionsResponse(
    val transitions: List<Transition> = emptyList(),
)

@Serializable
data class Transition(
    @SerialName("from_screen") val fromScreen: String,
    @SerialName("to_screen") val toScreen: String,
    val count: Long,
)

@Serializable
data class SessionsResponse(
    @SerialName("total_sessions") val totalSessions: Int = 0,
    @SerialName("avg_events") val avgEvents: Float = 0f,
    @SerialName("avg_duration_sec") val avgDurationSec: Int = 0,
    @SerialName("daily_sessions") val dailySessions: List<DailyPoint> = emptyList(),
)

@Serializable
data class RetentionResponse(
    val cohorts: List<RetentionCohort> = emptyList(),
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

@Serializable
data class Totals(val events: Long = 0, val offline: Long = 0, val errors: Long = 0)

@Serializable
data class TopEvent(@SerialName("event_name") val eventName: String, val count: Long)

@Serializable
data class TopScreen(val screen: String? = null, val count: Long)

@Serializable
data class DailyPoint(
    val day: String,
    val total: Long = 0,
    @SerialName("offline_total") val offlineTotal: Long = 0,
)

@Serializable
data class EventsResponse(
    val demo: Boolean = false,
    val events: List<EventRow> = emptyList(),
)

@Serializable
data class EventRow(
    val id: Long? = null,
    @SerialName("event_name")   val eventName:   String  = "",
    val screen: String? = null,
    @SerialName("session_id")   val sessionId:   String? = null,
    val ts: String = "",
    @SerialName("was_offline")  val wasOffline:  Boolean = false,
    val country: String? = null,
    @SerialName("device_class") val deviceClass: String? = null,
    val language:    String? = null,
    val platform:    String? = null,
    @SerialName("sdk_version")  val sdkVersion:  String? = null,
)
