package com.quietmetrix.server.config

import com.quietmetrix.server.billing.AdyenProvider
import com.quietmetrix.server.billing.PaymentProvider
import com.quietmetrix.server.billing.StripeProvider
import com.quietmetrix.server.ingest.EventNormalizer
import com.quietmetrix.server.ingest.EventValidator
import com.quietmetrix.server.ingest.IngestChannel
import com.quietmetrix.server.persistence.EventRepository
import com.quietmetrix.server.persistence.ProjectMemberRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
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
    single { ProjectMemberRepository(get()) }

    single { EventValidator() }
    single { EventNormalizer() }
    single { IngestChannel(get(), get(), get()) }

    single { RateLimiter(config.rateLimit, kotlinx.coroutines.CoroutineScope(Dispatchers.Default + SupervisorJob())) }

    if (config.isCloud && config.billing != null) {
        single { QuotaEnforcer(get()) }
        single<PaymentProvider> {
            when (config.billing.provider) {
                "adyen" -> AdyenProvider(config.billing)
                else -> StripeProvider(config.billing)
            }
        }
    }
}