package com.quietmetrix.dashboard.ui.screens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Validates a practical invite address before the backend applies its authoritative checks. */
internal fun isInvitationEmailValid(value: String): Boolean {
    val email = value.trim()
    if (email.length > 254 || email.any { it.isWhitespace() } || email.count { it == '@' } != 1) return false
    val local = email.substringBefore('@')
    val domain = email.substringAfter('@')
    return local.isNotEmpty() && local.length <= 64 && !local.startsWith('.') && !local.endsWith('.') && ".." !in local &&
        domain.split('.').let { labels -> labels.size >= 2 && labels.all { label ->
            label.isNotEmpty() && label.length <= 63 && !label.startsWith('-') && !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' }
        } }
}

/** Shared pretty JSON format for copyable MCP configuration. */
private val mcpConfigJson = Json { prettyPrint = true }

/** Builds the supported stdio client configuration using a placeholder, never a session credential. */
internal fun mcpClientConfiguration(serverUrl: String): String = mcpConfigJson.encodeToString(
    JsonObject.serializer(), buildJsonObject {
        putJsonObject("mcpServers") {
            putJsonObject("quietmetrix") {
                put("command", "npx")
                putJsonArray("args") { add("-y"); add("@sobuumedia/quietmetrix-mcp") }
                putJsonObject("env") {
                    put("QUIETMETRIX_URL", serverUrl)
                    put("QUIETMETRIX_TOKEN", "qm_pat_your_token_here")
                }
            }
        }
    },
)
