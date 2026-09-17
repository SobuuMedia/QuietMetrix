package com.quietmetrix.cli

/** Strips whitespace and a trailing slash: `"https://qm.example.com/ "` -> `"https://qm.example.com"`. */
fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

/** The events-ingest endpoint an SDK config should point at, derived from the server's base URL. */
fun trackingEndpoint(baseUrl: String): String = "${normalizeBaseUrl(baseUrl)}/api/v1/track"
