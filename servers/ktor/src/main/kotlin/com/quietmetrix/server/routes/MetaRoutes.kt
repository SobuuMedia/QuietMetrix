package com.quietmetrix.server.routes

import com.quietmetrix.server.domain.HealthResponse
import com.quietmetrix.server.domain.MetaResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Lets a client discover public, secret-free server flags (currently just `debug`) before
 * doing anything else — the dashboard uses it to gate the demo-data toggle, and the
 * CLI/MCP `whoami` command uses it to validate a server URL. Mirrors php-hosting's
 * GET /api/v1/_meta (index.php) so the two backends stay contract-identical.
 */
fun Routing.configureMetaRoutes() {
    route("/api/v1") {
        get("/health") {
            call.respond(HealthResponse(ok = true, version = "0.2.0"))
        }
        get("/_meta") {
            call.respond(MetaResponse())
        }
    }
}
