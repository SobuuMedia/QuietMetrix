package com.quietmetrix.server.ai

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.config.AuthConfig
import com.quietmetrix.server.config.DbConfig
import com.quietmetrix.server.config.RateLimitConfig
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.routes.configureAskRoutes
import io.ktor.client.request.bearerAuth
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Customer admin privileges cannot cross hosted AI billing boundaries or reach operator endpoints. */
class AiRoutesTest {
    @Test fun `owner member outsider admin operator and unavailable contracts`() = testApplication {
        val config=AppConfig(profile=AppConfig.Profile.CLOUD,ai=AiConfig(operatorKey="operator-secret"),db=DbConfig("unused","unused","unused","unused",1),auth=AuthConfig("test-secret","qm","qm",2),rateLimit=RateLimitConfig(false,10,60))
        val counters=mockk<CounterRepository>()
        val projects=mockk<ProjectRepository>()
        val members=mockk<ProjectMemberRepository>()
        val tokens=mockk<AccessTokenRepository>()
        val users=mockk<UserRepository>()
        every { users.findById(any()) } answers { mapOf("id" to firstArg<Long>(), "status" to "active", "role" to "reviewer") }
        val ledger=mockk<AiAccounting>()
        val service=mockk<AiService>()
        every { projects.findById(1) } returns mapOf("ownerUserId" to 10L)
        every { members.findMembership(1, any()) } returns null
        every { members.findMembership(1,20) } returns mapOf("role" to "reviewer")
        every { counters.readCells(any(),any(),any(),any(),any()) } returns emptyList()
        every { ledger.capabilities("10",any()) } returns aiCapabilities(false,false,"ai_unavailable","2026-11-01")
        every { service.generate(any(),any(),any(),any(),any(),any()) } throws AiDenied("ai_unavailable")
        every { ledger.operator("plans",null,null) } returns buildJsonArray {}
        application {
            install(ContentNegotiation) { json(Json { encodeDefaults=true }) }
            install(Koin) { modules(module { single { config };single { counters };single { projects };single { members };single { tokens };single { users };single { ledger };single { service } }) }
            routing { configureAskRoutes() }
        }
        fun jwt(id: Long,role: String="reviewer"): String {
            every { users.findById(id) } returns mapOf("id" to id, "status" to "active", "role" to role)
            return JWT.create().withAudience("qm").withIssuer("qm").withClaim("userId",id.toString()).withClaim("role",role).withExpiresAt(Date(System.currentTimeMillis()+60000)).sign(Algorithm.HMAC256("test-secret"))
        }
        for(id in listOf(10L,20L)) {
            val response=client.get("/api/v1/projects/1/ai/capabilities") { bearerAuth(jwt(id)) }
            assertEquals(HttpStatusCode.OK,response.status)
            assertFalse(response.bodyAsText().contains("cost"))
            for(endpoint in listOf("/ai/ask","/ask")) {
                val denied=client.post("/api/v1/projects/1$endpoint") { bearerAuth(jwt(id));contentType(ContentType.Application.Json);setBody("""{"question":"sessions","range":"7d"}""") }
                assertEquals(HttpStatusCode.NotFound,denied.status)
                assertTrue(denied.bodyAsText().contains("ai_unavailable"))
            }
        }
        for(role in listOf("admin","developer","reviewer")) {
            assertEquals(HttpStatusCode.Forbidden,client.get("/api/v1/projects/1/ai/capabilities") { bearerAuth(jwt(99,role)) }.status)
            assertEquals(HttpStatusCode.Unauthorized,client.get("/api/v1/_operator/ai/plans") { bearerAuth(jwt(10,role)) }.status)
        }
        assertEquals(HttpStatusCode.OK,client.get("/api/v1/_operator/ai/plans") { bearerAuth("operator-secret") }.status)
        verify(exactly=4) { service.generate("10","1",any(),false,any(),any()) }
    }
}
