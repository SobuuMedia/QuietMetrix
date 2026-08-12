package com.quietmetrix.server.funnels

/**
 * The unit of conversion for funnel matching. Prefers the analytics-salt [installHash]
 * (see A2) — it survives across sessions and an API-key rotation — and falls back to a
 * `sid:`-prefixed [sessionId] for events ingested before that column existed. The prefix
 * keeps the two identity spaces disjoint: a session id can never collide with a hash.
 * Returns null when neither identifier is present, meaning the event cannot be attributed
 * to any actor and must be excluded from funnel results.
 */
fun actorKey(installHash: String?, sessionId: String?): String? {
    if (!installHash.isNullOrBlank()) return installHash
    if (!sessionId.isNullOrBlank()) return "sid:$sessionId"
    return null
}
