package com.quietmetrix.server.routes

import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.EventCountsDaily
import com.quietmetrix.server.persistence.tables.Events
import com.quietmetrix.server.persistence.tables.EventsInbox
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.UsageCounters
import com.quietmetrix.server.persistence.tables.Users
import com.quietmetrix.server.ratelimit.QuotaEnforcer
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectRoutesIntegrationTest {

    @Test
    fun `create project returns 201 with keys`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:pr1;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Projects, Users, ProjectMembers, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val userRepo = UserRepository(testDb)
        userRepo.create("owner@test.com", "password")
        val projectRepo = ProjectRepository(testDb)

        application {
            routing {
                post("/api/v1/projects") {
                    val (wk, rk) = projectRepo.create("My App", 1L)
                    call.respondText("""{"api_key":"$wk"}""", ContentType.Application.Json, HttpStatusCode.Created)
                }
            }
        }
        val response = client.post("/api/v1/projects") {
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("qm_ak_"))
    }

    @Test
    fun `create project at limit returns 403 for free plan`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:pr2;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Projects, Users, ProjectMembers, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val userRepo = UserRepository(testDb)
        userRepo.create("freeuser@test.com", "password")
        val projectRepo = ProjectRepository(testDb)
        val quotaEnforcer = QuotaEnforcer(projectRepo)
        projectRepo.create("Existing Project", 1L)

        application {
            routing {
                post("/api/v1/projects") {
                    val count = projectRepo.countByOwnerId(1L)
                    if (!quotaEnforcer.checkProjectLimit("free", count)) {
                        call.respondText("""{"error":"project_limit_reached"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@post
                    }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json, HttpStatusCode.Created)
                }
            }
        }
        val response = client.post("/api/v1/projects") { contentType(ContentType.Application.Json) }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `patch project name returns updated project`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:pr3;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Projects, Users, ProjectMembers, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val userRepo = UserRepository(testDb)
        userRepo.create("patchuser@test.com", "password")
        val projectRepo = ProjectRepository(testDb)
        projectRepo.create("Old Name", 1L)

        application {
            routing {
                patch("/api/v1/projects/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                    val updated = projectRepo.updateName(id, "New Name")
                    if (updated) {
                        call.respondText("""{"name":"New Name"}""", ContentType.Application.Json)
                    } else {
                        call.respondText("""{"error":"not_found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                    }
                }
            }
        }
        val projectId = transaction(testDb) { Projects.select(Projects.id).first()[Projects.id] }
        val response = client.patch("/api/v1/projects/$projectId") { contentType(ContentType.Application.Json) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("New Name"))
    }

    @Test
    fun `delete project soft deletes`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:pr4;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Projects, Users, ProjectMembers, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val userRepo = UserRepository(testDb)
        userRepo.create("deleteuser@test.com", "password")
        val projectRepo = ProjectRepository(testDb)
        projectRepo.create("Deletable", 1L)

        application {
            routing {
                delete("/api/v1/projects/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                    val deleted = projectRepo.softDelete(id)
                    if (deleted) {
                        call.respondText("""{"deleted":true}""", ContentType.Application.Json)
                    } else {
                        call.respondText("""{"error":"not_found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                    }
                }
            }
        }
        val projectId = transaction(testDb) { Projects.select(Projects.id).first()[Projects.id] }
        val response = client.delete("/api/v1/projects/$projectId")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"deleted\":true"))
    }

    @Test
    fun `add and list members`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:pr5;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Projects, Users, ProjectMembers, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val userRepo = UserRepository(testDb)
        userRepo.create("owner@test.com", "password")
        userRepo.create("viewer@test.com", "password")
        val projectRepo = ProjectRepository(testDb)
        val memberRepo = ProjectMemberRepository(testDb)
        projectRepo.create("Team Project", 1L)

        application {
            routing {
                post("/api/v1/projects/{id}/members") {
                    val projectId = call.parameters["id"]?.toLongOrNull() ?: 0L
                    val memberUser = userRepo.findByEmail("viewer@test.com")
                    val memberUserId = memberUser?.get("id") as? Long ?: 0L
                    memberRepo.add(projectId, memberUserId, "viewer")
                    call.respondText("""{"status":"added"}""", ContentType.Application.Json, HttpStatusCode.Created)
                }
                get("/api/v1/projects/{id}/members") {
                    val projectId = call.parameters["id"]?.toLongOrNull() ?: 0L
                    val members = memberRepo.findByProjectId(projectId)
                    call.respondText("""{"count":${members.size}}""", ContentType.Application.Json)
                }
            }
        }
        val projectId = transaction(testDb) { Projects.select(Projects.id).first()[Projects.id] }

        val addResponse = client.post("/api/v1/projects/$projectId/members") { contentType(ContentType.Application.Json) }
        assertEquals(HttpStatusCode.Created, addResponse.status)

        val listResponse = client.get("/api/v1/projects/$projectId/members")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertTrue(listResponse.bodyAsText().contains("\"count\":1"))
    }

    @Test
    fun `remove member returns success`() = testApplication {
        val testDb = Database.connect("jdbc:h2:mem:pr6;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(testDb) { SchemaUtils.create(Projects, Users, ProjectMembers, Events, EventsInbox, EventCountsDaily, UsageCounters) }
        val userRepo = UserRepository(testDb)
        userRepo.create("owner@test.com", "password")
        val viewerId = userRepo.create("viewer@test.com", "password")
        val projectRepo = ProjectRepository(testDb)
        val memberRepo = ProjectMemberRepository(testDb)
        projectRepo.create("Team Project", 1L)
        val projectId = transaction(testDb) { Projects.select(Projects.id).first()[Projects.id] }
        memberRepo.add(projectId, viewerId, "viewer")

        application {
            routing {
                delete("/api/v1/projects/{projectId}/members/{memberId}") {
                    val pid = call.parameters["projectId"]?.toLongOrNull() ?: 0L
                    val mid = call.parameters["memberId"]?.toLongOrNull() ?: 0L
                    val removed = memberRepo.remove(pid, mid)
                    if (removed) {
                        call.respondText("""{"removed":true}""", ContentType.Application.Json)
                    } else {
                        call.respondText("""{"error":"not_found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                    }
                }
            }
        }
        val response = client.delete("/api/v1/projects/$projectId/members/$viewerId")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"removed\":true"))
    }
}