package com.quietmetrix.server.ai

import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.CountersQuarantine
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import com.quietmetrix.server.routes.aiReportableCells
import com.quietmetrix.server.routes.analyticsWindow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AI reuses the reportable-counter gate without exposing foreign projects or device counts. */
class AiPrivacyTest {
    @Test fun `threshold suppression and project binding survive AI reads`() {
        val db=Database.connect("jdbc:h2:mem:ai-privacy;DB_CLOSE_DELAY=-1","org.h2.Driver","sa","")
        transaction(db) { SchemaUtils.create(Users,Projects,Counters,CountersQuarantine) }
        val users=UserRepository(db);val projects=ProjectRepository(db)
        users.create("ai-privacy@test.example","password")
        val p=projects.validateApiKey(projects.create("p",null,1))!!
        val other=projects.validateApiKey(projects.create("other",null,1))!!
        val repo=CounterRepository(db,kThreshold=5)
        val day=LocalDate.parse("2026-10-04")
        repeat(4) { repo.upsert(p,day,CounterRepository.CounterDelta("session",mapOf("bucket" to "0_10s"),n=999,isNewDevice=true)) }
        repeat(5) { repo.upsert(p,day,CounterRepository.CounterDelta("session",mapOf("bucket" to "10_30s"),n=1,isNewDevice=true)) }
        repeat(5) { repo.upsert(other,day,CounterRepository.CounterDelta("session",mapOf("bucket" to "30_60s"),n=1000,isNewDevice=true)) }
        val window=analyticsWindow(86400,null,"2026-10-04",Instant.parse("2026-10-04T12:00:00Z"))
        val visible=aiReportableCells(repo,p,"session",window)
        assertEquals("""[{"dims":{"bucket":"10_30s"},"n":5}]""",visible.toString())
        assertFalse(visible.toString().contains("devices"))
        repeat(5) { repo.upsert(p,day,CounterRepository.CounterDelta("retention",mapOf("cohort" to "2026-09-01","day" to "30"),n=1,isNewDevice=true)) }
        assertTrue(aiReportableCells(repo,p,"retention",window).isEmpty())
    }
}
