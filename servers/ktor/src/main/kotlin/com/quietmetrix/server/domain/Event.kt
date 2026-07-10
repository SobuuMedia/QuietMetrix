package com.quietmetrix.server.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String? = null,
    val projectId: String,
    val eventName: String,
    val screen: String? = null,
    val props: String? = null,
    val sid: String? = null,
    val ts: Instant,
    val wasOffline: Boolean = false,
    val country: String? = null,
    val deviceClass: String? = null,
    val language: String? = null,
    val platform: String? = null,
    val sdkVersion: String? = null,
    val receivedAt: Instant? = null,
    val anonymousId: String? = null,
    val city: String? = null,
    val region: String? = null,
    val browser: String? = null,
    val browserVersion: String? = null,
    val os: String? = null,
    val osVersion: String? = null,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val durationMs: Long? = null,
    val referrer: String? = null,
    val sessionNumber: Int? = null,
    val isSessionStart: Boolean = false,
    val isSessionEnd: Boolean = false,
)