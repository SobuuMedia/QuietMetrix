package com.quietmetrix.analytics.internal.transport

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Serializable
internal data class EnqueuedEvent(
    val event: String,
    val screen: String? = null,
    val props: Map<String, @Contextual Any?> = emptyMap(),
    val sid: String? = null,
    @Contextual val ts: Instant,
    val wasOffline: Boolean = false,
    val sdk: SdkInfo? = null,
    val ctx: EventContext? = null,
    @Contextual val enqueuedAt: Instant = Clock.System.now(),
    val osName: String? = null,
    val osVersion: String? = null,
    val browserName: String? = null,
    val browserVersion: String? = null,
    val deviceModel: String? = null,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val sessionNumber: Int? = null,
    val isSessionStart: Boolean = false,
    val isSessionEnd: Boolean = false,
)

@Serializable
internal data class SdkInfo(
    val platform: String,
    val version: String,
)

@Serializable
internal data class EventContext(
    val referrer: String? = null,
    val language: String? = null,
    val ua: String? = null,
    val viewport: String? = null,
    val country: String? = null,
    /**
     * Per-install pseudonymous id from [com.quietmetrix.analytics.internal.context.DeviceContext].
     * Sent raw; both servers immediately salt-hash it per project and never store the raw value.
     * It belongs in `ctx` — a top-level field is silently dropped by both ingests.
     */
    val anonymousId: String? = null,
)

internal data class SendResult(
    val success: Boolean,
    val statusCode: Int,
    val retryable: Boolean,
)
