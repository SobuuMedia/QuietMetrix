package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.EventValidator
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.SDK_VERSION
import com.quietmetrix.analytics.internal.generateSid
import com.quietmetrix.analytics.internal.transport.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual suspend fun trackEvent(event: String, screen: String?, props: Map<String, Any?>) {
    if (!Gate.shouldTrack()) return
    val errors = EventValidator.validate(event, screen, props)
    if (errors.isNotEmpty()) return
    val config = ConfigHolder.config
    val isOffline = !ConnectivityMonitor().isOnline
    val sid = generateSid(config.storageKeyPrefix)
    EventQueue.enqueue(
        EnqueuedEvent(
            event = event,
            screen = screen,
            props = props,
            sid = sid,
            ts = Clock.System.now(),
            wasOffline = isOffline,
            sdk = SdkInfo("windows", SDK_VERSION),
            ctx = null,
        )
    )
}
