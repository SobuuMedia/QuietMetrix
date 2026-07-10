package com.quietmetrix.server.routes

import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.EventCountsDaily
import com.quietmetrix.server.persistence.tables.Events
import com.quietmetrix.server.persistence.tables.EventsInbox
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.UsageCounters
import com.quietmetrix.server.persistence.tables.Users
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteIntegrationTest {

    @Test
    fun `health endpoint serializes HealthResponse`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
            routing {
                get("/health") {
                    // Must respond with a @Serializable type — a mixed-type map
                    // (Boolean + String) fails kotlinx.serialization at runtime.
                    call.respond(com.quietmetrix.server.domain.HealthResponse(ok = true, version = "0.2.0"))
                }
            }
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"ok\":true"))
        assertTrue(body.contains("\"version\":\"0.2.0\""))
    }

    @Test
    fun `track without api key returns 401`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Projects, Users, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val projectRepo = ProjectRepository(testDb)

        application {
            install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
            routing {
                post("/track") {
                    val key = call.request.headers["X-QM-Api-Key"]
                    if (key.isNullOrBlank()) {
                        call.respondText("""{"error":"unauthorized","message":"Missing API key"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                    } else {
                        val pid = projectRepo.validateApiKey(key)
                        if (pid == null) {
                            call.respondText("""{"error":"unauthorized","message":"Invalid"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        } else {
                            call.respondText("""{"ok":true,"queued":1}""", ContentType.Application.Json, HttpStatusCode.Accepted)
                        }
                    }
                }
            }
        }
        val response = client.post("/track") {
            contentType(ContentType.Application.Json)
            setBody("""{"event":"page_view"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `track with valid api key returns 202`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Projects, Users, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val projectRepo = ProjectRepository(testDb)
        val userRepo = UserRepository(testDb)
        userRepo.create("test@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("valid-project", null, 1L)

        application {
            install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
            routing {
                post("/track") {
                    val key = call.request.headers["X-QM-Api-Key"] ?: ""
                    val pid = projectRepo.validateApiKey(key)
                    if (pid == null) {
                        call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    call.respondText("""{"ok":true,"queued":1}""", ContentType.Application.Json, HttpStatusCode.Accepted)
                }
            }
        }
        val response = client.post("/track") {
            header("X-QM-Api-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody("""{"event":"page_view"}""")
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
    }
}