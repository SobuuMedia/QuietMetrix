package com.quietmetrix.server.auth

import com.auth0.jwt.interfaces.Payload
import com.quietmetrix.server.persistence.UserRepository

/** Old role-bearing access JWTs remain valid until expiry; refresh/unknown types never do. */
fun validAccessClaims(payload: Payload): Boolean =
    payload.getClaim("type").asString().let { it == null || it == "access" } &&
        payload.getClaim("role").asString() in setOf("admin", "developer", "reviewer") &&
        payload.getClaim("userId").asString()?.toLongOrNull() != null

fun UserRepository.acceptsSession(payload: Payload): Boolean {
    if (!validAccessClaims(payload)) return false
    val user = findById(payload.getClaim("userId").asString().toLong()) ?: return false
    if (payload.getClaim("type").asString() == "access") {
        val sessionId = payload.getClaim("sessionId").asString() ?: return false
        if (!sessionActive((user["id"] as Number).toLong(), sessionId)) return false
    }
    return user["status"] == "active" && user["role"] == payload.getClaim("role").asString()
}
