package com.quietmetrix.dashboard.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class RefreshResponse(val token: String, @SerialName("refresh_token") val refreshToken: String)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: ApiUser,
)

@Serializable
data class ApiUser(val id: String, val email: String, val role: String = "admin", @SerialName("platform_operator") val platformOperator: Boolean = false)

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
data class InviteUserRequest(val email: String, val role: String, @SerialName("new_organization") val newOrganization: Boolean = false)

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
    @SerialName("member_role") val memberRole: String? = null,
)

@Serializable
data class MembersResponse(val members: List<ApiMember> = emptyList())

@Serializable
data class RegenerateKeyResponse(
    @SerialName("api_key") val apiKey: String,
    @SerialName("api_key_last4") val apiKeyLast4: String,
)

@Serializable
data class MetaResponse(val version: String = "0.0.0", val debug: Boolean = false, val ai_enabled: Boolean = false, val selfhost: Boolean = false)

/** A personal access token (`qm_pat_…`) for an agent/CLI — see docs/agents/setup.md. Never carries the plaintext token. */
@Serializable
data class AccessTokenDto(
    val id: String,
    val name: String,
    val last4: String,
    val scopes: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
)

@Serializable
data class AccessTokensResponse(val tokens: List<AccessTokenDto> = emptyList())

@Serializable
data class CreateTokenRequest(
    val name: String,
    val scopes: List<String>? = null,
    @SerialName("expires_in_days") val expiresInDays: Long? = null,
)

/** Response to POST /tokens. Carries the plaintext token once — never shown again. */
@Serializable
data class CreateTokenResponse(
    val id: String,
    val name: String,
    val token: String,
    val last4: String,
    val scopes: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class ProjectsResponse(val projects: List<ApiProject> = emptyList())

/** Natural-language question sent to the server's supported aggregate analyzer. */
@Serializable
data class AskDataRequest(val question: String, val range: String)

/** Server-computed answer and its aggregate source label. */
@Serializable
data class AskDataResponse(val answer: String, val source: String)

@Serializable
data class ApiProject(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("api_key_last4") val apiKeyLast4: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("owner_user_id") val ownerUserId: String? = null,
    @SerialName("last_received_at") val lastReceivedAt: String? = null,
    val platforms: List<String> = emptyList(),
    @SerialName("sdk_versions") val sdkVersions: List<String> = emptyList(),
    @SerialName("activity_status") val activityStatus: String = "waiting_for_first_data",
    @SerialName("icon_key") val iconKey: String = "book",
    @SerialName("declared_platforms") val declaredPlatforms: List<String> = emptyList(),
)

@Serializable
data class CreateProjectRequest(
    val name: String,
    val description: String? = null,
    @SerialName("icon_key") val iconKey: String = "book",
    @SerialName("declared_platforms") val declaredPlatforms: List<String> = emptyList(),
)

@Serializable
data class UpdateProjectRequest(val name: String)

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
    /** Distinct reported app installations for one exact fixed window; null when unavailable/suppressed. */
    val people: Long? = null,
    @SerialName("people_state") val peopleState: String? = null,
    @SerialName("people_source") val peopleSource: String? = null,
    @SerialName("people_comparison") val peopleComparison: Long? = null,
    @SerialName("v2_visits") val v2Visits: Long? = null,
    @SerialName("v2_visit_duration_ms") val v2VisitDurationMs: Long? = null,
    @SerialName("v2_visit_duration_state") val v2VisitDurationState: String? = null,
    @SerialName("v2_crashes") val v2Crashes: Long? = null,
    @SerialName("v2_crashes_state") val v2CrashesState: String? = null,
    /** Privacy-gated daily/hourly distinct-installation chart bins. */
    @SerialName("daily_people") val dailyPeople: List<DailyPoint> = emptyList(),
    /** Prior-period people bins returned with a report comparison. */
    @SerialName("daily_people_previous") val dailyPeoplePrevious: List<DailyPoint> = emptyList(),
    @SerialName("daily_visits_v2") val dailyVisitsV2: List<DailyPoint> = emptyList(),
    @SerialName("daily_visits_v2_previous") val dailyVisitsV2Previous: List<DailyPoint> = emptyList(),
    @SerialName("daily_actions_v2") val dailyActionsV2: List<DailyPoint> = emptyList(),
    @SerialName("daily_actions_v2_previous") val dailyActionsV2Previous: List<DailyPoint> = emptyList(),
    val totals: Totals = Totals(),
    @SerialName("top_events")  val topEvents:  List<TopEvent>  = emptyList(),
    @SerialName("top_screens") val topScreens: List<TopScreen> = emptyList(),
    @SerialName("screen_durations") val screenDurations: List<ScreenDuration> = emptyList(),
    @SerialName("top_values") val topValues: List<TopValue> = emptyList(),
    val daily: List<DailyPoint> = emptyList(),
    val countries: List<BreakdownItem> = emptyList(),
    val platforms: List<BreakdownItem> = emptyList(),
    @SerialName("device_classes") val deviceClasses: List<BreakdownItem> = emptyList(),
)

@Serializable
data class ReportMeasureDto(
    val value: Long? = null,
    val state: String,
    val unit: String,
    val source: String,
    val contributors: Long? = null,
    val rate: Double? = null,
)

@Serializable
data class ReportCoverageDto(
    val source: String = "v2",
    val resolution: String = "daily",
    @SerialName("oldest_day") val oldestDay: String? = null,
    @SerialName("newest_day") val newestDay: String? = null,
    @SerialName("sdk_versions") val sdkVersions: List<String> = emptyList(),
    @SerialName("legacy_present") val legacyPresent: Boolean = false,
)

@Serializable
data class ReportBinDto(
    @SerialName("start_day") val startDay: String,
    @SerialName("end_day") val endDay: String,
    val hour: Int? = null,
    val measures: Map<String, ReportMeasureDto> = emptyMap(),
)

@Serializable
data class ProjectReportDto(
    val scope: Map<String, String> = emptyMap(),
    @SerialName("generated_at") val generatedAt: String = "",
    @SerialName("last_received_at") val lastReceivedAt: String? = null,
    val coverage: ReportCoverageDto = ReportCoverageDto(),
    val overview: Map<String, ReportMeasureDto> = emptyMap(),
    val charts: Map<String, List<ReportBinDto>> = emptyMap(),
    @SerialName("comparison_charts") val comparisonCharts: Map<String, List<ReportBinDto>> = emptyMap(),
    val comparison: Map<String, ReportMeasureDto> = emptyMap(),
    val flow: Map<String, ReportMeasureDto> = emptyMap(),
    val retention: Map<String, ReportMeasureDto> = emptyMap(),
    @SerialName("retention_cohorts") val retentionCohorts: List<ReportRetentionCohortDto> = emptyList(),
    @SerialName("comparison_retention_cohorts") val comparisonRetentionCohorts: List<ReportRetentionCohortDto> = emptyList(),
    val definitions: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class ReportRetentionCohortDto(
    @SerialName("entry_day") val entryDay: String,
    val size: ReportMeasureDto,
    val days: Map<String, ReportMeasureDto> = emptyMap(),
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
data class SearchResponse(
    val screens: List<SearchScreenItem> = emptyList(),
)

/** The search query itself is never sent — see the SDK's `trackSearch` — only whether it came
 *  back empty. [rate] is null when [total] is 0, never a fake 0.0. */
@Serializable
data class SearchScreenItem(
    val screen: String,
    val total: Long = 0,
    @SerialName("zero_result") val zeroResult: Long = 0,
    val rate: Double? = null,
)

@Serializable
data class FrictionResponse(
    val screens: List<FrictionScreenItem> = emptyList(),
)

/** [rageTaps] is an absolute count of rage-tap bursts, not a per-view rate. Captured
 *  automatically on Android; requires a one-line integration on iOS ([QuietMetrixWindow]);
 *  no signal at all from JVM/Linux/Windows/Web — an honest platform gap. */
@Serializable
data class FrictionScreenItem(
    val screen: String,
    @SerialName("rage_taps") val rageTaps: Long = 0,
)

@Serializable
data class SessionsResponse(
    @SerialName("total_sessions") val totalSessions: Int = 0,
    @SerialName("avg_duration_sec") val avgDurationSec: Int = 0,
    @SerialName("daily_sessions") val dailySessions: List<DailyPoint> = emptyList(),
)

@Serializable
data class RetentionResponse(
    val cohorts: List<RetentionCohort> = emptyList(),
    val source: String = "legacy",
)

/** [activationRate] is null when the project's app hasn't configured an activation event —
 *  not zero, which would claim a real 0% rather than "not tracked". */
@Serializable
data class RetentionCohort(
    @SerialName("cohort_date") val cohortDate: String,
    val size: Int,
    val day1: Double? = null,
    val day3: Double? = null,
    val day7: Double? = null,
    val day14: Double? = null,
    val day30: Double? = null,
    @SerialName("activation_rate") val activationRate: Double? = null,
    @SerialName("day_states") val dayStates: Map<String, String> = emptyMap(),
    @SerialName("size_state") val sizeState: String = "reported",
)

@Serializable
data class Totals(val events: Long = 0, val offline: Long = 0, val errors: Long = 0)

@Serializable
data class TopEvent(@SerialName("event_name") val eventName: String, val count: Long)

/** [totalMinorUnits] is a summed amount (e.g. cents), not an occurrence count. */
@Serializable
data class TopValue(val name: String, @SerialName("total_minor_units") val totalMinorUnits: Long = 0)

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

// ---- Experiments (A/B testing) ----

/** One variant's display name and its share (1-99) of enrolled devices; also used, unchanged,
 *  as a variant's name+weight wherever the server's wire shape reuses it (see
 *  servers/ktor/.../experiments/ExperimentModels.kt's VariantSpec, which this mirrors). */
@Serializable
data class VariantDto(val name: String, val weight: Int)

@Serializable
data class ExperimentDto(
    val key: String,
    val name: String,
    val screen: String? = null,
    val status: String = "draft",
    @SerialName("traffic_percent") val trafficPercent: Int = 100,
    val variants: List<VariantDto> = emptyList(),
    val countries: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("selected_variant") val selectedVariant: String? = null,
    val mode: String = "legacy",
    @SerialName("placement_key") val placementKey: String? = null,
    val revision: Int = 0,
    @SerialName("b_percent") val bPercent: Int? = null,
    val languages: List<String>? = null,
    @SerialName("match_mode") val matchMode: String = "all",
    @SerialName("goal_event") val goalEvent: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
)

/** `limit` is null when the project's plan has no cap on experiments (paid plan, or self-host). */
@Serializable
data class ExperimentsResponse(val experiments: List<ExperimentDto> = emptyList(), val limit: Int? = null)

@Serializable
data class CreateExperimentRequest(
    val key: String,
    val name: String,
    val screen: String? = null,
    @SerialName("traffic_percent") val trafficPercent: Int? = null,
    val variants: List<VariantDto>? = null,
    val countries: List<String>? = null,
    val mode: String? = null,
    @SerialName("placement_key") val placementKey: String? = null,
    @SerialName("b_percent") val bPercent: Int? = null,
    val languages: List<String>? = null,
    @SerialName("match_mode") val matchMode: String? = null,
    @SerialName("goal_event") val goalEvent: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
)

@Serializable
data class UpdateExperimentRequest(
    val name: String? = null,
    val screen: String? = null,
    @SerialName("traffic_percent") val trafficPercent: Int? = null,
    val status: String? = null,
    @SerialName("selected_variant") val selectedVariant: String? = null,
    val variants: List<VariantDto>? = null,
    val countries: List<String>? = null,
    val mode: String? = null,
    @SerialName("placement_key") val placementKey: String? = null,
    @SerialName("b_percent") val bPercent: Int? = null,
    val languages: List<String>? = null,
    @SerialName("match_mode") val matchMode: String? = null,
    @SerialName("goal_event") val goalEvent: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
)

@Serializable
data class ExperimentRangeDto(val from: String = "", val to: String = "")

@Serializable
data class ExperimentVariantResultDto(
    val name: String,
    val impressions: Long? = null,
    val interactions: Long? = null,
    val rate: Double? = null,
)

@Serializable
data class ExperimentComparisonDto(
    val baseline: String,
    val challenger: String,
    val z: Double,
    @SerialName("p_value") val pValue: Double,
    val significant: Boolean,
    val winner: String? = null,
)

@Serializable
data class ExperimentResultsResponse(
    val experiment: ExperimentDto,
    val range: ExperimentRangeDto = ExperimentRangeDto(),
    val unit: String = "sessions",
    val basis: String = "selected_range",
    val variants: List<ExperimentVariantResultDto> = emptyList(),
    val comparison: ExperimentComparisonDto? = null,
)

/** Effective project entitlement contains no provider or consumption information. */
@Serializable
data class AiCapabilities(val enabled: Boolean = false, val available: Boolean = false, val reason: String? = null, val reset_date: String = "")

/** AI conversation history is bounded and stays client-side between requests. */
@Serializable
data class AiHistory(val role: String, val content: String)

/** AI requests use the same UTC anchor and comparison as analytics. */
@Serializable
data class AiRequest(val question: String = "", val language: String = "en", val range: String = "7d", val anchor: String? = null,
    val compare: Boolean = false, val history: List<AiHistory> = emptyList(), val operation_id: String)

/** Safe text-only AI response with aggregate source labels. */
@Serializable
data class AiAnswer(val answer: String, val sources: List<String> = emptyList())

/** Summary mode distinguishes computed text from generated text. */
@Serializable
data class AiInsight(val text: String, val mode: String, val sources: List<String> = emptyList(), val generated_at: String)
