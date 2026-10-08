package com.quietmetrix.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.config.AuthConfig
import com.quietmetrix.server.config.DbConfig
import com.quietmetrix.server.config.RateLimitConfig
import com.quietmetrix.server.domain.CreateTokenRequest
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.AccessTokens
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import com.quietmetrix.server.plugins.configureSecurity
import com.quietmetrix.server.plugins.configureStatusPages
import com.quietmetrix.server.plugins.configureTenantBoundary
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 3 — end-to-end proof that POST/GET /api/v1/projects accept either a dashboard JWT
 * or a scoped `qm_pat_…` personal access token, and that /api/v1/tokens is JWT-only and
 * enforces the projects:create admin gate.
 */
class AccessTokenAuthIntegrationTest {

    private val secret = "integration-test-secret-key-do-not-use-ever-64-chars!!"
    private val issuer = "quietmetrix"
    private val audience = "quietmetrix-api"

    private fun testConfig() = AppConfig(
        profile = AppConfig.Profile.SELFHOST,
        db = DbConfig("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "", "org.h2.Driver", 5),
        auth = AuthConfig(secret, issuer, audience, 2),
        rateLimit = RateLimitConfig(enabled = false, 10, 60),
    )

    private fun jwtFor(userId: Long, role: String): String =
        JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId.toString())
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600_000L))
            .sign(Algorithm.HMAC256(secret))

    private fun Application.testModule(config: AppConfig, db: Database) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Koin) {
            modules(module {
                single { config }
                single { db }
                single { ProjectRepository(get()) }
                single { UserRepository(get()) }
                single { ProjectMemberRepository(get()) }
                single { AccessTokenRepository(get()) }
            })
        }
        configureStatusPages()
        configureSecurity(config)
        configureTenantBoundary(config)
        routing {
            configureAuthRoutes(config)
            configureUserRoutes(config)
            configureProjectRoutes()
            configureTokenRoutes()
        }
    }

    private fun freshDb(name: String): Database {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) {
            SchemaUtils.create(
                Users, com.quietmetrix.server.persistence.tables.AuthSessions, com.quietmetrix.server.persistence.tables.RequestRateBuckets, Projects, ProjectMembers, AccessTokens, Counters, CounterCellsV2,
            )
        }
        return db
    }

    @Test
    fun `hosted administrators cannot enumerate read mutate or invite into another organization`() = testApplication {
        val config = testConfig().copy(profile = AppConfig.Profile.CLOUD)
        val db = freshDb("tenant_boundary")
        val users = UserRepository(db)
        val alice = users.create("alice@one.example", "password")
        val bob = users.create("bob@two.example", "password")
        val projects = ProjectRepository(db)
        projects.create("Alice project", null, alice)
        projects.create("Bob private project", null, bob)
        val bobProject = projects.findByOwnerId(bob).single()["id"].toString()
        val token = jwtFor(alice, "admin")
        application { testModule(config, db) }
        val list = client.get("/api/v1/projects") { header("Authorization", "Bearer $token") }
        assertEquals(HttpStatusCode.OK, list.status)
        assertFalse(list.bodyAsText().contains("Bob private project"))
        val team = client.get("/api/v1/users") { header("Authorization", "Bearer $token") }
        assertFalse(team.bodyAsText().contains("bob@two.example"))
        for (path in listOf("/api/v1/projects/$bobProject/members", "/api/v1/projects/$bobProject/events", "/api/v2/projects/$bobProject/report")) {
            val result = client.get(path) { header("Authorization", "Bearer $token") }
            assertEquals(HttpStatusCode.NotFound, result.status, path)
        }
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/projects/$bobProject") { header("Authorization", "Bearer $token") }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/users/$bob") { header("Authorization", "Bearer $token") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/users/invite") {
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json)
            setBody("""{"email":"other@three.example","role":"admin","new_organization":true}""")
        }.status)
        assertTrue(projects.findById(bobProject.removePrefix("proj_").toLong()) != null)
    }

    @Test
    fun `admin JWT can still create a project directly`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth1")
        val userId = UserRepository(db).create("admin@test.com", "password")
        application { testModule(config, db) }

        val response = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${jwtFor(userId, "admin")}")
            setBody("""{"name":"Via JWT"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("qm_ak_"))
    }

    @Test
    fun `PAT with projects create scope can create a project`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth2")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val tokenRepo = AccessTokenRepository(db)
        val pat = tokenRepo.create(userId, "agent", setOf("projects:create"))
        application { testModule(config, db) }

        val response = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${pat.token}")
            setBody("""{"name":"Via PAT"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("qm_ak_"))
    }

    @Test
    fun `PAT without projects create scope is forbidden from creating`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth3")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val tokenRepo = AccessTokenRepository(db)
        val pat = tokenRepo.create(userId, "readonly-agent", setOf("projects:read"))
        application { testModule(config, db) }

        val response = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${pat.token}")
            setBody("""{"name":"Should Fail"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `PAT creation permission follows current role after administrator demotion`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_demotion")
        val users = UserRepository(db)
        val id = users.create("demoted@auth.test", "password")
        val pat = AccessTokenRepository(db).create(id, "old-admin", setOf("projects:create"))
        users.updateRole(id, "developer")
        application { testModule(config, db) }
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${pat.token}")
            setBody("""{"name":"Should fail"}""")
        }.status)
    }

    @Test
    fun `unauthenticated create is rejected`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth4")
        application { testModule(config, db) }

        val response = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"No Auth"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `revoked PAT is rejected`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth5")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val tokenRepo = AccessTokenRepository(db)
        val pat = tokenRepo.create(userId, "agent", setOf("projects:create"))
        tokenRepo.revoke(pat.id, userId)
        application { testModule(config, db) }

        val response = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${pat.token}")
            setBody("""{"name":"Should Fail"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PAT cannot delete a project`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth6")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectRepo = ProjectRepository(db)
        projectRepo.create("Some Project", null, userId)
        val projectId = transaction(db) { Projects.selectAll().first()[Projects.id] }
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("projects:create"))
        application { testModule(config, db) }

        val response = client.delete("/api/v1/projects/proj_$projectId") {
            header("Authorization", "Bearer ${pat.token}")
        }
        // Not JWTPrincipal -> the auth-jwt provider rejects it outright.
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PAT with projects read scope can list projects`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth7")
        val userId = UserRepository(db).create("admin@test.com", "password")
        ProjectRepository(db).create("Listable", null, userId)
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("projects:read"))
        application { testModule(config, db) }

        val response = client.get("/api/v1/projects") {
            header("Authorization", "Bearer ${pat.token}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Listable"))
    }

    @Test
    fun `retrying a create with the same Idempotency-Key does not mint a second project`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth_idem1")
        val userId = UserRepository(db).create("admin@test.com", "password")
        application { testModule(config, db) }
        val jwt = jwtFor(userId, "admin")

        val first = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwt")
            header("Idempotency-Key", "req-abc")
            setBody("""{"name":"Retried App"}""")
        }
        assertEquals(HttpStatusCode.Created, first.status)
        val firstKey = Regex("qm_ak_\\w+").find(first.bodyAsText())!!.value

        val retry = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwt")
            header("Idempotency-Key", "req-abc")
            setBody("""{"name":"Retried App"}""")
        }
        assertEquals(HttpStatusCode.OK, retry.status)
        assertFalse(retry.bodyAsText().contains(firstKey), "the plaintext key must never be re-shown")

        val listResponse = client.get("/api/v1/projects") { header("Authorization", "Bearer $jwt") }
        assertEquals(1, Regex("\"name\":\"Retried App\"").findAll(listResponse.bodyAsText()).count())
    }

    @Test
    fun `different Idempotency-Key values create separate projects`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth_idem2")
        val userId = UserRepository(db).create("admin@test.com", "password")
        application { testModule(config, db) }
        val jwt = jwtFor(userId, "admin")

        client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwt")
            header("Idempotency-Key", "req-1")
            setBody("""{"name":"App One"}""")
        }
        client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwt")
            header("Idempotency-Key", "req-2")
            setBody("""{"name":"App Two"}""")
        }

        val listResponse = client.get("/api/v1/projects") { header("Authorization", "Bearer $jwt") }
        assertEquals(2, transaction(db) { Projects.selectAll().count() }.toInt())
        assertTrue(listResponse.bodyAsText().contains("App One") && listResponse.bodyAsText().contains("App Two"))
    }

    // --- /api/v1/tokens ---------------------------------------------------------------------

    @Test
    fun `admin can mint a token with the projects create scope`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth8")
        val userId = UserRepository(db).create("admin@test.com", "password")
        application { testModule(config, db) }

        val response = client.post("/api/v1/tokens") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${jwtFor(userId, "admin")}")
            setBody(Json.encodeToString(CreateTokenRequest.serializer(), CreateTokenRequest("laptop-agent", listOf("projects:create"))))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("qm_pat_"))
    }

    @Test
    fun `non-admin cannot mint a token with the projects create scope`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth9")
        val userId = UserRepository(db).create("dev@test.com", "password")
        UserRepository(db).updateRole(userId, "developer")
        application { testModule(config, db) }

        val response = client.post("/api/v1/tokens") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${jwtFor(userId, "developer")}")
            setBody(Json.encodeToString(CreateTokenRequest.serializer(), CreateTokenRequest("laptop-agent", listOf("projects:create"))))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `a PAT cannot be used to mint another token`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth10")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("projects:create"))
        application { testModule(config, db) }

        val response = client.post("/api/v1/tokens") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${pat.token}")
            setBody(Json.encodeToString(CreateTokenRequest.serializer(), CreateTokenRequest("nested-agent")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `revoke removes a token from the list and it stops validating`() = testApplication {
        val config = testConfig()
        val db = freshDb("pat_auth11")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val tokenRepo = AccessTokenRepository(db)
        val pat = tokenRepo.create(userId, "agent", setOf("projects:create"))
        application { testModule(config, db) }
        val jwt = jwtFor(userId, "admin")

        val before = client.get("/api/v1/tokens") { header("Authorization", "Bearer $jwt") }
        assertTrue(before.bodyAsText().contains("agent"))

        val deleteResponse = client.delete("/api/v1/tokens/${pat.id}") { header("Authorization", "Bearer $jwt") }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        val after = client.get("/api/v1/tokens") { header("Authorization", "Bearer $jwt") }
        assertFalse(after.bodyAsText().contains("agent"))

        val createResponse = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${pat.token}")
            setBody("""{"name":"Should Fail"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, createResponse.status)
    }
    @Test
    fun `real reviewer refresh token cannot access either session authentication path`() = testApplication {
        val config = testConfig()
        val db = freshDb("refresh_type_regression")
        val users = UserRepository(db)
        val id = users.create("reviewer@auth.test", "password")
        users.updateRole(id, "reviewer")
        application { testModule(config, db) }
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"reviewer@auth.test","password":"password"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val body = Json.parseToJsonElement(login.bodyAsText()) as kotlinx.serialization.json.JsonObject
        val refresh = (body.getValue("refresh_token") as kotlinx.serialization.json.JsonPrimitive).content
        val access = (body.getValue("token") as kotlinx.serialization.json.JsonPrimitive).content
        for (path in listOf("/api/v1/users", "/api/v1/projects")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path) { header("Authorization", "Bearer $refresh") }.status)
        }
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/users") { header("Authorization", "Bearer $access") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/projects") { header("Authorization", "Bearer $access") }.status)
        users.updateRole(id, "developer")
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/projects") { header("Authorization", "Bearer $access") }.status)
    }

    @Test
    fun `missing and unknown roles never become administrator sessions`() = testApplication {
        val config = testConfig()
        val db = freshDb("missing_role_regression")
        val id = UserRepository(db).create("role@auth.test", "password")
        application { testModule(config, db) }
        for (role in listOf(null, "superuser")) {
            val jwt = JWT.create().withAudience(audience).withIssuer(issuer)
                .withClaim("userId", id.toString()).withClaim("role", role)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600_000L)).sign(Algorithm.HMAC256(secret))
            for (path in listOf("/api/v1/users", "/api/v1/projects")) {
                assertEquals(HttpStatusCode.Unauthorized, client.get(path) { header("Authorization", "Bearer $jwt") }.status)
            }
        }
    }

    @Test
    fun `refresh rotates once and logout invalidates its access session`() = testApplication {
        val config = testConfig()
        val db = freshDb("rotation_logout_regression")
        UserRepository(db).create("rotate@auth.test", "password")
        application { testModule(config, db) }
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"rotate@auth.test","password":"password"}""")
        }
        val body = Json.parseToJsonElement(login.bodyAsText()) as kotlinx.serialization.json.JsonObject
        val refresh = (body.getValue("refresh_token") as kotlinx.serialization.json.JsonPrimitive).content
        suspend fun rotate() = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json); setBody("""{"refresh_token":"$refresh"}""")
        }
        val renewed = rotate()
        assertEquals(HttpStatusCode.OK, renewed.status)
        assertEquals(HttpStatusCode.Unauthorized, rotate().status)
        val tokens = Json.parseToJsonElement(renewed.bodyAsText()) as kotlinx.serialization.json.JsonObject
        val access = (tokens.getValue("token") as kotlinx.serialization.json.JsonPrimitive).content
        val currentRefresh = (tokens.getValue("refresh_token") as kotlinx.serialization.json.JsonPrimitive).content
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/projects") { header("Authorization", "Bearer $access") }.status)
        client.post("/api/v1/auth/logout") { contentType(ContentType.Application.Json); setBody("""{"refresh_token":"$currentRefresh"}""") }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/projects") { header("Authorization", "Bearer $access") }.status)
    }

}
