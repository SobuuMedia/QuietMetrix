package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import io.ktor.server.routing.Routing

fun Routing.configureAllRoutes(config: AppConfig) {
    configureMetaRoutes()
    configureCounterRoutes()
    configureAuthRoutes(config)
    configureUserRoutes(config)
    configureProjectRoutes()
    configureTokenRoutes()
    configureFunnelRoutes()
    configureDashboardRoutes()
    configureStaticDashboardRoutes()
}