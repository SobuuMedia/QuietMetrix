package com.quietmetrix.analytics

expect suspend fun trackEvent(event: String, screen: String? = null, props: Map<String, Any?> = emptyMap())
