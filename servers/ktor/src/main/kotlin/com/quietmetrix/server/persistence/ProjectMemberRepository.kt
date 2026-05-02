package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.ProjectMembers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ProjectMemberRepository(private val database: Database) {

    fun add(projectId: Long, userId: Long, role: String): Long {
        return transaction(database) {
            ProjectMembers.insert {
                it[this.projectId] = projectId
                it[this.userId] = userId
                it[this.role] = role
            } get ProjectMembers.id
        }
    }

    fun remove(pid: Long, uid: Long): Boolean {
        return transaction(database) {
            ProjectMembers.deleteWhere(limit = null) {
                with(it) {
                    (ProjectMembers.projectId eq pid) and (ProjectMembers.userId eq uid)
                }
            } > 0
        }
    }

    fun findByProjectId(projectId: Long): List<Map<String, Any?>> {
        return transaction(database) {
            ProjectMembers
                .selectAll()
                .where { ProjectMembers.projectId eq projectId }
                .map { rowToMap(it) }
        }
    }

    fun findByUserId(userId: Long): List<Long> {
        return transaction(database) {
            ProjectMembers
                .select(ProjectMembers.projectId)
                .where { ProjectMembers.userId eq userId }
                .map { it[ProjectMembers.projectId] }
        }
    }

    fun findMembership(projectId: Long, userId: Long): Map<String, Any?>? {
        return transaction(database) {
            ProjectMembers
                .selectAll()
                .where { (ProjectMembers.projectId eq projectId) and (ProjectMembers.userId eq userId) }
                .map { rowToMap(it) }
                .singleOrNull()
        }
    }

    fun countByProjectId(projectId: Long): Long {
        return transaction(database) {
            ProjectMembers
                .selectAll()
                .where { ProjectMembers.projectId eq projectId }
                .count()
        }
    }

    private fun rowToMap(row: ResultRow) = mapOf(
        "id" to row[ProjectMembers.id],
        "projectId" to row[ProjectMembers.projectId],
        "userId" to row[ProjectMembers.userId],
        "role" to row[ProjectMembers.role],
        "createdAt" to row[ProjectMembers.createdAt],
    )
}
