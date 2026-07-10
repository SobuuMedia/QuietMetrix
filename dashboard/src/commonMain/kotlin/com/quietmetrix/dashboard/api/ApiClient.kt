package com.quietmetrix.dashboard.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
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

    var token: String? = null
    var refreshToken: String? = null

    private fun url(path: String): String =
        baseUrl.trimEnd('/') + "/api/v1" + path

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

    suspend fun refresh(): Boolean {
        val rt = refreshToken ?: return false
        return try {
            val res = client.post(url("/auth/refresh")) {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(rt))
            }
            ensureSuccess(res)
            val body: LoginResponse = res.body()
            token = body.token
            refreshToken = body.refreshToken
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

    suspend fun createProject(name: String, description: String? = null): CreateProjectResponse {
        val res = client.post(url("/projects")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(CreateProjectRequest(name, description))
        }
        ensureSuccess(res)
        return res.body()
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

    // ---- User management (admin) ----

    suspend fun listUsers(): UsersResponse {
        val res = client.get(url("/users")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun inviteUser(email: String, role: String): InviteResponse {
        val res = client.post(url("/users/invite")) {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(InviteUserRequest(email, role))
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

    suspend fun aggregates(projectId: String, range: TimeRange, demo: Boolean): AggregatesResponse {
        val path = if (demo) "/_demo/aggregates" else "/projects/$projectId/aggregates"
        val res = client.get(url("$path?range=${range.token}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun events(projectId: String, demo: Boolean): EventsResponse {
        val path = if (demo) "/_demo/events" else "/projects/$projectId/events?limit=100"
        val res = client.get(url(path)) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun transitions(projectId: String, range: TimeRange): TransitionsResponse {
        val res = client.get(url("/projects/$projectId/transitions?range=${range.token}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun sessions(projectId: String, range: TimeRange): SessionsResponse {
        val res = client.get(url("/projects/$projectId/sessions?range=${range.token}")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }

    suspend fun retention(projectId: String, days: Int): RetentionResponse {
        val res = client.get(url("/projects/$projectId/retention?days=$days")) {
            token?.let { header("Authorization", "Bearer $it") }
        }
        ensureSuccess(res)
        return res.body()
    }
}
