package com.quietmetrix.server.persistence

import com.quietmetrix.server.ingest.InstallIdHasher
import com.quietmetrix.server.persistence.tables.InstallMeta
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallRepositoryTest {

    private fun freshDb(): Database =
        Database.connect("jdbc:h2:mem:installrepo_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")

    private fun seed(db: Database): Pair<ProjectRepository, String> {
        transaction(db) { SchemaUtils.create(Users, Projects, InstallMeta) }
        val userRepo = UserRepository(db)
        val projectRepo = ProjectRepository(db)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("fdroid-demo", null, 1L)
        return projectRepo to apiKey
    }

    @Test
    fun `upsert increments event count and sets first seen once`() {
        val db = freshDb()
        val (projectRepo, apiKey) = seed(db)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val salt = projectRepo.getInstallSalt(projectId)!!
        val installRepo = InstallRepository(db, rampEventThreshold = 500, rampWindowMinutes = 10)
        val h = InstallIdHasher.hash(salt, "install-A")

        val r1 = installRepo.upsert(projectId, h)
        val r2 = installRepo.upsert(projectId, h)
        val r3 = installRepo.upsert(projectId, h)

        assertEquals(1L, r1.eventCount)
        assertEquals(3L, r3.eventCount)
        assertEquals(r1.firstSeenAt, r3.firstSeenAt, "first_seen must be fixed after first upsert")
        assertFalse(r1.revoked)
    }

    @Test
    fun `auto-revokes an install that exceeds the ramp threshold inside the window`() {
        val db = freshDb()
        val (projectRepo, apiKey) = seed(db)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val salt = projectRepo.getInstallSalt(projectId)!!
        val installRepo = InstallRepository(db, rampEventThreshold = 3, rampWindowMinutes = 10)
        val h = InstallIdHasher.hash(salt, "noisy-install")

        installRepo.upsert(projectId, h)
        installRepo.upsert(projectId, h)
        val r3 = installRepo.upsert(projectId, h)
        assertTrue(r3.revoked, "install crossing the ramp threshold must be auto-revoked")
    }

    @Test
    fun `revoked install stays revoked across subsequent upserts`() {
        val db = freshDb()
        val (projectRepo, apiKey) = seed(db)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val salt = projectRepo.getInstallSalt(projectId)!!
        val installRepo = InstallRepository(db, rampEventThreshold = 2, rampWindowMinutes = 10)
        val h = InstallIdHasher.hash(salt, "bad-install")

        installRepo.upsert(projectId, h)
        installRepo.upsert(projectId, h)
        assertTrue(installRepo.upsert(projectId, h).revoked)
        assertTrue(installRepo.upsert(projectId, h).revoked, "once revoked, stays revoked")
    }

    @Test
    fun `raw install id is never stored`() {
        val db = freshDb()
        val (projectRepo, apiKey) = seed(db)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val salt = projectRepo.getInstallSalt(projectId)!!
        val installRepo = InstallRepository(db)
        val raw = "super-secret-install-id-xyz"
        installRepo.upsert(projectId, InstallIdHasher.hash(salt, raw))

        val dump = transaction(db) {
            InstallMeta.selectAll().joinToString { it[InstallMeta.anonymousIdHash] }
        }
        assertFalse(dump.contains(raw), "install_meta must never contain the raw install id: $dump")
    }

    @Test
    fun `manual revoke flips a known install`() {
        val db = freshDb()
        val (projectRepo, apiKey) = seed(db)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val salt = projectRepo.getInstallSalt(projectId)!!
        val installRepo = InstallRepository(db)
        val h = InstallIdHasher.hash(salt, "install-M")
        installRepo.upsert(projectId, h)
        assertTrue(installRepo.revoke(projectId, h))
        assertTrue(installRepo.upsert(projectId, h).revoked)
    }

    @Test
    fun `manual revoke of an unknown install returns false`() {
        val db = freshDb()
        val (projectRepo, apiKey) = seed(db)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        val installRepo = InstallRepository(db)
        assertFalse(installRepo.revoke(projectId, "never-seen-hash"))
    }
}