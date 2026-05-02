package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.persistence.ProjectRepository

class QuotaEnforcer(private val projectRepository: ProjectRepository) {

    private val planLimits = mapOf(
        "free" to 10_000L,
        "hobby" to 100_000L,
        "startup" to 1_000_000L,
        "business" to 10_000_000L,
    )

    private val planProjectLimits = mapOf(
        "free" to 1,
        "hobby" to 3,
        "startup" to 10,
        "business" to 50,
    )

    fun getEventLimit(planId: String?): Long {
        return planLimits[planId] ?: Long.MAX_VALUE
    }

    fun getProjectLimit(planId: String?): Int {
        return planProjectLimits[planId] ?: 1
    }

    fun checkQuota(planId: String?, currentUsage: Long): Boolean {
        val limit = getEventLimit(planId)
        return limit == Long.MAX_VALUE || currentUsage < limit
    }

    fun checkProjectLimit(planId: String?, currentProjectCount: Long): Boolean {
        val limit = getProjectLimit(planId)
        return limit == Int.MAX_VALUE || currentProjectCount < limit
    }

    fun createCheckoutSession(userId: String, planId: String): String {
        return "https://checkout.quietmetrix.example/create?user=${userId}&plan=${planId}"
    }
}