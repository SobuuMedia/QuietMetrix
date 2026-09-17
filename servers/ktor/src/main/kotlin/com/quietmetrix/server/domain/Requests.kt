package com.quietmetrix.server.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SdkInfo(
    val platform: String,
    val version: String,
)

/**
 * One counter delta on the wire: `metric` + `dims` identify the cell, `n` is the occurrence
 * count since the last flush, and `u` is 1 only on the first flush that touched this exact
 * cell today (the SDK's own signal, summed server-side into a distinct-device count with no
 * identifier ever existing — see [com.quietmetrix.server.persistence.CounterRepository]).
 */
@Serializable
data class CounterItem(
    val m: String,
    val d: Map<String, String> = emptyMap(),
    val n: Long,
    val u: Int = 0,
)

@Serializable
data class CounterAppInfo(
    val version: String? = null,
    val country: String? = null,
)

@Serializable
data class CounterBatchRequest(
    val sdk: SdkInfo? = null,
    val app: CounterAppInfo? = null,
    /** The device's local date (ISO `yyyy-MM-dd`); clamped server-side to `[today-2, today]`. */
    val day: String,
    val counters: List<CounterItem>,
)

@Serializable
data class CounterBatchResponse(
    val ok: Boolean = true,
    val accepted: Int,
    val quarantined: Int = 0,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

@Serializable
data class HealthResponse(
    val ok: Boolean = true,
    val version: String,
)

/** Public, secret-free server flags — lets a client discover `debug` before doing anything else. */
@Serializable
data class MetaResponse(
    val version: String = "0.2.0",
    val debug: Boolean = false,
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
data class SearchResponse(
    val screens: List<SearchScreenItem>,
)

/** The search query itself is never sent — see the SDK's `trackSearch` — only whether it came
 *  back empty. [rate] is null when [total] is 0, never a fake 0.0. */
@Serializable
data class SearchScreenItem(
    val screen: String,
    val total: Int,
    @SerialName("zero_result") val zeroResult: Int,
    val rate: Double? = null,
)

@Serializable
data class FrictionResponse(
    val screens: List<FrictionScreenItem>,
)

@Serializable
data class FrictionScreenItem(
    val screen: String,
    @SerialName("rage_taps") val rageTaps: Int,
)

/** [avgEvents] is not included: no counter ties an event count to a session under
 *  aggregate-only ingest, so it would be a permanently-fake number rather than an honest gap. */
@Serializable
data class SessionsResponse(
    @SerialName("total_sessions") val totalSessions: Int,
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

/** [activationRate] is null when the project's app hasn't configured
 *  [com.quietmetrix.analytics.QuietMetrixConfig.activationEvent] — not zero, which would claim
 *  a real 0% rather than "not tracked". */
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
)

@Serializable
data class CreateTokenRequest(
    val name: String,
    val scopes: List<String>? = null,
    @SerialName("expires_in_days") val expiresInDays: Long? = null,
)

@Serializable
data class TokenSummary(
    val id: String,
    val name: String,
    val last4: String,
    val scopes: List<String>,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
)

@Serializable
data class CreateTokenResponse(
    val id: String,
    val name: String,
    val token: String,
    val last4: String,
    val scopes: List<String>,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class TokenListResponse(
    val tokens: List<TokenSummary>,
)