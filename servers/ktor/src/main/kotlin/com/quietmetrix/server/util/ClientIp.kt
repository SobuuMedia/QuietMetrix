package com.quietmetrix.server.util

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * Returns the best-effort client IP address.
 *
 * If [trustedProxies] is non-empty and the direct remote address is in the
 * trusted set, the chain is inspected from the right until the first untrusted hop.
 * Otherwise the direct remote address is returned.
 *
 * This prevents clients from spoofing their IP by sending arbitrary
 * X-Forwarded-For headers when not behind a trusted proxy.
 */
fun ApplicationCall.clientIp(trustedProxies: Set<String> = emptySet()): String {
    val remoteAddr = request.local.remoteAddress
    if (trustedProxies.isNotEmpty() && remoteAddr in trustedProxies) {
        return validatedClientIp(remoteAddr, request.header("X-Forwarded-For"), trustedProxies)
    }
    return remoteAddr
}

internal fun validatedClientIp(remote: String, forwarded: String?, trusted: Set<String>): String {
    if (remote !in trusted || forwarded == null) return remote
    val hops = forwarded.split(',').map(String::trim)
    if (hops.size > 20 || hops.any { !it.matches(Regex("[0-9a-fA-F:.]+")) }) return remote
    return hops.asReversed().firstOrNull { it !in trusted } ?: remote
}
