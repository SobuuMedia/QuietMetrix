package com.quietmetrix.analytics.internal.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal actual suspend fun platformSend(endpoint: String, apiKey: String, events: List<EnqueuedEvent>): SendResult {
    // Explicit engine, not the no-arg HttpClient(): the no-arg constructor resolves the engine via
    // ServiceLoader, which R8/minification strips in consumers' release builds, silently breaking
    // all sends. An explicitly-referenced engine is reachable code and survives R8 deterministically.
    val client = HttpClient(Android)
    try {
        val payload = HttpTransport.serializeBatch(events)
        val response = client.post(endpoint) {
            header("X-QM-Api-Key", apiKey)
            header("Content-Type", "application/json")
            setBody(payload)
        }
        return when (response.status.value) {
            202 -> SendResult(success = true, statusCode = 202, retryable = false)
            401, 400, 403, 404, 413 -> SendResult(success = false, statusCode = response.status.value, retryable = false)
            else -> SendResult(success = false, statusCode = response.status.value, retryable = response.status.value in 500..599)
        }
    } catch (_: Exception) {
        return SendResult(success = false, statusCode = 0, retryable = true)
    } finally {
        client.close()
    }
}