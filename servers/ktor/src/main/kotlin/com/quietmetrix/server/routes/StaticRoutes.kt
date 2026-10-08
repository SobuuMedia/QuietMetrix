package com.quietmetrix.server.routes

import io.ktor.server.http.content.staticResources
import io.ktor.server.request.queryString
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

/**
 * Serves the Compose Multiplatform wasmJs dashboard from JAR resources under
 * the `/dashboard/` URL prefix. The bundle is dropped into
 * `src/main/resources/dashboard/` by the `:dashboard:copyDashboardToKtor`
 * Gradle task; if that task has not run, the directory is empty and
 * `/dashboard/` returns 404 (intentionally — better than serving stale assets).
 *
 * `/` redirects to `/dashboard/` so visiting the bare host opens the app.
 */
fun Routing.configureStaticDashboardRoutes() {
    // The index references assets relatively. Without this canonical redirect,
    // `/dashboard` serves index.html but the browser requests `/dashboard.js`.
    get("/dashboard") {
        val query = call.request.queryString().takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
        call.respondRedirect("/dashboard/$query", permanent = false)
    }

    staticResources(remotePath = "/dashboard", basePackage = "dashboard") {
        default("index.html")
    }

    get("/") {
        val query = call.request.queryString().takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
        call.respondRedirect("/dashboard/$query", permanent = false)
    }
}
