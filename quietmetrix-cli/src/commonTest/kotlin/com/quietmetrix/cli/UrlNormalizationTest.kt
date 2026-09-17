package com.quietmetrix.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlNormalizationTest {

    @Test
    fun stripsTrailingSlashAndWhitespace() {
        assertEquals("https://qm.example.com", normalizeBaseUrl(" https://qm.example.com/ "))
    }

    @Test
    fun leavesAUrlWithoutATrailingSlashUnchanged() {
        assertEquals("https://qm.example.com", normalizeBaseUrl("https://qm.example.com"))
    }

    @Test
    fun derivesTheTrackingEndpoint() {
        assertEquals("https://qm.example.com/api/v1/track", trackingEndpoint("https://qm.example.com"))
    }

    @Test
    fun derivesTheTrackingEndpointFromATrailingSlashUrl() {
        assertEquals("https://qm.example.com/api/v1/track", trackingEndpoint("https://qm.example.com/"))
    }
}
