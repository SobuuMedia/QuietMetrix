package com.quietmetrix.server.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val ownerUserId: String,
    val writeKey: String,
    val readKey: String,
    val planId: Plan? = null,
    val createdAt: Instant,
)