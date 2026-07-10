package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.persistence.ProjectRepository

/**
 * Usage/quota enforcement. QuietMetrix ships as a self-hosted, unlimited
 * analytics server, so every limit here is effectively unbounded. The interface
 * is retained so callers (e.g. [com.quietmetrix.server.routes.configureProjectRoutes])
 * stay quota-aware if a hosted deployment ever reintroduces tiered plans.
 */
class QuotaEnforcer(@Suppress("unused") private val projectRepository: ProjectRepository) {

    fun getEventLimit(planId: String?): Long = Long.MAX_VALUE

    fun getProjectLimit(planId: String?): Int = Int.MAX_VALUE

    fun checkQuota(planId: String?, currentUsage: Long): Boolean = true

    fun checkProjectLimit(planId: String?, currentProjectCount: Long): Boolean = true
}
