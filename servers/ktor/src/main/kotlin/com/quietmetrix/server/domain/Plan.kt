package com.quietmetrix.server.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Plan(val id: String, val eventsPerMonth: Long, val requestsPerSecond: Int, val retentionDays: Int) {
    FREE("free", 10_000, 10, 30),
    HOBBY("hobby", 100_000, 50, 90),
    STARTUP("startup", 1_000_000, 200, 365),
    BUSINESS("business", 10_000_000, 1000, 730),
}