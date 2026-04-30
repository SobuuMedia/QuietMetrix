package com.quietmetrix.server.config

import com.quietmetrix.server.persistence.*
import com.quietmetrix.server.persistence.tables.*
import com.quietmetrix.server.ingest.*
import com.quietmetrix.server.ratelimit.*
import com.zaxxer.hikari.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.koin.dsl.*
import org.koin.logger.slf4jLogger
import javax.sql.DataSource

fun appModule(config: AppConfig) = module {
    sl4jLogger()

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

    single { Database.connect(dataSource = get()) }

    single { EventRepository(get()) }
    single { ProjectRepository(get()) }
    single { UserRepository(get()) }

    single { EventValidator() }
    single { EventNormalizer() }
    single { IngestChannel(get(), get(), get()) }

    single { RateLimiter(config.rateLimit) }

    if (config.isCloud && config.billing != null) {
        single { QuotaEnforcer(get()) }
    }
}