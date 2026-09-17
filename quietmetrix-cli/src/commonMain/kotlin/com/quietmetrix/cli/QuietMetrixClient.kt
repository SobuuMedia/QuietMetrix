package com.quietmetrix.cli

import com.quietmetrix.cli.http.HttpClient
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/** Outcome of a network operation against a QuietMetrix server — status-classified, not exceptions. */
sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class AuthError(val message: String) : Outcome<Nothing>()
    data class ServerError(val message: String) : Outcome<Nothing>()
}

/**
 * The network operations behind "create a QuietMetrix project and wire it into this app"
 * (docs/agents/setup.md) — shared by the CLI's argv-driven commands and the MCP server's
 * tool handlers, so the two entry points can never drift on request/response handling.
 * All network access goes through [http], so this is testable with a fake.
 */
class QuietMetrixClient(private val http: HttpClient) {

    suspend fun whoami(url: String): Outcome<String> {
        return try {
            val res = http.get("$url/api/v1/_meta")
            if (res.status in 200..299) Outcome.Success(res.body)
            else Outcome.ServerError("server returned ${res.status} for $url")
        } catch (e: Throwable) {
            Outcome.ServerError("could not reach $url (${e.message})")
        }
    }

    suspend fun createProject(
        url: String,
        token: String,
        name: String,
        description: String? = null,
        idempotencyKey: String? = null,
    ): Outcome<ProjectCreateOutput> {
        val bodyMap = buildMap {
            put("name", name)
            description?.let { put("description", it) }
        }
        val requestBody = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), bodyMap)
        val headers = buildMap {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer $token")
            idempotencyKey?.let { put("Idempotency-Key", it) }
        }

        return try {
            val res = http.post("$url/api/v1/projects", headers, requestBody)
            when {
                res.status == 201 || res.status == 200 -> {
                    val parsed = runCatching { json.decodeFromString<CreateProjectApiResponse>(res.body) }
                        .getOrElse { CreateProjectApiResponse() }
                    Outcome.Success(
                        ProjectCreateOutput(
                            id = parsed.id,
                            name = parsed.name ?: name,
                            apiKey = parsed.apiKey,
                            apiKeyLast4 = parsed.apiKeyLast4,
                            trackingEndpoint = trackingEndpoint(url),
                            message = parsed.message,
                        )
                    )
                }
                res.status == 401 || res.status == 403 -> Outcome.AuthError("${res.status} — ${res.body}")
                else -> Outcome.ServerError("server returned ${res.status} — ${res.body}")
            }
        } catch (e: Throwable) {
            Outcome.ServerError("could not reach $url (${e.message})")
        }
    }

    suspend fun listProjects(url: String, token: String): Outcome<String> =
        authenticatedGet("$url/api/v1/projects", token)

    /** Top events, screens, DAU, and country/platform/device breakdowns for [range] (default "this week"). */
    suspend fun getAggregates(url: String, token: String, projectId: String, range: String = RangeToken.DEFAULT): Outcome<String> =
        authenticatedGet(RangeToken.appendTo("$url/api/v1/projects/$projectId/aggregates", RangeToken.resolve(range)), token)

    /** Screen-to-screen navigation flow counts for [range]. */
    suspend fun getTransitions(url: String, token: String, projectId: String, range: String = RangeToken.DEFAULT): Outcome<String> =
        authenticatedGet(RangeToken.appendTo("$url/api/v1/projects/$projectId/transitions", RangeToken.resolve(range)), token)

    /** Session counts and average duration for [range]. */
    suspend fun getSessions(url: String, token: String, projectId: String, range: String = RangeToken.DEFAULT): Outcome<String> =
        authenticatedGet(RangeToken.appendTo("$url/api/v1/projects/$projectId/sessions", RangeToken.resolve(range)), token)

    /** Cohort retention (day1/3/7/14/30) for [range]. */
    suspend fun getRetention(url: String, token: String, projectId: String, range: String = RangeToken.DEFAULT): Outcome<String> =
        authenticatedGet(RangeToken.appendTo("$url/api/v1/projects/$projectId/retention", RangeToken.resolve(range)), token)

    /** Active (non-archived) funnels defined for [projectId]. */
    suspend fun listFunnels(url: String, token: String, projectId: String): Outcome<String> =
        authenticatedGet("$url/api/v1/projects/$projectId/funnels", token)

    /** Step-by-step conversion/drop-off for the funnel [funnelKey] over [range]. */
    suspend fun getFunnelResults(url: String, token: String, projectId: String, funnelKey: String, range: String = RangeToken.DEFAULT): Outcome<String> =
        authenticatedGet(RangeToken.appendTo("$url/api/v1/projects/$projectId/funnels/$funnelKey/results", RangeToken.resolve(range)), token)

    /** Shared GET-with-Bearer-token plumbing: every analytics read maps status the same way. */
    private suspend fun authenticatedGet(url: String, token: String): Outcome<String> {
        return try {
            val res = http.get(url, mapOf("Authorization" to "Bearer $token"))
            when {
                res.status in 200..299 -> Outcome.Success(res.body)
                res.status == 401 || res.status == 403 -> Outcome.AuthError("${res.status} — ${res.body}")
                else -> Outcome.ServerError("server returned ${res.status} — ${res.body}")
            }
        } catch (e: Throwable) {
            Outcome.ServerError("could not reach $url (${e.message})")
        }
    }
}
