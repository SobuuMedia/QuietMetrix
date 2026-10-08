package com.quietmetrix.server.config

import com.quietmetrix.server.funnels.FunnelRegistrationService
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.CounterV2Repository
import com.quietmetrix.server.persistence.ExperimentRepository
import com.quietmetrix.server.persistence.FunnelRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectReportRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.ratelimit.IpRateLimiter
import com.quietmetrix.server.ratelimit.QuotaEnforcer
import com.quietmetrix.server.ratelimit.RateLimiter
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import javax.sql.DataSource

fun appModule(config: AppConfig) = module {

    single { config }
    single { com.quietmetrix.server.ai.AiAccounting(get(), config.ai, config.isCloud, requireProject = true) }
    single { com.quietmetrix.server.ai.AiService(config.ai, get()) }

    single<DataSource> {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.db.url
            username = config.db.user
            password = config.db.password
            driverClassName = config.db.driver
            maximumPoolSize = config.db.poolSize
            connectionTimeout = 3_000
            validationTimeout = 1_000
        })
    }

    single { Database.connect(get<DataSource>()) }

    single { ProjectRepository(get()) }
    single { UserRepository(get()) }
    single { AccessTokenRepository(get()) }
    single { ProjectMemberRepository(get()) }
    single { FunnelRepository(get()) }
    single { FunnelRegistrationService(get()) }
    single { ExperimentRepository(get()) }
    single { CounterRepository(get(), config.counters.kThreshold, config.counters.maxDistinctCellsPerMetric) }
    single { CounterV2Repository(get(), config.counters.maxDistinctCellsPerMetric) }
    single { ProjectReportRepository(get()) }

    single { RateLimiter(config.rateLimit, kotlinx.coroutines.CoroutineScope(Dispatchers.Default + SupervisorJob())) }

    single { IpRateLimiter(config.ipRateLimit, kotlinx.coroutines.CoroutineScope(Dispatchers.Default + SupervisorJob())) }

    // Quota enforcement is always available; the self-host build treats every
    // limit as unbounded (see QuotaEnforcer).
    single { QuotaEnforcer(get()) }
}
