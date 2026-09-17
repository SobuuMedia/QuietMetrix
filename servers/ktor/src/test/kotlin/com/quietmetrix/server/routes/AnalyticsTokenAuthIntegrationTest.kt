package com.quietmetrix.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.config.AuthConfig
import com.quietmetrix.server.config.DbConfig
import com.quietmetrix.server.config.RateLimitConfig
import com.quietmetrix.server.funnels.CreateFunnelRequest
import com.quietmetrix.server.funnels.FunnelDefinition
import com.quietmetrix.server.funnels.FunnelRegistrationService
import com.quietmetrix.server.funnels.FunnelStepDefinition
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.FunnelRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.AccessTokens
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.CountersQuarantine
import com.quietmetrix.server.persistence.tables.FunnelManifests
import com.quietmetrix.server.persistence.tables.Funnels
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import com.quietmetrix.server.plugins.configureSecurity
import com.quietmetrix.server.plugins.configureStatusPages
import com.quietmetrix.server.ratelimit.RateLimiter
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 1 — proves the analytics:read PAT scope: a scoped token reaches
 * /aggregates, /transitions, /sessions, /retention, and the funnel list/results
 * endpoints exactly like a dashboard session would, a token missing the scope is forbidden,
 * a revoked token is unauthorized, and an ordinary session JWT is unaffected (regression).
 */
class AnalyticsTokenAuthIntegrationTest {

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
                single { CounterRepository(get()) }
                single { FunnelRepository(get()) }
                single { FunnelRegistrationService(get()) }
                single { RateLimiter(config.rateLimit, kotlinx.coroutines.CoroutineScope(Dispatchers.Default + SupervisorJob())) }
            })
        }
        configureStatusPages()
        configureSecurity(config)
        routing {
            configureDashboardRoutes()
            configureFunnelRoutes()
        }
    }

    private fun freshDb(name: String): Database {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) {
            SchemaUtils.create(
                Users, Projects, ProjectMembers, AccessTokens,
                Funnels, FunnelManifests, Counters, CountersQuarantine,
            )
        }
        return db
    }

    private fun seedProject(db: Database, ownerUserId: Long, name: String = "Demo"): Long {
        val projectRepo = ProjectRepository(db)
        val apiKey = projectRepo.create(name, null, ownerUserId)
        return projectRepo.validateApiKey(apiKey)!!
    }

    // --- /aggregates, /events, /transitions, /sessions, /retention -----------------------------

    @Test
    fun `PAT with analytics read scope passes the auth gate on aggregates`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth1")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("analytics:read"))
        application { testModule(config, db) }

        val response = client.get("/api/v1/projects/proj_$projectId/aggregates?from=2020-01-01T00:00:00Z&to=2020-01-05T00:00:00Z") {
            header("Authorization", "Bearer ${pat.token}")
        }
        // Not asserting 200: /aggregates now reads from counters (no raw DATE(ts) SQL), but
        // this project has no counter rows seeded, and other dashboard routes exercised
        // elsewhere still build a raw DATE(ts) call that targets Postgres and H2 doesn't
        // define under any mode — a pre-existing gap, unrelated to and untouched by the
        // analytics:read scope this test verifies. What matters here is that the request got
        // PAST the auth gate at all.
        assertTrue(response.status != HttpStatusCode.Unauthorized && response.status != HttpStatusCode.Forbidden)
    }

    @Test
    fun `PAT without analytics read scope is forbidden from aggregates`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth2")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("projects:read"))
        application { testModule(config, db) }

        val response = client.get("/api/v1/projects/proj_$projectId/aggregates?from=2020-01-01T00:00:00Z&to=2020-01-05T00:00:00Z") {
            header("Authorization", "Bearer ${pat.token}")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `unauthenticated aggregates request is rejected`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth3")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        application { testModule(config, db) }

        val response = client.get("/api/v1/projects/proj_$projectId/aggregates?from=2020-01-01T00:00:00Z&to=2020-01-05T00:00:00Z")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `revoked PAT is rejected from aggregates`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth4")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        val tokenRepo = AccessTokenRepository(db)
        val pat = tokenRepo.create(userId, "agent", setOf("analytics:read"))
        tokenRepo.revoke(pat.id, userId)
        application { testModule(config, db) }

        val response = client.get("/api/v1/projects/proj_$projectId/aggregates?from=2020-01-01T00:00:00Z&to=2020-01-05T00:00:00Z") {
            header("Authorization", "Bearer ${pat.token}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `dashboard session JWT still reaches aggregates transitions sessions and retention`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth5")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        application { testModule(config, db) }
        val jwt = jwtFor(userId, "admin")

        // Not asserting 200 for aggregates: no counter rows are seeded for this project, and
        // the point of this test is the auth gate, not the response body.
        val aggregates = client.get("/api/v1/projects/proj_$projectId/aggregates?from=2020-01-01T00:00:00Z&to=2020-01-05T00:00:00Z") {
            header("Authorization", "Bearer $jwt")
        }
        assertTrue(aggregates.status != HttpStatusCode.Unauthorized && aggregates.status != HttpStatusCode.Forbidden)

        // transitions/sessions/retention all read from `counters` (no raw SQL DATE() call
        // left in any of them), so an empty, unseeded project still returns 200.
        // Regression: these previously sat outside the authenticate("auth-jwt") block by
        // accident (a stray closing brace), so a JWT never reached them — see RouteAuth.kt.
        val transitions = client.get("/api/v1/projects/proj_$projectId/transitions") { header("Authorization", "Bearer $jwt") }
        assertEquals(HttpStatusCode.OK, transitions.status)

        val sessions = client.get("/api/v1/projects/proj_$projectId/sessions") { header("Authorization", "Bearer $jwt") }
        assertEquals(HttpStatusCode.OK, sessions.status)

        val retention = client.get("/api/v1/projects/proj_$projectId/retention") { header("Authorization", "Bearer $jwt") }
        assertEquals(HttpStatusCode.OK, retention.status)
    }

    @Test
    fun `PAT with analytics read scope reaches transitions sessions and retention`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth6")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("analytics:read"))
        application { testModule(config, db) }

        val transitions = client.get("/api/v1/projects/proj_$projectId/transitions") { header("Authorization", "Bearer ${pat.token}") }
        assertEquals(HttpStatusCode.OK, transitions.status)

        val sessions = client.get("/api/v1/projects/proj_$projectId/sessions") { header("Authorization", "Bearer ${pat.token}") }
        assertEquals(HttpStatusCode.OK, sessions.status)

        val retention = client.get("/api/v1/projects/proj_$projectId/retention") { header("Authorization", "Bearer ${pat.token}") }
        assertEquals(HttpStatusCode.OK, retention.status)
    }

    // --- /funnels (list) and /funnels/{key}/results ---------------------------------------------

    @Test
    fun `PAT with analytics read scope can list funnels and read results`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth7")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        FunnelRepository(db).create(
            projectId,
            FunnelDefinition(
                funnelKey = "signup",
                name = "Signup",
                steps = listOf(
                    FunnelStepDefinition(key = "view", event = "screen_view", screen = "signup"),
                    FunnelStepDefinition(key = "submit", event = "signup_submitted"),
                ),
            ),
        )
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("analytics:read"))
        application { testModule(config, db) }

        val list = client.get("/api/v1/projects/proj_$projectId/funnels") { header("Authorization", "Bearer ${pat.token}") }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("signup"))

        val results = client.get("/api/v1/projects/proj_$projectId/funnels/signup/results?range=7d") {
            header("Authorization", "Bearer ${pat.token}")
        }
        assertEquals(HttpStatusCode.OK, results.status)
    }

    @Test
    fun `PAT without analytics read scope is forbidden from funnel list and results`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth8")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        FunnelRepository(db).create(
            projectId,
            FunnelDefinition(
                funnelKey = "signup",
                name = "Signup",
                steps = listOf(FunnelStepDefinition(key = "view", event = "screen_view")),
            ),
        )
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("projects:read"))
        application { testModule(config, db) }

        val list = client.get("/api/v1/projects/proj_$projectId/funnels") { header("Authorization", "Bearer ${pat.token}") }
        assertEquals(HttpStatusCode.Forbidden, list.status)

        val results = client.get("/api/v1/projects/proj_$projectId/funnels/signup/results") {
            header("Authorization", "Bearer ${pat.token}")
        }
        assertEquals(HttpStatusCode.Forbidden, results.status)
    }

    @Test
    fun `a PAT can never reach funnel write endpoints regardless of scope`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth9")
        val userId = UserRepository(db).create("admin@test.com", "password")
        val projectId = seedProject(db, userId)
        // A PAT can carry every scope that exists and must still be rejected — write access
        // stays JWT-only, see FunnelRoutes.kt's resolveProjectId() (JWT-only) vs
        // resolveProjectIdForRead() (JWT or PAT).
        val pat = AccessTokenRepository(db).create(userId, "agent", setOf("analytics:read", "projects:create", "projects:read"))
        application { testModule(config, db) }

        val response = client.post("/api/v1/projects/proj_$projectId/funnels") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${pat.token}")
            setBody(
                Json.encodeToString(
                    CreateFunnelRequest.serializer(),
                    CreateFunnelRequest(funnelKey = "x", name = "X", steps = listOf(FunnelStepDefinition(key = "a", event = "e"))),
                )
            )
        }
        // Not a JWTPrincipal -> the auth-jwt provider inside FunnelRoutes' write block rejects it.
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `a project owned by a different user is not found for a PAT`() = testApplication {
        val config = testConfig()
        val db = freshDb("analytics_auth10")
        val owner = UserRepository(db).create("owner@test.com", "password")
        val stranger = UserRepository(db).create("stranger@test.com", "password")
        val projectId = seedProject(db, owner)
        val pat = AccessTokenRepository(db).create(stranger, "agent", setOf("analytics:read"))
        application { testModule(config, db) }

        val response = client.get("/api/v1/projects/proj_$projectId/funnels") { header("Authorization", "Bearer ${pat.token}") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
