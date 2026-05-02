package com.quietmetrix.server.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Plan(val id: String, val eventsPerMonth: Long, val requestsPerSecond: Int, val retentionDays: Int, val maxProjects: Int) {
    FREE("free", 10_000, 10, 30, 1),
    HOBBY("hobby", 100_000, 50, 90, 3),
    STARTUP("startup", 1_000_000, 200, 365, 10),
    BUSINESS("business", 10_000_000, 1000, 730, 50),
}