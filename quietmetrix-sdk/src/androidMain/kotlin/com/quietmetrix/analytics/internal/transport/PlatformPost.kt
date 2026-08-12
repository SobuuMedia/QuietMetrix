package com.quietmetrix.analytics.internal.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal actual suspend fun platformPost(endpoint: String, apiKey: String, body: String): Boolean {
    val client = HttpClient(Android)
    try {
        val response = client.post(endpoint) {
            header("X-QM-Api-Key", apiKey)
            header("Content-Type", "application/json")
            setBody(body)
        }
        return response.status.value in 200..299
    } catch (_: Exception) {
        return false
    } finally {
        client.close()
    }
}
