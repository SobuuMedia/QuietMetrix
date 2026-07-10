package com.quietmetrix.dashboard

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * API base URL resolution, in priority order:
 *   1. `?api=...` query param  → for local dev against a remote server
 *   2. Empty string             → same-origin: calls go to `/api/v1/...` on
 *                                  whatever host served `dashboard.js`. This is
 *                                  how the dashboard is meant to run in
 *                                  production behind both the PHP and Ktor
 *                                  backends, since both serve the bundle from
 *                                  `/dashboard/` and the API from `/api/v1`.
 *
 * Query-param overrides are restricted to a whitelist to prevent token
 * exfiltration (e.g. `?api=https://attacker.com` would steal JWTs).
 */
private fun resolveApiBase(): String {
    val search = window.location.search.removePrefix("?")
    if (search.isNotEmpty()) {
        for (pair in search.split("&")) {
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq) == "api") {
                val candidate = pair.substring(eq + 1)
                if (isAllowedApiBase(candidate)) {
                    return candidate
                }
            }
        }
    }
    return ""
}

/** Allowed API base URLs from query-param override. */
private fun isAllowedApiBase(url: String): Boolean {
    // Same-origin is always safe and handled by empty string.
    if (url.isEmpty()) return true
    // Local dev hosts
    if (url.startsWith("http://localhost:")) return true
    if (url.startsWith("https://localhost:")) return true
    if (url.startsWith("http://127.0.0.1:")) return true
    if (url.startsWith("https://127.0.0.1:")) return true
    // Production runs same-origin (empty string above), so no host is hardcoded
    // here. The `?api=` override is a local-dev convenience only.
    return false
}

/**
 * Reads the `?invite=<token>` query param, if present. The token is sent only to
 * this same-origin backend (no SSRF surface), but we still restrict it to a
 * plausible token shape so junk values don't trigger requests.
 */
private fun resolveInviteToken(): String? {
    val search = window.location.search.removePrefix("?")
    if (search.isEmpty()) return null
    for (pair in search.split("&")) {
        val eq = pair.indexOf('=')
        if (eq > 0 && pair.substring(0, eq) == "invite") {
            val candidate = pair.substring(eq + 1)
            if (candidate.isNotEmpty() && candidate.length <= 256 && candidate.all { it.isLetterOrDigit() }) {
                return candidate
            }
        }
    }
    return null
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App(resolveApiBase(), resolveInviteToken())
    }
}
