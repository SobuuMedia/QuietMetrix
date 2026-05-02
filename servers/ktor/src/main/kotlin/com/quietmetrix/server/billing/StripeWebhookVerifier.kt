package com.quietmetrix.server.billing

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class StripeWebhookVerifier(private val webhookSecret: String) {

    private val toleranceSeconds = 300L // 5-minute replay window

    fun verify(payload: String, signatureHeader: String): Result {
        val parts = signatureHeader.split(",").associate { part ->
            val (k, v) = part.split("=", limit = 2)
            k.trim() to v.trim()
        }

        val timestamp = parts["t"]?.toLongOrNull()
            ?: return Result.Invalid("Missing or invalid timestamp in Stripe-Signature")
        val receivedSignature = parts["v1"]
            ?: return Result.Invalid("Missing v1 signature in Stripe-Signature")

        val now = System.currentTimeMillis() / 1000
        if (kotlin.math.abs(now - timestamp) > toleranceSeconds) {
            return Result.Invalid("Webhook timestamp outside tolerance window")
        }

        val signedPayload = "$timestamp.$payload"
        val expectedSignature = hmacSha256(signedPayload, webhookSecret)

        if (!constantTimeEquals(expectedSignature, receivedSignature)) {
            return Result.Invalid("Signature mismatch")
        }

        return Result.Valid
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    sealed class Result {
        data object Valid : Result()
        data class Invalid(val reason: String) : Result()
    }
}
