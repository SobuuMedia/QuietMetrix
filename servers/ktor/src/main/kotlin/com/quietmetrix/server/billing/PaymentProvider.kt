package com.quietmetrix.server.billing

interface PaymentProvider {
    val name: String
    suspend fun createCheckoutSession(
        projectId: String,
        planId: String,
        successUrl: String?,
        cancelUrl: String?,
        returnUrl: String?,
    ): CheckoutResult

    suspend fun handleWebhook(payload: String, signature: String?): WebhookResult
}

data class CheckoutResult(
    val checkoutUrl: String,
    val providerSessionId: String? = null,
)

data class WebhookResult(
    val processed: Boolean,
    val eventType: String? = null,
    val projectId: String? = null,
)