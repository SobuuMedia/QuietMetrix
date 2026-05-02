package com.quietmetrix.analytics.internal.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceContextTest {

    @Test
    fun `platform is jvm`() {
        assertEquals("jvm", DeviceContext().platform)
    }

    @Test
    fun `platform is a non-blank string`() {
        val platform = DeviceContext().platform
        assertTrue(platform.isNotBlank())
    }

    @Test
    fun `language is available when system property is set`() {
        val language = DeviceContext().language
        if (language != null) {
            assertTrue(language.isNotBlank())
        }
    }

    @Test
    fun `userAgent is available on JVM`() {
        val ua = DeviceContext().userAgent
        assertNotNull(ua)
        assertTrue(ua.isNotBlank())
    }
}
