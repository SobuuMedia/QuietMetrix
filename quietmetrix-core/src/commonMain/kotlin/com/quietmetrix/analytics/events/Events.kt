package com.quietmetrix.analytics

/** Fire-and-forget analytics event. Silently ignored if the tracker is unavailable or consent is declined. */
expect fun trackEvent(event: String, screen: String? = null)
