package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.Events
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Flow screen "To" column bug: transitions must be derived only from `screen_view` events with a
 * non-empty screen. Custom events (which the app stamps with the current screen) and empty-string
 * screens must not produce spurious transitions or a blank destination.
 */
class TransitionsTest {

    private fun db(name: String): Database {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers, Events) }
        return db
    }

    private fun seedProject(db: Database): Long {
        UserRepository(db).create("owner@test.com", "password")
        ProjectRepository(db).create("App", null, 1L)
        return transaction(db) { Projects.selectAll().first()[Projects.id] }
    }

    private fun Database.seedEvent(projectId: Long, name: String, screenName: String?, sid: String, secondsFromBase: Long, base: LocalDateTime) {
        transaction(this) {
            Events.insert {
                it[Events.projectId] = projectId
                it[eventName] = name
                it[screen] = screenName
                it[sessionId] = sid
                it[ts] = base.plusSeconds(secondsFromBase)
            }
        }
    }

    @Test
    fun `transitions ignore custom events and empty screens`() {
        val db = db("transitions1")
        val projectId = seedProject(db)
        val base = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10)

        // One session. Only screen_view events with a real screen should form the flow A -> B -> C.
        db.seedEvent(projectId, "screen_view", "A", "s1", 1, base)
        db.seedEvent(projectId, "button_click", "X", "s1", 2, base) // custom event, distinct screen: must be ignored
        db.seedEvent(projectId, "screen_view", "B", "s1", 3, base)
        db.seedEvent(projectId, "screen_view", "", "s1", 4, base)   // empty screen: must be ignored (was the blank "to")
        db.seedEvent(projectId, "screen_view", "C", "s1", 5, base)

        val from = Instant.now().minusSeconds(3600)
        val to = Instant.now().plusSeconds(3600)
        val transitions = EventRepository(db).findTransitions(projectId, from, to)

        val pairs = transitions.map { it["from_screen"] to it["to_screen"] }.toSet()
        assertEquals(setOf("A" to "B", "B" to "C"), pairs)
        assertTrue(
            transitions.all { (it["to_screen"] as String).isNotEmpty() },
            "no transition may have an empty destination screen",
        )
    }
}
