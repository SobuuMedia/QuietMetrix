package com.quietmetrix.analytics.internal.transport

/**
 * A generic authenticated POST used by both [com.quietmetrix.analytics.internal.counters
 * .CounterFlusher] and funnel registration — each builds its own JSON body, so this needs no
 * typed payload of its own, only the same per-platform explicit-engine treatment every actual
 * uses (see the comment in each `platformPost` actual about R8 stripping ServiceLoader-based
 * engine resolution). Returns true on a 2xx response, false otherwise — including on any
 * exception, so a network failure never propagates into the caller.
 */
internal expect suspend fun platformPost(endpoint: String, apiKey: String, body: String): Boolean
