package com.quietmetrix.server.funnels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The funnel matcher needs one identity per event row: prefer the analytics-salt install
 * hash (survives across sessions), fall back to the session id for pre-migration events
 * that have no hash yet (see A2), and give up only when neither is present.
 */
class ActorKeyTest {

    @Test
    fun `prefers the install hash when present`() {
        assertEquals("abc123", actorKey(installHash = "abc123", sessionId = "sess-1"))
    }

    @Test
    fun `falls back to a prefixed session id when install hash is absent`() {
        assertEquals("sid:sess-1", actorKey(installHash = null, sessionId = "sess-1"))
    }

    @Test
    fun `falls back to session id when install hash is blank`() {
        assertEquals("sid:sess-1", actorKey(installHash = "", sessionId = "sess-1"))
    }

    @Test
    fun `is null when both are absent`() {
        assertNull(actorKey(installHash = null, sessionId = null))
    }

    @Test
    fun `is null when both are blank`() {
        assertNull(actorKey(installHash = "", sessionId = ""))
    }

    @Test
    fun `install hash never collides with a session-id fallback`() {
        // A raw session id could coincidentally equal another actor's install hash; the
        // "sid:" prefix on the fallback keeps the two identity spaces disjoint.
        val bySession = actorKey(installHash = null, sessionId = "abc123")
        val byHash = actorKey(installHash = "abc123", sessionId = "sess-1")
        assertEquals("sid:abc123", bySession)
        assertEquals("abc123", byHash)
        assert(bySession != byHash)
    }
}
