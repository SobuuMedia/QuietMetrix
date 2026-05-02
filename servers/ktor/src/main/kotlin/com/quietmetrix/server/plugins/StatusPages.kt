package com.quietmetrix.server.plugins

import com.quietmetrix.server.domain.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.info("Unhandled: ${cause::class.simpleName}: ${cause.message}")
            call.application.environment.log.debug("Unhandled exception detail", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", "An unexpected error occurred")
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", "The requested resource was not found")
            )
        }

        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                ErrorResponse("method_not_allowed", "HTTP method not allowed for this endpoint")
            )
        }
    }
}