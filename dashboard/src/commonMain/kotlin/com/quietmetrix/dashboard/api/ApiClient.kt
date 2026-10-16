package com.quietmetrix.dashboard.api

import com.quietmetrix.dashboard.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Thin Ktor-based wrapper around the QuietMetrix HTTP API. The dashboard talks
 * to either the PHP backend or the Ktor backend on the `/api/v1/...` path —
 * both expose the same routes.
 *
 * Non-2xx responses are mapped to [ApiException] so callers can branch on the
 * server-side error tag instead of reading the underlying transport message.
 */
class ApiClient(private val baseUrl: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        // Don't auto-throw on 4xx/5xx — we surface them as ApiException ourselves.
        expectSuccess = false
    }

    private var sessionEpoch = 0L
    private var accessCredential: String? = null
    private var refreshCredential: String? = null
    var token: String?
        get() = accessCredential
        set(value) { if (accessCredential != value) sessionEpoch++; accessCredential = value }
    var refreshToken: String?
        get() = refreshCredential
        set(value) { if (refreshCredential != value) sessionEpoch++; refreshCredential = value }
    private val refreshMutex = Mutex()

    init {
        client.plugin(HttpSend).intercept { request ->
            val sentEpoch = sessionEpoch
            val sentToken = request.headers[HttpHeaders.Authorization]
            val first = execute(request)
            if (first.response.status != HttpStatusCode.Unauthorized || sentToken == null ||
                request.url.build().encodedPath.contains("/auth/")) return@intercept first
            val renewed = refreshMutex.withLock {
                if (sentEpoch != sessionEpoch) return@withLock false
                if (sentToken != "Bearer $token" && token != null) true else refresh()
            }
            if (!renewed) return@intercept first
            request.headers.remove(HttpHeaders.Authorization)
            request.headers.append(HttpHeaders.Authorization, "Bearer $token")
            execute(request)
        }
    }

    private fun url(path: String): String =
        baseUrl.trimEnd('/') + "/api/v1" + path

    private fun urlV2(path: String): String =
        baseUrl.trimEnd('/') + "/api/v2" + path

    private suspend fun ensureSuccess(res: HttpResponse) {
        if (res.status.isSuccess()) return
        val body = runCatching { res.body<ErrorBody>() }.getOrNull()
        throw ApiException(
            status = res.status.value,
            code = body?.error,
            serverMessage = body?.message,
        )
    }

    suspend fun meta(): MetaResponse {
        val res = client.get(url("/_meta"))
        ensureSuccess(res)
        return res.body()
    }

    suspend fun login(email: String, password: String): LoginResponse {
        val res = client.post(url("/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }
        ensureSuccess(res)
        val body: LoginResponse = res.body()
        token = body.token
        refreshToken = body.refreshToken
        return body
    }

    suspend fun revokeSession(refresh: String) {
        runCatching { client.post(url("/auth/logout")) { contentType(ContentType.Application.Json); setBody(RefreshRequest(refresh)) } }
    }

    suspend fun refresh(): Boolean {
        val epoch = sessionEpoch
        val rt = refreshToken ?: return false
        return try {
            val res = client.post(url("/auth/refresh")) {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(rt))
            }
            ensureSuccess(res)
            val body: RefreshResponse = res.body()
            if (epoch != sessionEpoch || refreshToken != rt) return false
            accessCredential = body.token
            refreshCredential = body.refreshToken
            TokenStorage.save(body.token)
            TokenStorage.saveRefresh(body.refreshToken)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listProjects(): ProjectsResponse {
        val res = client.get(url("/projects")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun createProject(name: String, description: String? = null, iconKey: String = "book", declaredPlatforms: List<String> = emptyList()): CreateProjectResponse {
        val res = client.post(url("/projects")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(CreateProjectRequest(name, description, iconKey, declaredPlatforms))
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun updateProject(projectId: String, name: String) {
        val res = client.patch(url("/projects/$projectId")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(UpdateProjectRequest(name))
        }
        ensureSuccess(res)
    }

    suspend fun deleteProject(projectId: String) {
        val res = client.delete(url("/projects/$projectId")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
    }

    suspend fun regenerateApiKey(projectId: String): RegenerateKeyResponse {
        val res = client.post(url("/projects/$projectId/regenerate-key")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    // ---- Access tokens (agents/CLIs) ----

    suspend fun listTokens(): AccessTokensResponse {
        val res = client.get(url("/tokens")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun createToken(name: String, scopes: List<String>? = null, expiresInDays: Long? = null): CreateTokenResponse {
        val res = client.post(url("/tokens")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(CreateTokenRequest(name, scopes, expiresInDays))
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun revokeToken(tokenId: String) {
        val res = client.delete(url("/tokens/$tokenId")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
    }

    // ---- User management (admin) ----

    suspend fun listUsers(): UsersResponse {
        val res = client.get(url("/users")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun recoverUser(userId: String): InviteResponse {
        val res = client.post(url("/users/$userId/recover")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun inviteUser(email: String, role: String, newOrganization: Boolean = false): InviteResponse {
        val res = client.post(url("/users/invite")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(InviteUserRequest(email, role, newOrganization))
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun updateUserRole(userId: String, role: String) {
        val res = client.patch(url("/users/$userId")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(UpdateRoleRequest(role))
        }
        ensureSuccess(res)
    }

    suspend fun deleteUser(userId: String) {
        val res = client.delete(url("/users/$userId")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
    }

    // ---- Invitation acceptance (public) ----

    suspend fun getInvite(inviteToken: String): InvitePreview {
        val res = client.get(url("/invites/$inviteToken"))
        ensureSuccess(res)
        return res.body()
    }

    suspend fun acceptInvite(inviteToken: String, password: String): LoginResponse {
        val res = client.post(url("/invites/$inviteToken/accept")) {
            contentType(ContentType.Application.Json)
            setBody(AcceptInviteRequest(password))
        }
        ensureSuccess(res)
        val body: LoginResponse = res.body()
        token = body.token
        refreshToken = body.refreshToken
        return body
    }

    // ---- Project membership (assign / unassign users) ----

    suspend fun listMembers(projectId: String): MembersResponse {
        val res = client.get(url("/projects/$projectId/members")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun addMember(projectId: String, email: String) {
        val res = client.post(url("/projects/$projectId/members")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(AddMemberRequest(email))
        }
        ensureSuccess(res)
    }

    suspend fun removeMember(projectId: String, userId: String) {
        val res = client.delete(url("/projects/$projectId/members/$userId")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
    }

    suspend fun aggregates(projectId: String, range: TimeRange, demo: Boolean, previous: Boolean = false, anchor: String? = null): AggregatesResponse {
        val path = if (demo) "/_demo/aggregates" else "/projects/$projectId/aggregates"
        val res = client.get(url("$path?range=${range.token}${analyticsWindowQuery(previous, anchor)}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun projectReport(projectId: String, range: TimeRange, anchor: String? = null, compare: Boolean = false, filterKind: String = "platform", filterValue: String = "all", trafficScope: String = "all"): ProjectReportDto {
        val reportRange = when (range) {
            TimeRange.Day1 -> "today"
            TimeRange.Day7 -> "7d"
            TimeRange.Day30 -> "30d"
            TimeRange.Day90 -> "90d"
            TimeRange.Hour1 -> "1h"
        }
        val query = buildString {
            append("range=").append(reportRange)
            anchor?.let { append("&anchor=").append(it) }
            append("&compare=").append(compare)
            append("&filter_kind=").append(filterKind.encodeURLParameter())
            append("&filter_value=").append(filterValue.encodeURLParameter())
            append("&traffic_scope=").append(trafficScope.encodeURLParameter())
        }
        val res = client.get(urlV2("/projects/$projectId/report?$query")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun transitions(projectId: String, range: TimeRange, previous: Boolean = false, anchor: String? = null, platform: String = "all"): TransitionsResponse {
        val res = client.get(url("/projects/$projectId/transitions?range=${range.token}${analyticsWindowQuery(previous, anchor)}&platform=$platform")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun sessions(projectId: String, range: TimeRange, previous: Boolean = false, anchor: String? = null): SessionsResponse {
        val res = client.get(url("/projects/$projectId/sessions?range=${range.token}${analyticsWindowQuery(previous, anchor)}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun retention(projectId: String, range: TimeRange, previous: Boolean = false, anchor: String? = null): RetentionResponse {
        val res = client.get(url("/projects/$projectId/retention?range=${range.token}${analyticsWindowQuery(previous, anchor)}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun search(projectId: String, range: TimeRange, previous: Boolean = false, anchor: String? = null): SearchResponse {
        val res = client.get(url("/projects/$projectId/search?range=${range.token}${analyticsWindowQuery(previous, anchor)}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun friction(projectId: String, range: TimeRange, previous: Boolean = false, anchor: String? = null): FrictionResponse {
        val res = client.get(url("/projects/$projectId/friction?range=${range.token}${analyticsWindowQuery(previous, anchor)}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    /** Requests a server-computed answer for a supported aggregate question. */
    suspend fun ask(projectId: String, question: String, range: TimeRange): AskDataResponse {
        val res = client.post(url("/projects/$projectId/ask")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(AskDataRequest(question, range.token))
        }
        ensureSuccess(res)
        return res.body()
    }

    /** Loads effective access before showing Ask controls. */
    suspend fun aiCapabilities(projectId: String): AiCapabilities {
        val res = client.get(url("/projects/$projectId/ai/capabilities")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    /** Sends bounded client-side history to the optional AI endpoint. */
    suspend fun aiAsk(projectId: String, request: AiRequest): AiAnswer {
        val res = client.post(url("/projects/$projectId/ai/ask")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(request)
        }
        ensureSuccess(res)
        return res.body()
    }

    /** Requests a free cached or computed summary, or one eligible generation. */
    suspend fun aiInsight(projectId: String, request: AiRequest): AiInsight {
        val res = client.post(url("/projects/$projectId/ai/insight")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(request)
        }
        ensureSuccess(res)
        return res.body()
    }

    // ---- Funnels ----

    suspend fun listFunnels(projectId: String): FunnelsResponse {
        val res = client.get(url("/projects/$projectId/funnels")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun createFunnel(projectId: String, request: CreateFunnelRequest): FunnelDto {
        val res = client.post(url("/projects/$projectId/funnels")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(request)
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun updateFunnel(projectId: String, funnelKey: String, request: UpdateFunnelRequest): FunnelDto {
        val res = client.patch(url("/projects/$projectId/funnels/$funnelKey")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(request)
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun deleteFunnel(projectId: String, funnelKey: String) {
        val res = client.delete(url("/projects/$projectId/funnels/$funnelKey")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
    }

    suspend fun funnelResults(
        projectId: String,
        funnelKey: String,
        range: TimeRange,
        breakdown: String? = null,
        revision: Int? = null,
        trend: Boolean = false,
        previous: Boolean = false,
        anchor: String? = null,
    ): FunnelResultsResponse {
        val query = buildString {
            append("?range=${range.token}${analyticsWindowQuery(previous, anchor)}")
            revision?.let { append("&revision=$it") }
            breakdown?.let { append("&breakdown=$it") }
            if (trend) append("&trend=1")
        }
        val res = client.get(url("/projects/$projectId/funnels/$funnelKey/results$query")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    // ---- Experiments (A/B testing) ----

    suspend fun listExperiments(projectId: String): ExperimentsResponse {
        val res = client.get(url("/projects/$projectId/experiments")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun createExperiment(projectId: String, request: CreateExperimentRequest): ExperimentDto {
        val res = client.post(url("/projects/$projectId/experiments")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(request)
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun updateExperiment(projectId: String, experimentKey: String, request: UpdateExperimentRequest): ExperimentDto {
        val res = client.patch(url("/projects/$projectId/experiments/$experimentKey")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(request)
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun deleteExperiment(projectId: String, experimentKey: String) {
        val res = client.delete(url("/projects/$projectId/experiments/$experimentKey")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
    }

    suspend fun experimentResults(projectId: String, experimentKey: String, range: TimeRange, previous: Boolean = false, anchor: String? = null): ExperimentResultsResponse {
        val res = client.get(url("/projects/$projectId/experiments/$experimentKey/results?range=${range.token}${analyticsWindowQuery(previous, anchor)}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }
}

/** Appends a stable UTC anchor and optional previous-period selector to an analytics request. */
internal fun analyticsWindowQuery(previous: Boolean, anchor: String?): String = buildString {
    if (previous) append("&period=previous")
    if (anchor != null) append("&anchor=$anchor")
}
