package com.quietmetrix.server.routes

import io.ktor.server.auth.jwt.JWTPrincipal

/**
 * Global role carried in the JWT (admin | developer | reviewer). Legacy tokens
 * issued before roles existed default to admin (tokens are short-lived and all
 * pre-existing users were admins).
 */
internal fun JWTPrincipal.roleClaim(): String =
    payload.getClaim("role").asString() ?: "admin"
