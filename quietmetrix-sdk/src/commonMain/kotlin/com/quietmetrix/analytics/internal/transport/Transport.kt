package com.quietmetrix.analytics.internal.transport

internal interface Transport {
    suspend fun send(endpoint: String, apiKey: String, events: List<EnqueuedEvent>): SendResult
}