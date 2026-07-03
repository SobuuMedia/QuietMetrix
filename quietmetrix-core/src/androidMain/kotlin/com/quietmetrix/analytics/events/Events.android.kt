package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.EventValidator
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.SDK_VERSION
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.context.DeviceContext
import com.quietmetrix.analytics.internal.generateSid
import com.quietmetrix.analytics.internal.transport.ConnectivityMonitor
import com.quietmetrix.analytics.internal.transport.EnqueuedEvent
import com.quietmetrix.analytics.internal.transport.EventContext
import com.quietmetrix.analytics.internal.transport.EventQueue
import com.quietmetrix.analytics.internal.transport.SdkInfo
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
    val device = DeviceContext()
    EventQueue.enqueue(
        EnqueuedEvent(
            event = event,
            screen = screen,
            props = ScreenTracker.enrichWithDwell(props),
            sid = sid,
            ts = Clock.System.now(),
            wasOffline = isOffline,
            sdk = SdkInfo("android", SDK_VERSION),
            ctx = EventContext(
                language = device.language,
                ua = device.userAgent,
                country = device.country,
            ),
        )
    )
}
