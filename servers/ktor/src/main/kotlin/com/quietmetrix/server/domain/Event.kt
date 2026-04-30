package com.quietmetrix.server.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String? = null,
    val projectId: String,
    val eventName: String,
    val screen: String? = null,
    val props: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val sid: String? = null,
    val ts: Instant,
    val wasOffline: Boolean = false,
    val country: String? = null,
    val deviceClass: String? = null,
    val language: String? = null,
    val platform: String? = null,
    val sdkVersion: String? = null,
    val receivedAt: Instant? = null,
)