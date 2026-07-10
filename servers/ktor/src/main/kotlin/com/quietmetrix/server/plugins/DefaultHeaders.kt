package com.quietmetrix.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header

fun Application.configureDefaultHeaders() {
    // Security headers are set via an interceptor so HSTS can be conditional
    // on HTTPS (avoid sending it over plain HTTP).
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("X-Frame-Options", "DENY")
        call.response.headers.append("Referrer-Policy", "no-referrer")
        // The dashboard is a Compose/Kotlin-Wasm app: WebAssembly instantiation
        // needs 'wasm-unsafe-eval' in script-src, and Compose injects inline
        // styles (style-src 'unsafe-inline'). Everything else stays same-origin.
        call.response.headers.append(
            "Content-Security-Policy",
            "default-src 'self'; " +
                "script-src 'self' 'wasm-unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'",
        )

        val proto = call.request.header("X-Forwarded-Proto")
        val isSecure = proto == "https" || call.request.local.scheme == "https"
        if (isSecure) {
            call.response.headers.append("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }
}
