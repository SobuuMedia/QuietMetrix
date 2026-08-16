package com.quietmetrix.server.config

import com.quietmetrix.server.funnels.FunnelRegistrationService
import com.quietmetrix.server.ingest.EventNormalizer
import com.quietmetrix.server.ingest.EventValidator
import com.quietmetrix.server.ingest.IngestChannel
import com.quietmetrix.server.persistence.AccessTokenRepository
import com.quietmetrix.server.persistence.EventRepository
import com.quietmetrix.server.persistence.FunnelRepository
import com.quietmetrix.server.persistence.IngestAuditRepository
import com.quietmetrix.server.persistence.InstallRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.QuarantineRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.ratelimit.InstallRateLimiter
import com.quietmetrix.server.ratelimit.IpRateLimiter
import com.quietmetrix.server.ratelimit.QuotaEnforcer
import com.quietmetrix.server.ratelimit.RateLimiter
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import javax.sql.DataSource

fun appModule(config: AppConfig) = module {

    single { config }

    single<DataSource> {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.db.url
            username = config.db.user
            password = config.db.password
            driverClassName = config.db.driver
            maximumPoolSize = config.db.poolSize
        })
    }

    single { Database.connect(get<DataSource>()) }

    single { EventRepository(get()) }
    single { ProjectRepository(get()) }
    single { UserRepository(get()) }
    single { AccessTokenRepository(get()) }
    single { ProjectMemberRepository(get()) }
    single { FunnelRepository(get()) }
    single { FunnelRegistrationService(get()) }

    single { EventValidator() }
    single { EventNormalizer() }
    single { IngestChannel(get(), get(), get()) }

    single { RateLimiter(config.rateLimit, kotlinx.coroutines.CoroutineScope(Dispatchers.Default + SupervisorJob())) }

    single { IpRateLimiter(config.ipRateLimit, kotlinx.coroutines.CoroutineScope(Dispatchers.Default + SupervisorJob())) }

    single { InstallRateLimiter(config.installRateLimit, kotlinx.coroutines.CoroutineScope(Dispatchers.Default + SupervisorJob())) }

    single { InstallRepository(get(), config.installRateLimit.rampEventThreshold, config.installRateLimit.rampWindowMinutes) }

    single { QuarantineRepository(get()) }
    single { IngestAuditRepository(get()) }

    // Quota enforcement is always available; the self-host build treats every
    // limit as unbounded (see QuotaEnforcer).
    single { QuotaEnforcer(get()) }
}