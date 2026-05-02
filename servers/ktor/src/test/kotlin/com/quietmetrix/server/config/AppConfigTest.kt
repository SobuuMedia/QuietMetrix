package com.quietmetrix.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigTest {

    @Test
    fun `default profile is selfhost`() {
        val config = io.ktor.server.config.MapApplicationConfig(
            "quietmetrix.db.url" to "jdbc:postgresql://localhost:5432/test",
            "quietmetrix.db.user" to "test",
            "quietmetrix.db.password" to "test",
            "quietmetrix.auth.jwtSecret" to "testsecret",
        )
        val appConfig = AppConfig.from(config)
        assertEquals(AppConfig.Profile.SELFHOST, appConfig.profile)
        assertFalse(appConfig.isCloud)
    }

    @Test
    fun `cloud profile enables billing`() {
        val config = io.ktor.server.config.MapApplicationConfig(
            "quietmetrix.profile" to "cloud",
            "quietmetrix.db.url" to "jdbc:postgresql://localhost:5432/test",
            "quietmetrix.db.user" to "test",
            "quietmetrix.db.password" to "test",
            "quietmetrix.auth.jwtSecret" to "testsecret",
            "quietmetrix.billing.provider" to "stripe",
        )
        val appConfig = AppConfig.from(config)
        assertEquals(AppConfig.Profile.CLOUD, appConfig.profile)
        assertTrue(appConfig.isCloud)
        assertEquals("stripe", appConfig.billing!!.provider)
    }
}