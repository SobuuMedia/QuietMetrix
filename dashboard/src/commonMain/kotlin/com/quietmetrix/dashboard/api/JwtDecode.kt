package com.quietmetrix.dashboard.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val B64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** Decodes a base64url segment (no padding required) to its byte string. */
private fun base64UrlDecodeToString(input: String): String? {
    val clean = input.replace('-', '+').replace('_', '/').trimEnd('=')
    val out = StringBuilder()
    var buffer = 0
    var bits = 0
    for (c in clean) {
        val v = B64_ALPHABET.indexOf(c)
        if (v < 0) return null
        buffer = (buffer shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.append(((buffer shr bits) and 0xFF).toChar())
        }
    }
    return out.toString()
}

/**
 * Reconstructs the signed-in user from a JWT access token's payload, so a
 * restored session (page reload) knows the user's role even when no stored user
 * object is available. Handles both backends' claim names: PHP uses `sub`, Ktor
 * uses `userId`; both carry `email` and `role`.
 *
 * This is a non-verifying decode — it only reads claims for UI gating. The
 * server still enforces every role-restricted action.
 */
fun decodeUserFromJwt(token: String): ApiUser? {
    val parts = token.split('.')
    if (parts.size < 2) return null
    val payload = base64UrlDecodeToString(parts[1]) ?: return null
    val obj = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
    val id = (obj["sub"] ?: obj["userId"])?.jsonPrimitive?.contentOrNull ?: return null
    val email = obj["email"]?.jsonPrimitive?.contentOrNull ?: ""
    val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: UserRole.ADMIN
    return ApiUser(id = id, email = email, role = role)
}
