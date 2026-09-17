package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Task 3 — invite creation, acceptance, role/list management. */
class UserInviteTest {

    private fun repo(name: String): UserRepository {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers) }
        return UserRepository(db)
    }

    @Test
    fun `invite then accept activates account and enables login`() {
        val repo = repo("invite1")
        val token = repo.createInvite("dev@test.com", "developer")

        // Invited user can't log in yet (placeholder password).
        assertNull(repo.validatePassword("dev@test.com", "anything"))

        val invited = repo.findByInviteToken(token)
        assertNotNull(invited)
        assertEquals("dev@test.com", invited["email"])
        assertEquals("developer", invited["role"])

        assertTrue(repo.acceptInvite((invited["id"] as Long), "newpassword1"))

        // Token is single-use, and login now works with the new role.
        assertNull(repo.findByInviteToken(token), "token must be cleared after accept")
        val loggedIn = repo.validatePassword("dev@test.com", "newpassword1")
        assertNotNull(loggedIn)
        assertEquals("developer", loggedIn["role"])
        assertEquals("active", loggedIn["status"])
    }

    @Test
    fun `bogus invite token returns null`() {
        val repo = repo("invite2")
        assertNull(repo.findByInviteToken("does-not-exist"))
    }

    @Test
    fun `list updateRole and delete with last-admin awareness`() {
        val repo = repo("invite3")
        repo.create("admin@test.com", "password")            // default role admin
        val devToken = repo.createInvite("dev@test.com", "developer")
        repo.acceptInvite(repo.findByInviteToken(devToken)!!["id"] as Long, "password1")

        assertEquals(2, repo.listAll().size)
        assertEquals(1, repo.countAdmins())

        // Promote developer to admin, then there are two admins.
        val devId = repo.findByEmail("dev@test.com")!!["id"] as Long
        assertTrue(repo.updateRole(devId, "admin"))
        assertEquals(2, repo.countAdmins())

        // Delete works.
        assertTrue(repo.delete(devId))
        assertEquals(1, repo.listAll().size)
    }
}
