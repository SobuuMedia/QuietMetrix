package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.AccessTokens
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 1 — long-lived agent access tokens (`qm_pat_…`). Mirrors the plaintext-once,
 * hash-only storage contract already proven for project API keys in
 * [ApiKeyRegenerationTest].
 */
class AccessTokenRepositoryTest {

    private fun db(name: String): Database {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, AccessTokens) }
        return db
    }

    @Test
    fun `create returns a plaintext token that validates`() {
        val db = db("pat1")
        val userId = UserRepository(db).create("owner@test.com", "password")
        val repo = AccessTokenRepository(db)

        val created = repo.create(userId, "laptop-agent", setOf("projects:create"))

        assertTrue(created.token.startsWith("qm_pat_"))
        assertEquals(created.token.takeLast(4), created.last4)

        val validated = repo.validate(created.token)
        assertEquals(userId, validated?.userId)
        assertEquals(setOf("projects:create"), validated?.scopes)
    }

    @Test
    fun `plaintext token is never stored`() {
        val db = db("pat2")
        val userId = UserRepository(db).create("owner@test.com", "password")
        val created = AccessTokenRepository(db).create(userId, "agent", setOf("projects:create"))

        val storedSha256 = transaction(db) { AccessTokens.selectAll().first()[AccessTokens.tokenSha256] }
        assertNotEquals(created.token, storedSha256)
    }

    @Test
    fun `unknown token does not validate`() {
        val db = db("pat3")
        assertNull(AccessTokenRepository(db).validate("qm_pat_doesnotexist"))
    }

    @Test
    fun `revoked token stops validating`() {
        val db = db("pat4")
        val userId = UserRepository(db).create("owner@test.com", "password")
        val repo = AccessTokenRepository(db)
        val created = repo.create(userId, "agent", setOf("projects:create"))

        assertTrue(repo.revoke(created.id, userId))
        assertNull(repo.validate(created.token))
    }

    @Test
    fun `revoke is scoped to the owning user`() {
        val db = db("pat5")
        val userRepo = UserRepository(db)
        val ownerA = userRepo.create("a@test.com", "password")
        val ownerB = userRepo.create("b@test.com", "password")
        val repo = AccessTokenRepository(db)
        val created = repo.create(ownerA, "agent", setOf("projects:create"))

        assertFalse(repo.revoke(created.id, ownerB), "another user must not be able to revoke this token")
        assertTrue(repo.validate(created.token) != null, "token must still be valid")
    }

    @Test
    fun `expired token does not validate`() {
        val db = db("pat6")
        val userId = UserRepository(db).create("owner@test.com", "password")
        val repo = AccessTokenRepository(db)
        val created = repo.create(userId, "agent", setOf("projects:create"), expiresInDays = 1)

        transaction(db) {
            AccessTokens.update({ AccessTokens.id eq created.id }) {
                it[expiresAt] = LocalDateTime.now().minusDays(1)
            }
        }

        assertNull(repo.validate(created.token))
    }

    @Test
    fun `validate advances last_used_at`() {
        val db = db("pat7")
        val userId = UserRepository(db).create("owner@test.com", "password")
        val repo = AccessTokenRepository(db)
        val created = repo.create(userId, "agent", setOf("projects:create"))

        assertNull(transaction(db) { AccessTokens.selectAll().first()[AccessTokens.lastUsedAt] })

        repo.validate(created.token)

        assertNotEquals(null, transaction(db) { AccessTokens.selectAll().first()[AccessTokens.lastUsedAt] })
    }

    @Test
    fun `list returns tokens for a user without plaintext`() {
        val db = db("pat8")
        val userRepo = UserRepository(db)
        val ownerA = userRepo.create("a@test.com", "password")
        val ownerB = userRepo.create("b@test.com", "password")
        val repo = AccessTokenRepository(db)
        repo.create(ownerA, "agent-1", setOf("projects:create"))
        repo.create(ownerB, "agent-2", setOf("projects:create"))

        val listed = repo.list(ownerA)
        assertEquals(1, listed.size)
        assertEquals("agent-1", listed.first()["name"])
    }
}
