package com.quietmetrix.server.routes

import com.quietmetrix.server.config.AppConfig
import io.ktor.server.routing.Routing

fun Routing.configureAllRoutes(config: AppConfig) {
    configureTrackRoutes()
    configureAuthRoutes(config)
    configureProjectRoutes()
    configureDashboardRoutes()
    configureBillingRoutes(config)
    configureStaticDashboardRoutes()
}