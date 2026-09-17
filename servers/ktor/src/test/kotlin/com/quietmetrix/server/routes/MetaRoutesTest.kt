package com.quietmetrix.server.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GET /api/v1/_meta — was documented and consumed by the dashboard (ApiClient.meta()) but
 * had no matching route on the Ktor backend (PHP's has one; see php-hosting/index.php).
 * Every call 404'd, silently swallowed by DashboardViewModel.init()'s catch, and by the new
 * CLI/MCP `whoami` command, which uses this endpoint to validate a server URL before doing
 * anything else — see docs/agents/setup.md.
 */
class MetaRoutesTest {

    @Test
    fun `meta endpoint returns version and debug flag`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
            routing { configureMetaRoutes() }
        }
        val response = client.get("/api/v1/_meta")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"version\""))
        assertTrue(body.contains("\"debug\":false"))
    }
}
