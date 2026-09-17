package com.quietmetrix.cli

import com.quietmetrix.cli.http.HttpClient
import com.quietmetrix.cli.http.HttpResponse

/** Records every request it receives and returns canned responses in call order. */
class FakeHttpClient(private val responses: MutableList<Result<HttpResponse>> = mutableListOf()) : HttpClient {
    data class Request(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    val requests = mutableListOf<Request>()

    fun enqueue(status: Int, body: String) {
        responses += Result.success(HttpResponse(status, body))
    }

    fun enqueueFailure(error: Throwable) {
        responses += Result.failure(error)
    }

    private fun next(): HttpResponse =
        (responses.removeFirstOrNull() ?: error("FakeHttpClient: no more responses queued")).getOrThrow()

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        requests += Request("GET", url, headers, null)
        return next()
    }

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
        requests += Request("POST", url, headers, body)
        return next()
    }
}
