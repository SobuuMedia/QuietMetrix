package com.quietmetrix.cli.http

/** A single HTTP response: status code and raw body text. */
data class HttpResponse(val status: Int, val body: String)

/**
 * Minimal HTTP abstraction the CLI's command logic depends on. Kept separate from any
 * concrete implementation (e.g. Ktor's JS engine) so command logic is testable with a
 * fake — no network, no Node runtime required.
 */
interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
    suspend fun post(url: String, headers: Map<String, String> = emptyMap(), body: String = ""): HttpResponse
}
