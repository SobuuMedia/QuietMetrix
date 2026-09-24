package com.quietmetrix.analytics.internal.transport

/**
 * A generic authenticated GET, the read-side counterpart to [platformPost] — used by
 * [com.quietmetrix.analytics.internal.experiments.ExperimentClient] to fetch experiment
 * config. Returns the response body on a 2xx response, or null on any failure (non-2xx status,
 * network error, timeout) — never throws, so a network failure never propagates into the
 * caller.
 */
internal expect suspend fun platformGet(endpoint: String, apiKey: String): String?
