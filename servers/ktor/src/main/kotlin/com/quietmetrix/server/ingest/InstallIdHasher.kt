package com.quietmetrix.server.ingest

import java.security.MessageDigest

/**
 * Salted SHA-256 hashing of the per-install `anonymousId`. The SDK sends `anonymousId` raw
 * on the wire; the server never stores or logs it raw — only this per-project-salted hash
 * lands in `install_meta` / audit tables. The salt lives on the project row and is rotated
 * on key regeneration (old install rows are discarded at rotation since they cannot be
 * re-hashed). See docs/security/publishable-api-key.md — Privacy note.
 */
object InstallIdHasher {
    fun hash(salt: String, anonymousId: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(Charsets.UTF_8))
        md.update(0x3A) // ':' separator byte
        return md.digest(anonymousId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}