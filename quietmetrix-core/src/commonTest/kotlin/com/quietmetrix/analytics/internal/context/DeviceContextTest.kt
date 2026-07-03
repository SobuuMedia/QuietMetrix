package com.quietmetrix.analytics.internal.context

import kotlin.test.Test
import kotlin.test.assertTrue

class DeviceContextTest {

    @Test
    fun `platform is a non-blank string`() {
        val platform = DeviceContext().platform
        assertTrue(platform.isNotBlank())
    }

    @Test
    fun `language is non-blank when present`() {
        val language = DeviceContext().language
        if (language != null) {
            assertTrue(language.isNotBlank())
        }
    }

    @Test
    fun `userAgent is non-blank when present`() {
        val ua = DeviceContext().userAgent
        if (ua != null) {
            assertTrue(ua.isNotBlank())
        }
    }
}
