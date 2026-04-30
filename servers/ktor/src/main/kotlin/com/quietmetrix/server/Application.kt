package com.quietmetrix.server

import io.ktor.server.application.*
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.plugins.*
import com.quietmetrix.server.routes.*
import com.quietmetrix.server.ratelimit.rateLimitModule
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val config = AppConfig(environment.config)

    install(Koin) {
        sl4jLogger()
        modules(appModule(config))
    }

    configureSerialization()
    configureMonitoring()
    configureCors()
    configureStatusPages()
    configureSecurity(config)
    configureRateLimiting(config)

    routing(config)
}