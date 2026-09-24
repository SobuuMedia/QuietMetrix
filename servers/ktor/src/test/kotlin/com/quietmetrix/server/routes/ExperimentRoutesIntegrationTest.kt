package com.quietmetrix.server.routes

import com.quietmetrix.server.experiments.CreateExperimentRequest
import com.quietmetrix.server.experiments.ExperimentConfigResponse
import com.quietmetrix.server.experiments.ExperimentOutcome
import com.quietmetrix.server.experiments.ExperimentService
import com.quietmetrix.server.experiments.ExperimentsResponse
import com.quietmetrix.server.experiments.UpdateExperimentRequest
import com.quietmetrix.server.experiments.toConfigDto
import com.quietmetrix.server.experiments.toDto
import com.quietmetrix.server.persistence.ExperimentRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.Experiments
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import com.quietmetrix.server.ratelimit.QuotaEnforcer
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
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the actual ExperimentDto/CreateExperimentRequest/ExperimentConfigResponse JSON wire
 * shape through real (de)serialization — not just the service. Mirrors
 * FunnelRoutesIntegrationTest's style of re-declaring a minimal route rather than the full
 * authenticated app module (JWT + Koin DI wiring is exercised only at compile time here).
 */
class ExperimentRoutesIntegrationTest {

    private fun freshDb(): Database =
        Database.connect("jdbc:h2:mem:experimentroutes_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")

    @Test
    fun `create then list returns the exact wire shape the dashboard expects`() = testApplication {
        val db = freshDb()
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers, Experiments) }
        UserRepository(db).create("admin@test.com", "password")
        val projectRepo = ProjectRepository(db)
        val apiKey = projectRepo.create("Demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val experimentRepo = ExperimentRepository(db)
        val service = ExperimentService(experimentRepo, projectRepo, QuotaEnforcer(projectRepo), isCloud = false)

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                post("/api/v1/projects/{projectId}/experiments") {
                    val request = call.receive<CreateExperimentRequest>()
                    when (val outcome = service.create(projectId, request)) {
                        is ExperimentOutcome.Ok -> call.respond(HttpStatusCode.Created, outcome.value.toDto())
                        is ExperimentOutcome.Error -> call.respond(HttpStatusCode.fromValue(outcome.status), outcome.message)
                    }
                }
                get("/api/v1/projects/{projectId}/experiments") {
                    val result = service.list(projectId)
                    call.respond(ExperimentsResponse(result.experiments.map { it.toDto() }, result.limit))
                }
            }
        }

        val createResponse = client.post("/api/v1/projects/$projectId/experiments") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateExperimentRequest.serializer(),
                    CreateExperimentRequest(key = "checkout_cta", name = "Checkout button colour", screen = "Checkout"),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createdBody = createResponse.bodyAsText()
        assertTrue(createdBody.contains("\"key\":\"checkout_cta\""))
        assertTrue(createdBody.contains("\"status\":\"draft\""))
        assertTrue(createdBody.contains("\"traffic_percent\":100"))
        assertTrue(createdBody.contains("\"weight\":50"))

        val listResponse = client.get("/api/v1/projects/$projectId/experiments")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(ExperimentsResponse.serializer(), listResponse.bodyAsText())
        assertEquals(1, parsed.experiments.size)
        assertEquals("checkout_cta", parsed.experiments[0].key)
        assertEquals(null, parsed.limit) // isCloud = false -> unlimited
    }

    @Test
    fun `list returns a numeric limit on a CLOUD free-plan project`() = testApplication {
        val db = freshDb()
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers, Experiments) }
        UserRepository(db).create("admin@test.com", "password")
        val projectRepo = ProjectRepository(db)
        val apiKey = projectRepo.create("Demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val service = ExperimentService(ExperimentRepository(db), projectRepo, QuotaEnforcer(projectRepo), isCloud = true)

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                get("/api/v1/projects/{projectId}/experiments") {
                    val result = service.list(projectId)
                    call.respond(ExperimentsResponse(result.experiments.map { it.toDto() }, result.limit))
                }
            }
        }

        val listResponse = client.get("/api/v1/projects/$projectId/experiments")
        val parsed = Json.decodeFromString(ExperimentsResponse.serializer(), listResponse.bodyAsText())
        assertEquals(2, parsed.limit)
    }

    @Test
    fun `sdk config endpoint returns only active experiments with the minimal fields`() = testApplication {
        val db = freshDb()
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers, Experiments) }
        UserRepository(db).create("admin@test.com", "password")
        val projectRepo = ProjectRepository(db)
        val apiKey = projectRepo.create("Demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val experimentRepo = ExperimentRepository(db)
        val service = ExperimentService(experimentRepo, projectRepo, null, isCloud = false)

        service.create(projectId, CreateExperimentRequest(key = "draft_one", name = "Draft"))
        service.create(projectId, CreateExperimentRequest(key = "active_one", name = "Active"))
        service.update(projectId, "active_one", UpdateExperimentRequest(status = "active"))

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                get("/api/v1/experiments/config") {
                    val key = call.request.header("X-QM-Api-Key")
                    if (key != apiKey) {
                        call.respond(HttpStatusCode.Unauthorized, "unauthorized")
                        return@get
                    }
                    call.respond(ExperimentConfigResponse(service.activeConfig(projectId).map { it.toConfigDto() }))
                }
            }
        }

        val unauthorized = client.get("/api/v1/experiments/config") { header("X-QM-Api-Key", "wrong_key") }
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

        val response = client.get("/api/v1/experiments/config") { header("X-QM-Api-Key", apiKey) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"key\":\"active_one\""))
        assertTrue(!body.contains("draft_one"))
        // Only the minimal SDK-facing fields -- the experiment's display name, screen label,
        // and every timestamp must never leave the server. (A variant's own "name" field, e.g.
        // {"name":"a","weight":50}, legitimately appears -- it's the config, not display metadata.)
        assertTrue(!body.contains("Active")) // the experiment's display name, "Active"
        assertTrue(!body.contains("\"screen\""))
        assertTrue(!body.contains("\"created_at\""))
        val parsed = Json.decodeFromString(ExperimentConfigResponse.serializer(), body)
        assertEquals(1, parsed.experiments.size)
        assertEquals(100, parsed.experiments[0].trafficPercent)
    }
}
