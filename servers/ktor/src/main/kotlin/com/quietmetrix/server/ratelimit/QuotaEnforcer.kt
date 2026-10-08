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

    /**
     * Max non-ended experiments allowed per project for [planId]; null means unlimited. Unlike
     * [checkProjectLimit], this DOES enforce a real limit — but only when [isCloud] is true.
     * A self-hosted instance (the default) is always unlimited, same philosophy as every other
     * limit in this class; see ProjectRoutesIntegrationTest's self-host regression test for the
     * invariant this preserves.
     */
    fun experimentLimit(planId: String?, isCloud: Boolean): Int? {
        if (!isCloud) return null
        return if (planId == null || planId == "free") 2 else null
    }
}

/** Hosted tenant project limits; the default self-host path remains unlimited. */
fun hostedProjectLimit(planId: String?): Int = when (planId) {
    "hobby" -> 3
    "startup" -> 10
    "business" -> 50
    else -> 1
}
