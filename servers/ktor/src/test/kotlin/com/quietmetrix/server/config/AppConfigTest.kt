package com.quietmetrix.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppConfigTest {

    @Test
    fun `default profile is selfhost`() {
        val config = io.ktor.server.config.MapApplicationConfig().apply {
            put("quietmetrix.db.url", "jdbc:postgresql://localhost:5432/test")
            put("quietmetrix.db.user", "test")
            put("quietmetrix.db.password", "test")
            put("quietmetrix.auth.jwtSecret", "test-secret-do-not-use-in-production-64-chars-long!!")
            put("quietmetrix.cors.allowedOrigins", listOf("http://localhost:8080"))
        }
        val appConfig = AppConfig.from(config)
        assertEquals(AppConfig.Profile.SELFHOST, appConfig.profile)
        assertFalse(appConfig.isCloud)
    }

    @Test
    fun `cors origins accept a comma-separated string from env`() {
        val config = io.ktor.server.config.MapApplicationConfig().apply {
            put("quietmetrix.db.url", "jdbc:postgresql://localhost:5432/test")
            put("quietmetrix.db.user", "test")
            put("quietmetrix.db.password", "test")
            put("quietmetrix.auth.jwtSecret", "test-secret-do-not-use-in-production-64-chars-long!!")
            // As delivered by the QM_CORS_ALLOWED_ORIGINS env var: a single string.
            put("quietmetrix.cors.allowedOrigins", "http://localhost:8080, https://example.com")
        }
        val appConfig = AppConfig.from(config)
        assertEquals(
            listOf("http://localhost:8080", "https://example.com"),
            appConfig.cors.allowedOrigins,
        )
    }
}
