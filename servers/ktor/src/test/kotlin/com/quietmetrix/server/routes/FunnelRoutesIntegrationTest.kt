package com.quietmetrix.server.routes

import com.quietmetrix.server.funnels.CreateFunnelRequest
import com.quietmetrix.server.funnels.FunnelDefinition
import com.quietmetrix.server.funnels.FunnelResponse
import com.quietmetrix.server.funnels.FunnelStepDefinition
import com.quietmetrix.server.funnels.FunnelValidation
import com.quietmetrix.server.funnels.FunnelValidationResult
import com.quietmetrix.server.funnels.FunnelsResponse
import com.quietmetrix.server.persistence.FunnelRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.Funnels
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the actual FunnelResponse/CreateFunnelRequest JSON wire shape through real
 * (de)serialization — not just the repository. Mirrors ProjectRoutesIntegrationTest's style
 * of re-declaring a minimal route rather than the full authenticated app module, which no
 * integration test in this codebase stands up (JWT + Koin DI wiring is exercised only at
 * compile time and manually).
 */
class FunnelRoutesIntegrationTest {

    private fun freshDb(): Database =
        Database.connect("jdbc:h2:mem:funnelroutes_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")

    @Test
    fun `create funnel then list returns it with the wire shape the dashboard expects`() = testApplication {
        val db = freshDb()
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers, Funnels) }
        UserRepository(db).create("admin@test.com", "password")
        val projectRepo = ProjectRepository(db)
        val apiKey = projectRepo.create("Demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val funnelRepo = FunnelRepository(db)

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                post("/api/v1/projects/{projectId}/funnels") {
                    val request = call.receive<CreateFunnelRequest>()
                    val definition = FunnelDefinition(
                        funnelKey = request.funnelKey, name = request.name, steps = request.steps,
                        windowSeconds = request.windowSeconds,
                    )
                    val result = FunnelValidation.validate(definition, funnelRepo.countActive(projectId))
                    if (result is FunnelValidationResult.Invalid) {
                        call.respond(HttpStatusCode.BadRequest, result.errors)
                        return@post
                    }
                    val created = funnelRepo.create(projectId, definition)
                    call.respond(
                        HttpStatusCode.Created,
                        FunnelResponse(
                            funnelKey = created.funnelKey, name = created.name, description = created.description,
                            steps = created.steps, windowSeconds = created.windowSeconds, source = created.source,
                            locked = created.locked, createdAt = created.createdAt, updatedAt = created.updatedAt,
                        ),
                    )
                }
                get("/api/v1/projects/{projectId}/funnels") {
                    call.respond(FunnelsResponse(funnelRepo.listActive(projectId).map {
                        FunnelResponse(
                            funnelKey = it.funnelKey, name = it.name, description = it.description,
                            steps = it.steps, windowSeconds = it.windowSeconds, source = it.source,
                            locked = it.locked, createdAt = it.createdAt, updatedAt = it.updatedAt,
                        )
                    }))
                }
            }
        }

        val createResponse = client.post("/api/v1/projects/$projectId/funnels") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateFunnelRequest.serializer(),
                    CreateFunnelRequest(
                        funnelKey = "signup",
                        name = "Signup",
                        steps = listOf(
                            FunnelStepDefinition(key = "view", event = "screen_view", screen = "signup"),
                            FunnelStepDefinition(key = "submit", event = "signup_submitted"),
                        ),
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        assertTrue(createResponse.bodyAsText().contains("\"funnel_key\":\"signup\""))
        assertTrue(createResponse.bodyAsText().contains("\"window_seconds\":604800"))

        val listResponse = client.get("/api/v1/projects/$projectId/funnels")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(FunnelsResponse.serializer(), listResponse.bodyAsText())
        assertEquals(1, parsed.funnels.size)
        assertEquals("signup", parsed.funnels[0].funnelKey)
        assertEquals(2, parsed.funnels[0].steps.size)
        assertEquals("screen_view", parsed.funnels[0].steps[0].event)
    }

    @Test
    fun `invalid funnel definition is rejected before hitting the repository`() = testApplication {
        val db = freshDb()
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers, Funnels) }
        UserRepository(db).create("admin@test.com", "password")
        val projectRepo = ProjectRepository(db)
        val apiKey = projectRepo.create("Demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val funnelRepo = FunnelRepository(db)

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                post("/api/v1/projects/{projectId}/funnels") {
                    val request = call.receive<CreateFunnelRequest>()
                    val definition = FunnelDefinition(
                        funnelKey = request.funnelKey, name = request.name, steps = request.steps,
                        windowSeconds = request.windowSeconds,
                    )
                    val result = FunnelValidation.validate(definition, funnelRepo.countActive(projectId))
                    if (result is FunnelValidationResult.Invalid) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("errors" to result.errors))
                        return@post
                    }
                    call.respond(HttpStatusCode.Created)
                }
            }
        }

        val response = client.post("/api/v1/projects/$projectId/funnels") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateFunnelRequest.serializer(),
                    CreateFunnelRequest(funnelKey = "Bad Key", name = "x", steps = emptyList()),
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(0, transaction(db) { Funnels.selectAll().count() })
    }
}
