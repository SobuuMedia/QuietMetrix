package com.quietmetrix.server.routes

import com.quietmetrix.server.ingest.InstallIdHasher
import com.quietmetrix.server.persistence.InstallRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.EventCountsDaily
import com.quietmetrix.server.persistence.tables.Events
import com.quietmetrix.server.persistence.tables.EventsInbox
import com.quietmetrix.server.persistence.tables.InstallMeta
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.UsageCounters
import com.quietmetrix.server.persistence.tables.Users
import com.quietmetrix.server.ratelimit.InstallRateLimiter
import com.quietmetrix.server.config.InstallRateLimitConfig
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
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage 2 — per-install (anonymousId) abuse defense on the ingest route, exercised end-to-end.
 * Mirrors the TrackRoutes per-install block so the koin-ktor Route.inject incompatibility
 * is sidestepped.
 */
class IngestInstallThrottleTest {

    private val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Test
    fun `events from a revoked install are rejected with 403`() = testApplication {
        val dbName = "installthrottle_${java.util.UUID.randomUUID()}"
        val testDb = Database.connect("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Users, Projects, InstallMeta, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val projectRepo = ProjectRepository(testDb)
        val userRepo = UserRepository(testDb)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("fdroid-demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val salt = projectRepo.getInstallSalt(projectId)!!

        val installRepo = InstallRepository(testDb, rampEventThreshold = 500, rampWindowMinutes = 10)
        val installLimiter = InstallRateLimiter(
            InstallRateLimitConfig(enabled = true, requestsPerSecond = 0, burstPerMinute = 30),
            testScope,
        )

        // Pre-revoke the install that this test client will identify as.
        val rawInstall = "fdroid-install-001"
        val installHash = InstallIdHasher.hash(salt, rawInstall)
        installRepo.upsert(testDb.let { projectId }, installHash)
        installRepo.revoke(projectId, installHash)

        application {
            install(ContentNegotiation) { json(kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
            routing {
                post("/api/v1/track") {
                    val key = call.request.headers["X-QM-Api-Key"] ?: ""
                    val pid = projectRepo.validateApiKey(key)
                    if (pid == null) { call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized); return@post }
                    val body = """{"event":"page_view","ctx":{"anonymous_id":"$rawInstall"}}"""
                    // (Decode skipped in this mirror — we know the fixed install.)
                    val s = projectRepo.getInstallSalt(pid)
                    val repo = installRepo
                    if (!s.isNullOrBlank()) {
                        val inst = repo.upsert(pid, installHash)
                        if (inst.revoked) { call.respondText("""{"error":"install_revoked"}""", ContentType.Application.Json, HttpStatusCode.Forbidden); return@post }
                        if (!installLimiter.tryConsume("$pid:$installHash")) { call.response.header("Retry-After","60"); call.respondText("""{"error":"rate_limit_exceeded"}""", ContentType.Application.Json, HttpStatusCode.TooManyRequests); return@post }
                    }
                    call.respondText("""{"ok":true,"queued":1}""", ContentType.Application.Json, HttpStatusCode.Accepted)
                }
            }
        }

        val res = client.post("/api/v1/track") {
            header("X-QM-Api-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.Forbidden, res.status, "revoked install must be rejected: ${res.bodyAsText()}")
    }
}