package com.quietmetrix.server.routes

import com.quietmetrix.server.ai.AiAccounting
import com.quietmetrix.server.ai.AiDenied
import com.quietmetrix.server.ai.AiRequest
import com.quietmetrix.server.ai.AiService
import com.quietmetrix.server.ask.AskAnalyticsAnalyzer
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.retention.RetentionCounterCell
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.ktor.ext.inject
import java.security.MessageDigest
import com.quietmetrix.server.util.receiveBounded as receive

/** AI routes bind every tool and cache read to an authorized project and its owner account. */
fun Routing.configureAskRoutes() {
    val counterRepo by inject<CounterRepository>()
    val tokenRepo by inject<AccessTokenRepository>()
    val projectRepo by inject<ProjectRepository>()
    val memberRepo by inject<ProjectMemberRepository>()
    val config by inject<AppConfig>()
    val accounting by inject<AiAccounting>()
    val service by inject<AiService>()
    route("/api/v1/_operator/ai/{resource}/{id?}") {
        handle {
            val supplied = call.request.headers["Authorization"]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ") ?: ""
            if(config.ai.operatorKey.isBlank() || !MessageDigest.isEqual(supplied.toByteArray(), config.ai.operatorKey.toByteArray())) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Operator credential required")); return@handle
            }
            try {
                val body = if(call.request.local.method.value == "PUT") call.receive<JsonObject>() else if(call.request.local.method.value == "GET") null else throw IllegalArgumentException()
                call.respond(withContext(Dispatchers.IO) { accounting.operator(call.parameters["resource"]!!, call.parameters["id"], body) })
            } catch(e: Exception) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid operator request")) }
        }
    }
    route("/api/v1/projects/{projectId}") {
        for(endpoint in listOf("/ai/capabilities", "/ai/insight", "/ai/ask", "/ask")) {
            route(endpoint) {
                handle {
                    val capabilities = endpoint.endsWith("capabilities")
                    if(call.request.local.method.value != if(capabilities) "GET" else "POST") { call.respond(HttpStatusCode.MethodNotAllowed); return@handle }
                    val projectId = call.parameters["projectId"]?.removePrefix("proj_")?.toLongOrNull() ?: run { call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid project id")); return@handle }
                    val principal = resolveApiPrincipal(call, config, tokenRepo) ?: run { call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing or invalid authorization")); return@handle }
                    if(!principal.canReadAnalytics()) { call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "This token cannot read analytics")); return@handle }
                    val project = projectRepo.findById(projectId) ?: run { call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Project not found")); return@handle }
                    val owner = (project["ownerUserId"] as Number).toLong()
                    if(owner != principal.userId && memberRepo.findMembership(projectId, principal.userId) == null && (config.isCloud || !principal.isAdmin())) { call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "You cannot access this project")); return@handle }
                    if(capabilities) { call.respond(withContext(Dispatchers.IO) { accounting.capabilities(owner.toString()) }); return@handle }
                    try {
                        val request = call.receive<AiRequest>()
                        require(request.range in setOf("1h", "1d", "7d", "30d", "90d"))
                        val window = analyticsWindow(parseRangeSeconds(request.range), request.period, request.anchor)
                        val metrics = mapOf("aggregates" to listOf("event", "value"), "sessions" to listOf("session"), "retention" to listOf("retention", "activation"), "transitions" to listOf("screen_transition", "screen_dwell"), "search" to listOf("search", "search_zero_result"), "friction" to listOf("friction"), "funnels" to listOf("funnel_step"), "experiments" to listOf("experiment"))
                        val snapshot = buildJsonObject {
                            put("anchor", request.anchor ?: window.toDay.toString())
                            metrics.forEach { (name, reads) -> put(name, buildJsonObject {
                                put("from", window.fromDay.toString()); put("to", window.toDay.toString())
                                put("current", buildJsonObject { reads.forEach { metric -> put(metric, aiReportableCells(counterRepo, projectId, metric, window)) } })
                                if(request.compare) {
                                    val previous = analyticsWindow(parseRangeSeconds(request.range), "previous", window.toDay.toString())
                                    put("previous_from", previous.fromDay.toString()); put("previous_to", previous.toDay.toString())
                                    put("previous", buildJsonObject { reads.forEach { metric -> put(metric, aiReportableCells(counterRepo, projectId, metric, previous)) } })
                                }
                            }) }
                        }
                        val sessions = counterRepo.readCells(projectId, "session", window.fromDay, window.toDay)
                        val computed = AskAnalyticsAnalyzer.answer("sessions", sessions, emptyList(), emptyList(), emptyList(), emptyList<RetentionCounterCell>(), request.range).text
                        val result = withContext(Dispatchers.IO) { service.generate(owner.toString(), projectId.toString(), request, endpoint == "/ai/insight", snapshot, computed) }
                        call.respond(if(endpoint == "/ask") buildJsonObject { put("answer", result["answer"]!!); put("source", result["sources"]!!.jsonArray.joinToString(" · ") { it.jsonPrimitive.content }) } else result)
                    } catch(e: AiDenied) {
                        val status = when(e.code) { "ai_unavailable" -> HttpStatusCode.NotFound; "ai_allowance_exhausted", "ai_rate_limited" -> HttpStatusCode.TooManyRequests; "ai_operation_pending", "ai_operation_conflict", "ai_operation_expired" -> HttpStatusCode.Conflict; else -> HttpStatusCode.ServiceUnavailable }
                        call.respond(status, ErrorResponse(e.code, when(e.code) { "ai_allowance_exhausted" -> "Your plan’s AI allowance has been reached."; "ai_provider_failed" -> "AI could not answer. Please retry."; else -> "AI is unavailable." }))
                    } catch(e: IllegalArgumentException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid AI request")) }
                    catch(e: kotlinx.serialization.SerializationException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid AI request")) }
                    catch(e: io.ktor.server.plugins.BadRequestException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid AI request")) }
                    catch(e: Exception) {
                        if(endpoint == "/ai/insight") call.respond(buildJsonObject { put("text","There isn't enough reportable analytics data to summarize yet.");put("mode","computed");put("sources",buildJsonArray {});put("generated_at",java.time.Instant.now().toString()) })
                        else call.respond(HttpStatusCode.ServiceUnavailable,ErrorResponse("ai_accounting_unavailable","AI is unavailable."))
                    }
                }
            }
        }
    }
}

/** Reuses the existing privacy gate, never truncates JSON, and filters cohort scope. */
internal fun aiReportableCells(repo: CounterRepository, project: Long, metric: String, window: AnalyticsWindow): JsonArray {
    val cells = repo.readCells(project, metric, window.fromDay, window.toDay).filter { cell ->
        if(metric != "retention" && metric != "activation") true else cell.dims["cohort"]?.let { reportedCohortStart(it) }?.let { it >= window.fromDay && it <= window.toDay } ?: false
    }
    return buildJsonArray { cells.forEach { cell -> add(buildJsonObject { put("dims", buildJsonObject { cell.dims.forEach { (key,value) -> put(key,value) } }); put("n", cell.n) }) } }
}
