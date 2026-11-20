package com.quietmetrix.analytics.internal.transport

internal actual suspend fun platformGet(endpoint: String, apiKey: String): String? =
    browserFetch(endpoint, apiKey, body = null, readBody = true)
