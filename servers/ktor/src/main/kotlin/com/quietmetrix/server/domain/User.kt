package com.quietmetrix.server.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant,
)