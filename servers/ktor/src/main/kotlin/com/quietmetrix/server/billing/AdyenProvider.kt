package com.quietmetrix.server.billing

import com.quietmetrix.server.config.BillingConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AdyenProvider(
    private val config: BillingConfig,
) : PaymentProvider {

    override val name: String = "adyen"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createCheckoutSession(
        projectId: String,
        planId: String,
        successUrl: String?,
        cancelUrl: String?,
        returnUrl: String?,
    ): CheckoutResult {
        val amount = planToAmount(planId)
        val reference = "qm_${projectId}_${System.currentTimeMillis()}"

        // In production, this would call the Adyen Checkout API
        // For now, return a placeholder URL
        return CheckoutResult(
            checkoutUrl = "https://checkout-test.adyen.com/checkout/pay?ref=$reference",
            providerSessionId = reference,
        )
    }

    override suspend fun handleWebhook(payload: String, signature: String?): WebhookResult {
        val hmacKey = config.adyenHmacKey ?: return WebhookResult(
            processed = false,
            eventType = "no_hmac_key"
        )

        val isValid = verifyHmac(payload, signature ?: "", hmacKey)
        if (!isValid) {
            return WebhookResult(processed = false, eventType = "hmac_verification_failed")
        }

        return try {
            val jsonElement = json.parseToJsonElement(payload)
            val eventType = jsonElement.jsonObject["eventType"]?.jsonPrimitive?.content ?: "unknown"

            WebhookResult(
                processed = true,
                eventType = eventType,
                projectId = jsonElement.jsonObject["merchantReference"]?.jsonPrimitive?.content,
            )
        } catch (_: Exception) {
            WebhookResult(processed = false, eventType = "parse_error")
        }
    }

    private fun verifyHmac(payload: String, signature: String, key: String): Boolean {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"))
            val computed = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
            computed == signature
        } catch (_: Exception) {
            false
        }
    }

    private fun planToAmount(planId: String): Int {
        return when (planId) {
            "hobby" -> 900      // $9.00 in cents
            "startup" -> 2900   // $29.00 in cents
            "business" -> 9900  // $99.00 in cents
            else -> throw IllegalArgumentException("Unknown plan: $planId")
        }
    }
}