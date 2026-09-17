package com.quietmetrix.mcp

import com.quietmetrix.cli.Outcome
import com.quietmetrix.cli.ProjectCreateOutput
import com.quietmetrix.cli.QuietMetrixClient
import com.quietmetrix.cli.RangeToken
import com.quietmetrix.cli.normalizeBaseUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private const val PROTOCOL_VERSION = "2024-11-05"
private const val SERVER_NAME = "quietmetrix"
private const val SERVER_VERSION = "0.1.0"

/**
 * A minimal MCP server over stdio (newline-delimited JSON-RPC 2.0) exposing QuietMetrix
 * project provisioning as tools — the machinery behind `docs/agents/setup.md`'s "create a
 * QuietMetrix project and wire it into this app" flow, for agents that speak MCP instead of
 * shelling out to the CLI. [client] does the actual network work — the same
 * [QuietMetrixClient] the CLI uses — so this class never re-implements request/response
 * handling; it only reshapes results into MCP's `content` shape. [env] supplies
 * QUIETMETRIX_URL / QUIETMETRIX_TOKEN, read once per call so tests can inject a fake
 * environment without touching the real process.
 */
class McpServer(private val client: QuietMetrixClient, private val env: Map<String, String>) {

    /** Handles one JSON-RPC line. Returns the response line to write, or null for a notification. */
    suspend fun handle(line: String): String? {
        val request = try {
            json.parseToJsonElement(line).jsonObject
        } catch (e: Exception) {
            return errorResponse(JsonNull, -32700, "Parse error: ${e.message}")
        }

        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.contentOrNull
            ?: return errorResponse(id ?: JsonNull, -32600, "Invalid Request: missing method")

        // A request with no "id" is a notification (e.g. notifications/initialized) — no
        // response is sent, per JSON-RPC 2.0.
        if (id == null) return null

        return when (method) {
            "initialize" -> successResponse(id, initializeResult())
            "tools/list" -> successResponse(id, toolsListResult())
            "tools/call" -> successResponse(id, handleToolsCall(request["params"] as? JsonObject))
            else -> errorResponse(id, -32601, "Method not found: $method")
        }
    }

    private fun initializeResult(): JsonObject = buildJsonObject {
        put("protocolVersion", PROTOCOL_VERSION)
        putJsonObject("capabilities") { putJsonObject("tools") {} }
        putJsonObject("serverInfo") {
            put("name", SERVER_NAME)
            put("version", SERVER_VERSION)
        }
    }

    private fun toolsListResult(): JsonObject = buildJsonObject {
        putJsonArray("tools") {
            add(createProjectToolSchema())
            add(listProjectsToolSchema())
            add(analyticsToolSchema("quietmetrix_get_aggregates", "Top events, top screens, daily active users/events, and country/platform/device breakdowns for a project over a time range.", withRange = true))
            add(analyticsToolSchema("quietmetrix_get_transitions", "Screen-to-screen navigation flow counts for a project over a time range.", withRange = true))
            add(analyticsToolSchema("quietmetrix_get_sessions", "Session counts and average duration for a project over a time range.", withRange = true))
            add(analyticsToolSchema("quietmetrix_get_retention", "Cohort retention (day 1/3/7/14/30 return rate) for a project over a time range.", withRange = true))
            add(analyticsToolSchema("quietmetrix_list_funnels", "Lists the funnels defined for a project (funnel_key, name, steps) — call this first to discover a funnel_key before quietmetrix_get_funnel_results.", withRange = false))
            add(funnelResultsToolSchema())
        }
    }

    private suspend fun handleToolsCall(params: JsonObject?): JsonObject {
        val name = params?.get("name")?.jsonPrimitive?.contentOrNull
            ?: return toolError("Invalid params: missing tool name")
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())

        return when (name) {
            "quietmetrix_create_project" -> callCreateProject(arguments)
            "quietmetrix_list_projects" -> callListProjects(arguments)
            "quietmetrix_get_aggregates" -> callProjectRead(arguments) { url, token, projectId, range -> client.getAggregates(url, token, projectId, range) }
            "quietmetrix_get_transitions" -> callProjectRead(arguments) { url, token, projectId, range -> client.getTransitions(url, token, projectId, range) }
            "quietmetrix_get_sessions" -> callProjectRead(arguments) { url, token, projectId, range -> client.getSessions(url, token, projectId, range) }
            "quietmetrix_get_retention" -> callProjectRead(arguments) { url, token, projectId, range -> client.getRetention(url, token, projectId, range) }
            "quietmetrix_list_funnels" -> callProjectRead(arguments) { url, token, projectId, _ -> client.listFunnels(url, token, projectId) }
            "quietmetrix_get_funnel_results" -> callFunnelResults(arguments)
            else -> toolError("Unknown tool: $name")
        }
    }

    private fun resolveUrl(arguments: JsonObject): String? =
        (arguments["url"]?.jsonPrimitive?.contentOrNull ?: env["QUIETMETRIX_URL"])
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizeBaseUrl(it) }

    private fun resolveToken(): String? = env["QUIETMETRIX_TOKEN"]?.takeIf { it.isNotBlank() }

    private suspend fun callCreateProject(arguments: JsonObject): JsonObject {
        val url = resolveUrl(arguments)
            ?: return toolError("Missing 'url' argument (and QUIETMETRIX_URL is not set in this server's environment).")
        val token = resolveToken()
            ?: return toolError("QUIETMETRIX_TOKEN is not set in this server's environment. Mint one at Dashboard > Access tokens.")
        val name = arguments["name"]?.jsonPrimitive?.contentOrNull
            ?: return toolError("Missing required 'name' argument.")
        val description = arguments["description"]?.jsonPrimitive?.contentOrNull
        val idempotencyKey = arguments["idempotency_key"]?.jsonPrimitive?.contentOrNull

        return when (val outcome = client.createProject(url, token, name, description, idempotencyKey)) {
            is Outcome.Success -> toolText(json.encodeToString(ProjectCreateOutput.serializer(), outcome.value))
            is Outcome.AuthError -> toolError(outcome.message)
            is Outcome.ServerError -> toolError(outcome.message)
        }
    }

    private suspend fun callListProjects(arguments: JsonObject): JsonObject {
        val url = resolveUrl(arguments)
            ?: return toolError("Missing 'url' argument (and QUIETMETRIX_URL is not set in this server's environment).")
        val token = resolveToken()
            ?: return toolError("QUIETMETRIX_TOKEN is not set in this server's environment. Mint one at Dashboard > Access tokens.")

        return when (val outcome = client.listProjects(url, token)) {
            is Outcome.Success -> toolText(outcome.value)
            is Outcome.AuthError -> toolError(outcome.message)
            is Outcome.ServerError -> toolError(outcome.message)
        }
    }

    /**
     * Shared shape for every read-only, project-scoped analytics tool: resolve url/token,
     * require `project_id`, validate `range` if the tool accepts one, call [fetch], surface the
     * server's raw JSON verbatim on success or a tool error otherwise.
     */
    private suspend fun callProjectRead(
        arguments: JsonObject,
        fetch: suspend (url: String, token: String, projectId: String, range: String) -> Outcome<String>,
    ): JsonObject {
        val url = resolveUrl(arguments)
            ?: return toolError("Missing 'url' argument (and QUIETMETRIX_URL is not set in this server's environment).")
        val token = resolveToken()
            ?: return toolError("QUIETMETRIX_TOKEN is not set in this server's environment. Mint one at Dashboard > Access tokens. It must carry the analytics:read scope.")
        val projectId = arguments["project_id"]?.jsonPrimitive?.contentOrNull
            ?: return toolError("Missing required 'project_id' argument — the proj_... id from quietmetrix_list_projects.")
        val range = arguments["range"]?.jsonPrimitive?.contentOrNull ?: RangeToken.DEFAULT
        if (!RangeToken.isValid(range)) {
            return toolError("Invalid 'range' — expected one of ${RangeToken.VALID.sorted().joinToString(", ")}.")
        }

        return when (val outcome = fetch(url, token, projectId, range)) {
            is Outcome.Success -> toolText(outcome.value)
            is Outcome.AuthError -> toolError(outcome.message)
            is Outcome.ServerError -> toolError(outcome.message)
        }
    }

    private suspend fun callFunnelResults(arguments: JsonObject): JsonObject {
        val funnelKey = arguments["funnel_key"]?.jsonPrimitive?.contentOrNull
            ?: return toolError("Missing required 'funnel_key' argument — call quietmetrix_list_funnels first to find one.")
        return callProjectRead(arguments) { url, token, projectId, range ->
            client.getFunnelResults(url, token, projectId, funnelKey, range)
        }
    }

    private fun toolText(text: String): JsonObject = buildJsonObject {
        putJsonArray("content") { addJsonObject { put("type", "text"); put("text", text) } }
    }

    private fun toolError(message: String): JsonObject = buildJsonObject {
        put("isError", true)
        putJsonArray("content") { addJsonObject { put("type", "text"); put("text", message) } }
    }

    private fun successResponse(id: JsonElement, result: JsonObject): String =
        json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", result)
            },
        )

    private fun errorResponse(id: JsonElement, code: Int, message: String): String =
        json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                }
            },
        )
}

private fun createProjectToolSchema(): JsonObject = buildJsonObject {
    put("name", "quietmetrix_create_project")
    put(
        "description",
        "Creates a new QuietMetrix analytics project at the given server URL and returns its " +
            "publishable API key and tracking endpoint. After calling this, write the returned " +
            "trackingEndpoint and apiKey into the app's QuietMetrix SDK config (see " +
            "docs/agents/setup.md for the per-platform config location).",
    )
    putJsonObject("inputSchema") {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "QuietMetrix server base URL, e.g. https://qm.example.com. Falls back to QUIETMETRIX_URL.")
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Project name.")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Optional project description.")
            }
            putJsonObject("idempotency_key") {
                put("type", "string")
                put("description", "Optional: safe to retry a failed call with the same key — it re-finds the earlier project instead of minting a second one.")
            }
        }
        putJsonArray("required") { add("name") }
    }
}

private fun listProjectsToolSchema(): JsonObject = buildJsonObject {
    put("name", "quietmetrix_list_projects")
    put("description", "Lists QuietMetrix projects visible to the caller's access token.")
    putJsonObject("inputSchema") {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "QuietMetrix server base URL. Falls back to QUIETMETRIX_URL.")
            }
        }
    }
}

private const val RANGE_DESCRIPTION =
    "One of 1h, 1d, 7d, 30d, 90d — a trailing window ending now, in UTC. Defaults to 7d, " +
        "the right choice for a \"this week\" question. There is no calendar-week or " +
        "per-user-timezone bucketing: 7d always means the last 7*24 hours from the moment " +
        "of the call, not the current Monday-to-Sunday week."

/**
 * Shared schema shape for the read-only analytics tools: every one of them takes `project_id`
 * (required) and `url` (optional, falls back to QUIETMETRIX_URL); [withRange] adds the `range`
 * parameter for the ones whose underlying endpoint is time-windowed.
 */
private fun analyticsToolSchema(name: String, description: String, withRange: Boolean): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    putJsonObject("inputSchema") {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("project_id") {
                put("type", "string")
                put("description", "The proj_... project id, from quietmetrix_list_projects.")
            }
            putJsonObject("url") {
                put("type", "string")
                put("description", "QuietMetrix server base URL. Falls back to QUIETMETRIX_URL.")
            }
            if (withRange) {
                putJsonObject("range") {
                    put("type", "string")
                    put("description", RANGE_DESCRIPTION)
                }
            }
        }
        putJsonArray("required") { add("project_id") }
    }
}

private fun funnelResultsToolSchema(): JsonObject = buildJsonObject {
    put("name", "quietmetrix_get_funnel_results")
    put(
        "description",
        "Step-by-step conversion, drop-off, and time-to-convert for one funnel over a time " +
            "range — e.g. \"how many times has the onboarding funnel completed this week?\". " +
            "Call quietmetrix_list_funnels first to find the funnel_key.",
    )
    putJsonObject("inputSchema") {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("project_id") {
                put("type", "string")
                put("description", "The proj_... project id, from quietmetrix_list_projects.")
            }
            putJsonObject("funnel_key") {
                put("type", "string")
                put("description", "The funnel's key, from quietmetrix_list_funnels.")
            }
            putJsonObject("url") {
                put("type", "string")
                put("description", "QuietMetrix server base URL. Falls back to QUIETMETRIX_URL.")
            }
            putJsonObject("range") {
                put("type", "string")
                put("description", RANGE_DESCRIPTION)
            }
        }
        putJsonArray("required") { add("project_id"); add("funnel_key") }
    }
}
