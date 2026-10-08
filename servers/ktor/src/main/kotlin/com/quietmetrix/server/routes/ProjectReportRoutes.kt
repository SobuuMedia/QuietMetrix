package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectReportRepository
import com.quietmetrix.server.persistence.ProjectRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.ZoneOffset

/** Typed v2 dashboard report entry point; access checks match the legacy analytics reads. */
fun Routing.configureProjectReportRoutes() {
    val projects by inject<ProjectRepository>()
    val members by inject<ProjectMemberRepository>()
    val tokens by inject<AccessTokenRepository>()
    val reports by inject<ProjectReportRepository>()
    val config by inject<AppConfig>()
    route("/api/v2/projects/{projectId}/report") {
        get {
            val idText = call.parameters["projectId"].orEmpty()
            val id = idText.removePrefix("proj_").toLongOrNull()
            if (id == null || !idText.startsWith("proj_")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid project id")); return@get
            }
            val principal = resolveApiPrincipal(call, config, tokens)
            if (principal == null) { call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Authentication required")); return@get }
            if (!principal.canReadAnalytics()) { call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Analytics read access required")); return@get }
            val project = projects.findById(id)
            if (project == null) { call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found")); return@get }
            val owner = (project["ownerUserId"] as? Number)?.toLong() == principal.userId
            if (!principal.isAdmin() && !owner && members.findMembership(id, principal.userId) == null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project")); return@get
            }
            val range = call.request.queryParameters["range"] ?: "7d"
            if (range !in setOf("today", "7d", "30d", "90d")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "range must be today, 7d, 30d, or 90d")); return@get
            }
            val today = LocalDate.now(ZoneOffset.UTC)
            val anchorRaw = call.request.queryParameters["anchor"]
            val anchor = if (anchorRaw == null) today else runCatching { LocalDate.parse(anchorRaw) }.getOrNull()
            if (anchor == null || (anchorRaw != null && anchor.toString() != anchorRaw) || anchor.isAfter(today)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "anchor must be a real UTC date no later than today")); return@get
            }
            val platform = call.request.queryParameters["platform"] ?: "all"
            if (platform !in setOf("all", "android", "ios", "web", "desktop")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid platform")); return@get
            }
            val compareRaw = call.request.queryParameters["compare"] ?: "false"
            if (compareRaw !in setOf("true", "false")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "compare must be true or false")); return@get
            }
            call.respond(reports.read(id, range, anchor, platform, compareRaw == "true"))
        }
    }
}
