package com.quietmetrix.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `cloud profile enables billing`() {
        val config = io.ktor.server.config.MapApplicationConfig().apply {
            put("quietmetrix.profile", "cloud")
            put("quietmetrix.db.url", "jdbc:postgresql://localhost:5432/test")
            put("quietmetrix.db.user", "test")
            put("quietmetrix.db.password", "test")
            put("quietmetrix.auth.jwtSecret", "test-secret-do-not-use-in-production-64-chars-long!!")
            put("quietmetrix.billing.provider", "stripe")
            put("quietmetrix.billing.stripeWebhookSecret", "whsec_test_secret")
            put("quietmetrix.cors.allowedOrigins", listOf("http://localhost:8080"))
        }
        val appConfig = AppConfig.from(config)
        assertEquals(AppConfig.Profile.CLOUD, appConfig.profile)
        assertTrue(appConfig.isCloud)
        assertEquals("stripe", appConfig.billing!!.provider)
    }
}
