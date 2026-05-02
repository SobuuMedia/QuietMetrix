package com.quietmetrix.server

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.config.appModule
import com.quietmetrix.server.ingest.IngestChannel
import com.quietmetrix.server.plugins.configureCors
import com.quietmetrix.server.plugins.configureDefaultHeaders
import com.quietmetrix.server.plugins.configureMonitoring
import com.quietmetrix.server.plugins.configureRateLimiting
import com.quietmetrix.server.plugins.configureSecurity
import com.quietmetrix.server.plugins.configureSerialization
import com.quietmetrix.server.plugins.configureStatusPages
import com.quietmetrix.server.routes.configureAllRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val config = AppConfig.from(environment.config)

    install(Koin) {
        modules(appModule(config))
    }

    configureSerialization()
    configureMonitoring()
    configureDefaultHeaders()
    configureCors(config)
    configureStatusPages()
    configureSecurity(config)
    configureRateLimiting(config)

    val ingestChannel: IngestChannel = get()
    ingestChannel.start(this)

    routing {
        configureAllRoutes(config)
    }
}