package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.config.AuthConfig
import com.quietmetrix.server.config.DbConfig
import com.quietmetrix.server.config.RateLimitConfig
import com.quietmetrix.server.domain.LoginRequest
import com.quietmetrix.server.plugins.configureCors
import com.quietmetrix.server.plugins.configureSecurity
import com.quietmetrix.server.plugins.configureStatusPages
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class AuthRoutesIntegrationTest {
    @Test
    fun `POST login with missing fields returns 400`() = testApplication {
        val config = testConfig()
        application { testModule(config) }
        val client = createClient { }
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST login with invalid email returns 401`() = testApplication {
        val config = testConfig()
        application { testModule(config) }
        val client = createClient { }
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest.serializer(), LoginRequest("fake@example.com", "wrongpassword")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `protected route without token returns 401`() = testApplication {
        val config = testConfig()
        application { testModule(config) }
        val client = createClient { }
        val response = client.get("/api/v1/projects") { }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun testConfig() = AppConfig(
        profile = AppConfig.Profile.SELFHOST,
        db = DbConfig("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "", "org.h2.Driver", 5),
        auth = AuthConfig("integration-test-secret-key-do-not-use-ever-64-chars!!", "quietmetrix", "quietmetrix-api", 2),
        rateLimit = RateLimitConfig(enabled = false, 10, 60),
        billing = null,
    )

    private fun Application.testModule(config: AppConfig) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        configureCors(config)
        configureStatusPages()
        configureSecurity(config)
        routing {
            configureAuthRoutes(config)
            configureProjectRoutes()
        }
    }
}
