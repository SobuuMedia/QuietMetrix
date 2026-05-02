package com.quietmetrix.server.billing

import com.quietmetrix.server.config.BillingConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StripeProvider(
    private val config: BillingConfig,
) : PaymentProvider {

    override val name: String = "stripe"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createCheckoutSession(
        projectId: String,
        planId: String,
        successUrl: String?,
        cancelUrl: String?,
        returnUrl: String?,
    ): CheckoutResult {
        val priceId = planToPriceId(planId)
        val reference = "cs_${projectId}_${planId}_${System.currentTimeMillis()}"

        return CheckoutResult(
            checkoutUrl = "https://checkout.stripe.com/pay/$reference?price=$priceId&success_url=${successUrl ?: "https://app.example.com/billing/success"}&cancel_url=${cancelUrl ?: "https://app.example.com/billing/cancel"}",
            providerSessionId = reference,
        )
    }

    override suspend fun handleWebhook(payload: String, signature: String?): WebhookResult {
        if (config.stripeWebhookSecret == null) {
            return WebhookResult(processed = false, eventType = "no_webhook_secret")
        }

        return try {
            val jsonElement = json.parseToJsonElement(payload)
            val eventType = jsonElement.jsonObject["type"]?.jsonPrimitive?.content ?: "unknown"
            val metadata = jsonElement.jsonObject["data"]?.jsonObject?.get("object")?.jsonObject?.get("metadata")?.jsonObject
            val projectId = metadata?.get("project_id")?.jsonPrimitive?.content

            when (eventType) {
                "checkout.session.completed",
                "customer.subscription.created",
                "customer.subscription.updated",
                "customer.subscription.deleted" -> {
                    WebhookResult(processed = true, eventType = eventType, projectId = projectId)
                }
                else -> WebhookResult(processed = true, eventType = eventType)
            }
        } catch (_: Exception) {
            WebhookResult(processed = false, eventType = "parse_error")
        }
    }

    private fun planToPriceId(planId: String): String {
        return when (planId) {
            "hobby" -> System.getenv("STRIPE_HOBBY_PRICE_ID") ?: "price_hobby"
            "startup" -> System.getenv("STRIPE_STARTUP_PRICE_ID") ?: "price_startup"
            "business" -> System.getenv("STRIPE_BUSINESS_PRICE_ID") ?: "price_business"
            else -> throw IllegalArgumentException("Unknown plan: $planId")
        }
    }
}