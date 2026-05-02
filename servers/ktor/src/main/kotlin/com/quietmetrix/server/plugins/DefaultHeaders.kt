package com.quietmetrix.server.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureDefaultHeaders() {
    install(DefaultHeaders) {
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        header("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'")
    }
}
