package com.quietmetrix.cli

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shape of the server's POST /api/v1/projects response — only the fields the CLI reads. */
@Serializable
internal data class CreateProjectApiResponse(
    val id: String? = null,
    val name: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
    @SerialName("api_key_last4") val apiKeyLast4: String? = null,
    val message: String? = null,
)

/** Shape of the server's GET /api/v1/projects response — only the fields the CLI reads. */
@Serializable
internal data class ListProjectsApiResponse(
    val projects: List<ProjectSummary> = emptyList(),
    val total: Int = 0,
)

@Serializable
internal data class ProjectSummary(
    val id: String,
    val name: String,
    @SerialName("api_key_last4") val apiKeyLast4: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** JSON the CLI prints to stdout for `project create` — the contract an agent parses. */
@Serializable
data class ProjectCreateOutput(
    val id: String? = null,
    val name: String,
    @SerialName("api_key") val apiKey: String? = null,
    @SerialName("api_key_last4") val apiKeyLast4: String? = null,
    @SerialName("tracking_endpoint") val trackingEndpoint: String,
    val message: String? = null,
)

@Serializable
data class ErrorOutput(val error: String, val message: String)
