package com.quietmetrix.analytics.internal.transport

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

internal actual suspend fun platformGet(endpoint: String, apiKey: String): String? {
    val client = HttpClient(io.ktor.client.engine.winhttp.WinHttp)
    try {
        val response = client.get(endpoint) {
            header("X-QM-Api-Key", apiKey)
        }
        return if (response.status.value in 200..299) response.bodyAsText() else null
    } catch (_: Exception) {
        return null
    } finally {
        client.close()
    }
}
