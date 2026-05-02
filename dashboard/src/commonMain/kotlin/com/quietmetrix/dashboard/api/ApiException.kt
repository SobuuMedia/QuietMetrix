package com.quietmetrix.dashboard.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Thrown by [ApiClient] when the server returns a non-2xx response. The
 * caller can branch on [status] (HTTP code) and [code] (server-side error
 * tag like `"unauthorized"` or `"misconfigured"`) to choose a user-facing
 * message, without parsing free-text English from the underlying transport.
 */
class ApiException(
    val status: Int,
    val code: String? = null,
    val serverMessage: String? = null,
    cause: Throwable? = null,
) : RuntimeException("API error $status: ${code ?: "no-code"}", cause)

@Serializable
internal data class ErrorBody(
    val error: String? = null,
    val message: String? = null,
    @SerialName("debug") val debug: kotlinx.serialization.json.JsonElement? = null,
)
