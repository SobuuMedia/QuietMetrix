package com.quietmetrix.server.util

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * Returns the best-effort client IP address.
 *
 * If [trustedProxies] is non-empty and the direct remote address is in the
 * trusted set, the left-most value of `X-Forwarded-For` is used.
 * Otherwise the direct remote address is returned.
 *
 * This prevents clients from spoofing their IP by sending arbitrary
 * X-Forwarded-For headers when not behind a trusted proxy.
 */
fun ApplicationCall.clientIp(trustedProxies: Set<String> = emptySet()): String {
    val remoteAddr = request.local.remoteAddress
    if (trustedProxies.isNotEmpty() && remoteAddr in trustedProxies) {
        return request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim() ?: remoteAddr
    }
    return remoteAddr
}
