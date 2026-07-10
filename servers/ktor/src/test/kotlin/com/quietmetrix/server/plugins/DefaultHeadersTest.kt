package com.quietmetrix.server.plugins

import io.ktor.client.request.get
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultHeadersTest {

    @Test
    fun `CSP allows wasm and inline styles for the dashboard`() = testApplication {
        application {
            configureDefaultHeaders()
            routing { get("/dashboard/") { call.respondText("<html></html>") } }
        }
        val csp = client.get("/dashboard/").headers["Content-Security-Policy"]
        assertNotNull(csp, "CSP header must be present")
        // Kotlin/Wasm (skiko) needs wasm compilation; Compose injects inline styles.
        assertTrue(csp.contains("'wasm-unsafe-eval'"), "script-src must allow wasm: $csp")
        assertTrue(csp.contains("style-src 'self' 'unsafe-inline'"), "style-src must allow inline: $csp")
        assertTrue(csp.contains("frame-ancestors 'none'"), "clickjacking protection must remain: $csp")
    }
}
