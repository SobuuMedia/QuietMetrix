package com.quietmetrix.dashboard

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import kotlinx.browser.window

/**
 * API base URL resolution, in priority order:
 *   1. `?api=...` query param  → for local dev against a remote server
 *   2. Empty string             → same-origin: calls go to `/api/v1/...` on
 *                                  whatever host served `dashboard.js`. This is
 *                                  how the dashboard is meant to run in
 *                                  production behind both the PHP and Ktor
 *                                  backends, since both serve the bundle from
 *                                  `/dashboard/` and the API from `/api/v1/`.
 */
private fun resolveApiBase(): String {
    val search = window.location.search.removePrefix("?")
    if (search.isNotEmpty()) {
        for (pair in search.split("&")) {
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq) == "api") {
                return pair.substring(eq + 1)
            }
        }
    }
    return ""
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        App(resolveApiBase())
    }
}
