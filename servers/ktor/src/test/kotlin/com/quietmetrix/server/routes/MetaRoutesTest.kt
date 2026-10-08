package com.quietmetrix.server.routes

import io.ktor.client.request.get
import io.ktor.client.request.header
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
    fun `readiness rejects an unmigrated database while liveness remains available`() = testApplication {
        val db = org.jetbrains.exposed.v1.jdbc.Database.connect("jdbc:h2:mem:unmigrated_readiness;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        val projects = com.quietmetrix.server.persistence.ProjectRepository(db)
        application {
            install(ContentNegotiation) { json() }
            install(org.koin.ktor.plugin.Koin) { modules(org.koin.dsl.module { single { projects } }) }
            routing { configureMetaRoutes() }
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/ready").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/health").status)
    }

    @Test
    fun `authenticated SDK capabilities serializes lists and version maps`() = testApplication {
        val db = org.jetbrains.exposed.v1.jdbc.Database.connect("jdbc:h2:mem:capabilities;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        org.jetbrains.exposed.v1.jdbc.transactions.transaction(db) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(com.quietmetrix.server.persistence.tables.Users, com.quietmetrix.server.persistence.tables.Projects)
        }
        val id = com.quietmetrix.server.persistence.UserRepository(db).create("capabilities@fixture.example", "password123")
        val projects = com.quietmetrix.server.persistence.ProjectRepository(db)
        val key = projects.create("SDK fixture", null, id)
        application {
            install(ContentNegotiation) { json() }
            install(org.koin.ktor.plugin.Koin) { modules(org.koin.dsl.module { single { projects } }) }
            routing { configureMetaRoutes() }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/sdk/capabilities").status)
        val response = client.get("/api/v1/sdk/capabilities") { header("X-QM-Api-Key", key) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).toString()
        assertTrue(body.contains("counter_protocol_v2_schema2"))
        assertTrue(body.contains("minimum_versions"))
        assertTrue(body.contains("0.6.0"))
    }

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
        assertTrue(body.contains("counter_batch_receipts"))
        assertTrue(body.contains("unique_window_v2"))
    }
}
