package com.quietmetrix.server.routes

import com.quietmetrix.server.domain.HealthResponse
import com.quietmetrix.server.domain.MetaResponse
import com.quietmetrix.server.persistence.ProjectRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject

@kotlinx.serialization.Serializable
private data class SdkCapabilitiesResponse(
    val capabilities: List<String>,
    val minimum_versions: Map<String, String>,
)

/**
 * Lets a client discover public, secret-free server flags (currently just `debug`) before
 * doing anything else — the dashboard uses it to gate the demo-data toggle, and the
 * CLI/MCP `whoami` command uses it to validate a server URL. Mirrors php-hosting's
 * GET /api/v1/_meta (index.php) so the two backends stay contract-identical.
 */
fun Routing.configureMetaRoutes(aiEnabled: Boolean = com.quietmetrix.server.ai.AiConfig.fromEnvironment().enabled(System.getenv("QM_PROFILE") == "cloud"), selfhost: Boolean = System.getenv("QM_PROFILE") != "cloud") {
    val projects by inject<ProjectRepository>()
    route("/api/v1") {
        get("/health") {
            call.respond(HealthResponse(ok = true, version = "0.2.0"))
        }
        get("/ready") {
            val ready = withContext(Dispatchers.IO) { projects.ready() }
            call.respond(if (ready) io.ktor.http.HttpStatusCode.OK else io.ktor.http.HttpStatusCode.ServiceUnavailable,
                HealthResponse(ok = ready, version = "0.2.0"))
        }
        get("/_meta") {
            call.respond(MetaResponse(ai_enabled = aiEnabled, selfhost = selfhost))
        }
        get("/sdk/capabilities") {
            val projectId = call.request.header("X-QM-Api-Key")?.let(projects::validateApiKey)
            if (projectId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                return@get
            }
            call.respond(SdkCapabilitiesResponse(
                capabilities = listOf("counter_batch_receipts", "unique_window_v2", "counter_protocol_v2_hourly", "counter_protocol_v2_schema2", "remote_funnels", "experiment_config_v2"),
                minimum_versions = mapOf("counter_protocol_v2_hourly" to "0.4.0", "counter_protocol_v2_schema2" to "0.6.0", "remote_funnels" to "0.4.0", "experiment_config_v2" to "0.4.0"),
            ))
        }
    }
}
