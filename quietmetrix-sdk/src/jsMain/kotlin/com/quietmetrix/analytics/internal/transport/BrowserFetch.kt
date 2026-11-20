package com.quietmetrix.analytics.internal.transport

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.js.Promise

/** JSON is already serialized by the shared core; the browser needs no HTTP runtime. */
internal suspend fun browserFetch(endpoint: String, apiKey: String, body: String?, readBody: Boolean): String? =
    suspendCancellableCoroutine { continuation ->
        val controller: dynamic = js("typeof AbortController === 'function' ? new AbortController() : null")
        continuation.invokeOnCancellation { if (controller != null) controller.abort() }
        fun complete(value: String?) {
            if (continuation.isActive) continuation.resume(value)
        }
        try {
            val options: dynamic = js("({})")
            val headers: dynamic = js("({})")
            headers["X-QM-Api-Key"] = apiKey
            options.method = if (body == null) "GET" else "POST"
            if (body != null) {
                headers["Content-Type"] = "application/json"
                options.body = body
            }
            options.headers = headers
            if (controller != null) options.signal = controller.signal
            val request = js("globalThis.fetch")(endpoint, options) as Promise<dynamic>
            request.then<Unit>({ response ->
                try {
                    if ((response.status as Int) !in 200..299) complete(null)
                    else if (!readBody) complete("")
                    else {
                        val text = response.text() as Promise<String>
                        text.then<Unit>({ value -> complete(value) }, { complete(null) })
                    }
                } catch (_: Throwable) { complete(null) }
            }, { complete(null) })
        } catch (_: Throwable) { complete(null) }
    }
