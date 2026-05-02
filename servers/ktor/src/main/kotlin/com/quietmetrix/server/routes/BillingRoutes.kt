package com.quietmetrix.server.routes

import com.quietmetrix.server.billing.AdyenWebhookVerifier
import com.quietmetrix.server.billing.StripeWebhookVerifier
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.domain.ErrorResponse
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.ratelimit.QuotaEnforcer
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Routing.configureBillingRoutes(config: AppConfig) {
    if (!config.isCloud) return

    val projectRepo by inject<ProjectRepository>()
    val userRepo by inject<UserRepository>()
    val quotaEnforcer by inject<QuotaEnforcer>()

    val stripeVerifier = config.billing?.stripeWebhookSecret?.let {
        StripeWebhookVerifier(it)
    }
    val adyenVerifier = config.billing?.adyenHmacKey?.let {
        AdyenWebhookVerifier(it)
    }

    route("/api/v1/billing") {
        authenticate("auth-jwt") {
            post("/checkout") {
                val userId = (call.principal<JWTPrincipal>() ?: return@post).payload.getClaim("userId").asString()
                val user = userRepo.findById(userId.toLong())
                val planId = user?.get("planId") as? String ?: "free"

                val endpoint = quotaEnforcer.createCheckoutSession(userId, planId)
                call.respond(mapOf("url" to endpoint))
            }

            get("/usage") {
                val principal = call.principal<JWTPrincipal>() ?: return@get
                val userId = principal.payload.getClaim("userId").asString()
                val user = userRepo.findById(userId.toLong())
                val planId = user?.get("planId") as? String

                val projectsUsed = projectRepo.countByOwnerId(userId.toLong())
                val projectsLimit = quotaEnforcer.getProjectLimit(planId)
                val eventsUsed = projectRepo.countAccessibleByUserId(userId.toLong())
                val eventsLimit = quotaEnforcer.getEventLimit(planId)

                call.respond(mapOf(
                    "projects_used" to projectsUsed,
                    "projects_limit" to projectsLimit,
                    "events_used" to eventsUsed,
                    "events_limit" to eventsLimit,
                    "plan" to (planId ?: "selfhost"),
                ))
            }
        }

        post("/webhook/stripe") {
            try {
                val sig = call.request.header("Stripe-Signature") ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing Stripe-Signature header"))
                    return@post
                }
                val body = call.receiveText()

                val verifier = stripeVerifier ?: run {
                    call.application.environment.log.error("Stripe webhook called but no webhook secret configured")
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Webhook not configured"))
                    return@post
                }
                when (val result = verifier.verify(body, sig)) {
                    is StripeWebhookVerifier.Result.Valid -> {
                        call.application.environment.log.info("Stripe webhook verified successfully")
                        call.respond(mapOf("received" to true, "provider" to "stripe"))
                    }
                    is StripeWebhookVerifier.Result.Invalid -> {
                        call.application.environment.log.warn("Stripe webhook verification failed: ${result.reason}")
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_signature", result.reason))
                    }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("webhook_error", "Invalid webhook payload"))
            }
        }

        post("/webhook/adyen") {
            try {
                val body = call.receiveText()
                val payload = try {
                    kotlinx.serialization.json.Json.parseToJsonElement(body).let {
                        @Suppress("UNCHECKED_CAST")
                        if (it is kotlinx.serialization.json.JsonObject) {
                            it.toMap()
                        } else null
                    }
                } catch (_: Exception) {
                    null
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("webhook_error", "Invalid JSON payload"))
                    return@post
                }

                val verifier = adyenVerifier ?: run {
                    call.application.environment.log.error("Adyen webhook called but no HMAC key configured")
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Webhook not configured"))
                    return@post
                }
                when (val result = verifier.verify(payload)) {
                    is AdyenWebhookVerifier.Result.Valid -> {
                        call.application.environment.log.info("Adyen webhook verified successfully")
                        call.respond(mapOf("received" to true, "provider" to "adyen"))
                    }
                    is AdyenWebhookVerifier.Result.Invalid -> {
                        call.application.environment.log.warn("Adyen webhook verification failed: ${result.reason}")
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_signature", result.reason))
                    }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("webhook_error", "Invalid webhook payload"))
            }
        }
    }
}
