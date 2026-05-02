package com.quietmetrix.analytics.internal.transport

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

internal actual suspend fun platformSend(endpoint: String, apiKey: String, events: List<EnqueuedEvent>): SendResult {
    val client = HttpClient(engine = io.ktor.client.engine.winhttp.WinHttp)
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