package com.quietmetrix.analytics.internal.transport

internal actual suspend fun platformPost(endpoint: String, apiKey: String, body: String): Boolean =
    browserFetch(endpoint, apiKey, body, readBody = false) != null
