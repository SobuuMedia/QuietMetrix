package com.quietmetrix.server.plugins

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.routes.resolveApiPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import org.koin.ktor.ext.inject

/** Applies before routing, including handlers whose administrator role bypasses membership. */
fun Application.configureTenantBoundary(config: AppConfig) {
    if (!config.isCloud) return
    val users by inject<UserRepository>()
    val projects by inject<ProjectRepository>()
    val tokens by inject<AccessTokenRepository>()
    intercept(ApplicationCallPipeline.Call) {
        val segments = runCatching { java.net.URI(call.request.uri).path.trim('/').split('/') }.getOrNull() ?: emptyList()
        if (segments.size >= 4 && segments[0] == "api" && segments[1] in setOf("v1", "v2") && segments[2] in setOf("projects", "users")) {
            val principal = resolveApiPrincipal(call, config, tokens)
            val actor = principal?.let { users.findById(it.userId) }
            if (actor != null && actor["platformOperator"] != true) {
                val id = segments[3].removePrefix("proj_").toLongOrNull()
                val target = if (segments[2] == "projects") id?.let(projects::findById)?.get("ownerUserId")?.let { (it as Number).toLong() }?.let(users::findById)
                    else id?.let(users::findById)
                if (target != null && actor["tenantId"] != target["tenantId"]) {
                    call.respond(HttpStatusCode.NotFound, com.quietmetrix.server.domain.ErrorResponse("not_found", "Resource not found"))
                    finish()
                }
            }
        }
    }
}
