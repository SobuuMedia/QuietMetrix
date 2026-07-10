package com.quietmetrix.server.ingest

import com.quietmetrix.server.ingest.InstallIdHasher.hash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InstallIdHasherTest {

    @Test
    fun `hash is deterministic for the same salt and id`() {
        assertEquals(hash("salt-a", "install-123"), hash("salt-a", "install-123"))
    }

    @Test
    fun `hash changes when the salt changes`() {
        // Per-project salt means install ids cannot be correlated across projects or rotations.
        assertNotEquals(hash("salt-a", "install-123"), hash("salt-b", "install-123"))
    }

    @Test
    fun `hash is a 64-char hex string`() {
        val h = hash("salt-a", "install-123")
        assertEquals(64, h.length)
        assertTrue(h.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `hash never contains the raw install id`() {
        val raw = "super-secret-install-id-xyz"
        val h = hash("salt-a", raw)
        assertFalse(h.contains(raw), "salted hash must not leak the raw install id")
    }
}