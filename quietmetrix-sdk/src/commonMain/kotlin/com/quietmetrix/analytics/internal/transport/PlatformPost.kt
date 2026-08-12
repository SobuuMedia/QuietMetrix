package com.quietmetrix.analytics.internal.transport

/**
 * A generic authenticated POST, distinct from [platformSend]: funnel registration has its own
 * wire shape (a JSON body, not a batch of [EnqueuedEvent]s), but needs the same per-platform
 * explicit-engine treatment (see the comment in each `platformSend` actual about R8 stripping
 * ServiceLoader-based engine resolution). Returns true on a 2xx response, false otherwise —
 * including on any exception, so a network failure never propagates into the caller.
 */
internal expect suspend fun platformPost(endpoint: String, apiKey: String, body: String): Boolean
