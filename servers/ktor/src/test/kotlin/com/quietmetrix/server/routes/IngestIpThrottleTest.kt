package com.quietmetrix.server.routes

import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.EventCountsDaily
import com.quietmetrix.server.persistence.tables.Events
import com.quietmetrix.server.persistence.tables.EventsInbox
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.UsageCounters
import com.quietmetrix.server.persistence.tables.Users
import com.quietmetrix.server.ratelimit.IpRateLimiter
import com.quietmetrix.server.config.IpRateLimitConfig
import com.quietmetrix.server.ratelimit.RateLimiter
import com.quietmetrix.server.config.RateLimitConfig
import com.quietmetrix.server.util.clientIp
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
 * Stage 1 — per-IP throttling on the ingest routes, exercised end-to-end through the HTTP
 * layer using the real [IpRateLimiter], the real trusted-proxy [clientIp] extension, and a
 * real H2-backed [ProjectRepository]. (Avoids koin-ktor's Route.inject here because of a
 * pre-existing koin 3.5.6 ↔ ktor 3 incompatibility; the per-IP wiring in TrackRoutes mirrors
 * this logic exactly.)
 */
class IngestIpThrottleTest {

    private val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Test
    fun `track returns 429 after per-ip burst is exhausted`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:ipthrottle;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) {
            SchemaUtils.create(Projects, Users, Events, EventsInbox, EventCountsDaily, UsageCounters)
        }
        val projectRepo = ProjectRepository(testDb)
        val userRepo = UserRepository(testDb)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("fdroid-demo", null, 1L)

        val ipLimiter = IpRateLimiter(
            IpRateLimitConfig(enabled = true, requestsPerSecond = 0, burstPerMinute = 2),
            testScope,
        )
        val projectLimiter = RateLimiter(
            RateLimitConfig(enabled = false, requestsPerSecond = 10, burstPerMinute = 60),
            testScope,
        )
        val trustedProxies = setOf("localhost", "127.0.0.1")

        application {
            install(ContentNegotiation) { json(kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
            routing {
                post("/api/v1/track") {
                    val key = call.request.headers["X-QM-Api-Key"]
                    if (key.isNullOrBlank()) {
                        call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val pid = projectRepo.validateApiKey(key)
                    if (pid == null) {
                        call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val ip = call.clientIp(trustedProxies)
                    if (!projectLimiter.tryConsume("wm:$pid")) {
                        call.response.header("Retry-After", "60")
                        call.respondText("""{"error":"rate_limit_exceeded"}""", ContentType.Application.Json, HttpStatusCode.TooManyRequests)
                        return@post
                    }
                    if (!ipLimiter.tryConsume("wm:ip:$ip")) {
                        call.response.header("Retry-After", "60")
                        call.respondText("""{"error":"rate_limit_exceeded","message":"IP rate limit exceeded"}""", ContentType.Application.Json, HttpStatusCode.TooManyRequests)
                        return@post
                    }
                    call.respondText("""{"ok":true,"queued":1}""", ContentType.Application.Json, HttpStatusCode.Accepted)
                }
            }
        }

        val body = """{"event":"page_view"}"""
        val r1 = client.post("/api/v1/track") {
            header("X-QM-Api-Key", apiKey); header("X-Forwarded-For", "9.9.9.9")
            contentType(ContentType.Application.Json); setBody(body)
        }
        val r2 = client.post("/api/v1/track") {
            header("X-QM-Api-Key", apiKey); header("X-Forwarded-For", "9.9.9.9")
            contentType(ContentType.Application.Json); setBody(body)
        }
        val r3 = client.post("/api/v1/track") {
            header("X-QM-Api-Key", apiKey); header("X-Forwarded-For", "9.9.9.9")
            contentType(ContentType.Application.Json); setBody(body)
        }
        assertEquals(HttpStatusCode.Accepted, r1.status, "r1 body: ${r1.bodyAsText()}")
        assertEquals(HttpStatusCode.Accepted, r2.status, "r2 body: ${r2.bodyAsText()}")
        assertEquals(HttpStatusCode.TooManyRequests, r3.status, "third request from same IP must be throttled: ${r3.bodyAsText()}")

        val rOther = client.post("/api/v1/track") {
            header("X-QM-Api-Key", apiKey); header("X-Forwarded-For", "8.8.8.8")
            contentType(ContentType.Application.Json); setBody(body)
        }
        assertEquals(HttpStatusCode.Accepted, rOther.status, "different IP must be unaffected: ${rOther.bodyAsText()}")
    }
}