package com.quietmetrix.server.billing

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AdyenWebhookVerifier(private val hmacKey: String) {

    private val signedFieldNames = listOf(
        "pspReference",
        "originalReference",
        "merchantAccountCode",
        "merchantReference",
        "amount.value",
        "amount.currency",
        "eventCode",
        "success",
    )

    fun verify(payload: Map<*, *>): Result {
        val additionalData = payload["additionalData"] as? Map<*, *>
            ?: return Result.Invalid("Missing additionalData in Adyen webhook")

        val receivedHmac = additionalData["hmacSignature"] as? String
            ?: return Result.Invalid("Missing hmacSignature in Adyen webhook")

        val signingString = buildSigningString(payload)
        val expectedHmac = hmacSha256(signingString, hmacKey)
        val expectedBase64 = java.util.Base64.getEncoder().encodeToString(
            expectedHmac.toByteArray(Charsets.UTF_8)
        )

        if (!constantTimeEquals(expectedBase64, receivedHmac)) {
            return Result.Invalid("HMAC signature mismatch")
        }

        return Result.Valid
    }

    private fun buildSigningString(payload: Map<*, *>): String {
        val parts = mutableListOf<String>()
        val amount = payload["amount"] as? Map<*, *>

        for (fieldName in signedFieldNames) {
            when {
                fieldName == "amount.value" -> {
                    val value = (amount?.get("value")?.toString() ?: "")
                    parts.add(value)
                }
                fieldName == "amount.currency" -> {
                    val currency = (amount?.get("currency")?.toString() ?: "")
                    parts.add(currency)
                }
                fieldName == "success" -> {
                    val success = when (payload["success"]) {
                        true, "true" -> "true"
                        else -> "false"
                    }
                    parts.add(success)
                }
                else -> {
                    val value = (payload[fieldName]?.toString() ?: "")
                    parts.add(value)
                }
            }
        }

        return parts.joinToString(":")
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
