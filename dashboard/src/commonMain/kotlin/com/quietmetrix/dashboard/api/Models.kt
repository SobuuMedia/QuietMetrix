package com.quietmetrix.dashboard.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    @SerialName("screen_durations") val screenDurations: List<ScreenDuration> = emptyList(),
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
    @SerialName("from_screen") val fromScreen: String? = null,
    @SerialName("to_screen") val toScreen: String? = null,
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
data class ScreenDuration(
    val screen: String? = null,
    val count: Long = 0,
    @SerialName("avg_ms") val avgMs: Long = 0,
    @SerialName("total_ms") val totalMs: Long = 0,
)

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
data class FunnelStepDto(
    val key: String,
    val event: String,
    val name: String? = null,
    val screen: String? = null,
    @Serializable(with = LenientStringMapSerializer::class)
    val props: Map<String, String> = emptyMap(),
)

@Serializable
data class FunnelDto(
    @SerialName("funnel_key") val funnelKey: String,
    val name: String,
    val description: String? = null,
    val steps: List<FunnelStepDto> = emptyList(),
    @SerialName("window_seconds") val windowSeconds: Long = 604_800,
    val source: String = "dashboard",
    val locked: Boolean = false,
    @SerialName("count_mode") val countMode: String = "actor",
    @SerialName("identity_scope") val identityScope: String = "install_or_session",
    @SerialName("correlation_property") val correlationProperty: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class FunnelsResponse(val funnels: List<FunnelDto> = emptyList())

@Serializable
data class CreateFunnelRequest(
    @SerialName("funnel_key") val funnelKey: String,
    val name: String,
    val description: String? = null,
    val steps: List<FunnelStepDto> = emptyList(),
    @SerialName("window_seconds") val windowSeconds: Long = 604_800,
)

@Serializable
data class UpdateFunnelRequest(
    val name: String? = null,
    val description: String? = null,
    val steps: List<FunnelStepDto>? = null,
    @SerialName("window_seconds") val windowSeconds: Long? = null,
)

@Serializable
data class FunnelStepResultDto(
    val key: String,
    val name: String? = null,
    val count: Int = 0,
    @SerialName("conversion_from_entry") val conversionFromEntry: Double = 0.0,
    @SerialName("conversion_from_previous") val conversionFromPrevious: Double = 0.0,
    val dropped: Int = 0,
    @SerialName("drop_rate") val dropRate: Double = 0.0,
    @SerialName("median_ms_from_previous") val medianMsFromPrevious: Long? = null,
    @SerialName("p90_ms_from_previous") val p90MsFromPrevious: Long? = null,
)

@Serializable
data class FunnelBreakdownValueDto(
    val value: String,
    val entered: Int = 0,
    @SerialName("overall_conversion") val overallConversion: Double = 0.0,
    val steps: List<FunnelStepResultDto> = emptyList(),
)

@Serializable
data class FunnelBreakdownDto(
    val dimension: String,
    val values: List<FunnelBreakdownValueDto> = emptyList(),
)

@Serializable
data class FunnelTrendPointDto(
    val bucket: String,
    val entered: Int = 0,
    val converted: Int = 0,
    val conversion: Double = 0.0,
)

@Serializable
data class FunnelSummaryDto(
    @SerialName("funnel_key") val funnelKey: String,
    val name: String,
    @SerialName("window_seconds") val windowSeconds: Long = 604_800,
    val steps: List<FunnelStepDto> = emptyList(),
    @SerialName("count_mode") val countMode: String = "actor",
    @SerialName("identity_scope") val identityScope: String = "install_or_session",
    @SerialName("correlation_property") val correlationProperty: String? = null,
)

@Serializable
data class FunnelRangeDto(val from: String = "", val to: String = "")

@Serializable
data class FunnelResultsResponse(
    val funnel: FunnelSummaryDto,
    val range: FunnelRangeDto = FunnelRangeDto(),
    @SerialName("counted_by") val countedBy: String = "install",
    val entered: Int = 0,
    val converted: Int = 0,
    @SerialName("overall_conversion") val overallConversion: Double = 0.0,
    @SerialName("median_total_ms") val medianTotalMs: Long? = null,
    val steps: List<FunnelStepResultDto> = emptyList(),
    val breakdown: FunnelBreakdownDto? = null,
    val trend: List<FunnelTrendPointDto>? = null,
    val truncated: Boolean = false,
)

@Serializable
data class EventRow(
    val id: Long? = null,
    @SerialName("event_name")   val eventName:   String  = "",
    val screen: String? = null,
    @SerialName("session_id")   val sessionId:   String? = null,
    @SerialName("duration_ms")  val durationMs:  Long?   = null,
    val ts: String = "",
    @SerialName("was_offline")  val wasOffline:  Boolean = false,
    val country: String? = null,
    @SerialName("device_class") val deviceClass: String? = null,
    val language:    String? = null,
    val platform:    String? = null,
    @SerialName("sdk_version")  val sdkVersion:  String? = null,
    val props:       JsonElement? = null,
)
