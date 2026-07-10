package com.quietmetrix.server.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val profile: Profile,
    val db: DbConfig,
    val auth: AuthConfig,
    val rateLimit: RateLimitConfig,
    val ipRateLimit: IpRateLimitConfig = IpRateLimitConfig(),
    val installRateLimit: InstallRateLimitConfig = InstallRateLimitConfig(),
    val cors: CorsConfig = CorsConfig(),
    val trustedProxies: Set<String> = emptySet(),
) {
    enum class Profile { SELFHOST, CLOUD }

    val isCloud: Boolean get() = profile == Profile.CLOUD

    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            val profileStr = config.propertyOrNull("quietmetrix.profile")?.getString() ?: "selfhost"
            val profile = Profile.entries.firstOrNull {
                it.name.equals(profileStr, ignoreCase = true)
            } ?: Profile.SELFHOST

            val appConfig = AppConfig(
                profile = profile,
                db = DbConfig(
                    url = config.property("quietmetrix.db.url").getString(),
                    user = config.property("quietmetrix.db.user").getString(),
                    password = config.property("quietmetrix.db.password").getString(),
                    driver = config.propertyOrNull("quietmetrix.db.driver")?.getString() ?: "org.postgresql.Driver",
                    poolSize = config.propertyOrNull("quietmetrix.db.poolSize")?.getString()?.toInt() ?: 10,
                ),
                auth = AuthConfig(
                    jwtSecret = config.property("quietmetrix.auth.jwtSecret").getString(),
                    jwtIssuer = config.propertyOrNull("quietmetrix.auth.jwtIssuer")?.getString() ?: "quietmetrix",
                    jwtAudience = config.propertyOrNull("quietmetrix.auth.jwtAudience")?.getString() ?: "quietmetrix-api",
                    sessionTtlHours = config.propertyOrNull("quietmetrix.auth.sessionTtlHours")?.getString()?.toInt() ?: 2,
                ),
                rateLimit = RateLimitConfig(
                    enabled = config.propertyOrNull("quietmetrix.rateLimit.enabled")?.getString()?.toBoolean() ?: (profile == Profile.CLOUD),
                    requestsPerSecond = config.propertyOrNull("quietmetrix.rateLimit.requestsPerSecond")?.getString()?.toInt() ?: 10,
                    burstPerMinute = config.propertyOrNull("quietmetrix.rateLimit.burstPerMinute")?.getString()?.toInt() ?: 60,
                ),
                ipRateLimit = IpRateLimitConfig(
                    enabled = config.propertyOrNull("quietmetrix.ipRateLimit.enabled")?.getString()?.toBoolean() ?: true,
                    requestsPerSecond = config.propertyOrNull("quietmetrix.ipRateLimit.requestsPerSecond")?.getString()?.toInt() ?: 5,
                    burstPerMinute = config.propertyOrNull("quietmetrix.ipRateLimit.burstPerMinute")?.getString()?.toInt() ?: 60,
                ),
                installRateLimit = InstallRateLimitConfig(
                    enabled = config.propertyOrNull("quietmetrix.installRateLimit.enabled")?.getString()?.toBoolean() ?: true,
                    requestsPerSecond = config.propertyOrNull("quietmetrix.installRateLimit.requestsPerSecond")?.getString()?.toInt() ?: 1,
                    burstPerMinute = config.propertyOrNull("quietmetrix.installRateLimit.burstPerMinute")?.getString()?.toInt() ?: 30,
                    rampEventThreshold = config.propertyOrNull("quietmetrix.installRateLimit.rampEventThreshold")?.getString()?.toLong() ?: 500L,
                    rampWindowMinutes = config.propertyOrNull("quietmetrix.installRateLimit.rampWindowMinutes")?.getString()?.toLong() ?: 10L,
                ),
                cors = CorsConfig(
                    allowedOrigins = parseOrigins(config.propertyOrNull("quietmetrix.cors.allowedOrigins")),
                ),
                trustedProxies = config.propertyOrNull("quietmetrix.security.trustedProxies")?.getList()?.toSet() ?: emptySet(),
            )
            require(appConfig.auth.jwtSecret.isNotBlank() && !appConfig.auth.jwtSecret.startsWith("change-me")) {
                "QM_JWT_SECRET must be set to a strong random value in production"
            }
            require(appConfig.db.password.isNotBlank()) {
                "QM_DB_PASSWORD must be set"
            }
            require(appConfig.cors.allowedOrigins.isNotEmpty()) {
                "QM_CORS_ALLOWED_ORIGINS must be set. Provide a comma-separated list of allowed origins."
            }
            return appConfig
        }

        /**
         * Accepts either a HOCON list (as used in tests / .conf files) or a single
         * comma-separated string (as delivered by the QM_CORS_ALLOWED_ORIGINS env var).
         */
        private fun parseOrigins(value: io.ktor.server.config.ApplicationConfigValue?): List<String> {
            if (value == null) return emptyList()
            return try {
                value.getList()
            } catch (_: Exception) {
                value.getString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }
}

data class CorsConfig(
    val allowedOrigins: List<String> = emptyList(),
)

data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
    val driver: String,
    val poolSize: Int,
)

data class AuthConfig(
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val sessionTtlHours: Int,
)

data class RateLimitConfig(
    val enabled: Boolean,
    val requestsPerSecond: Int,
    val burstPerMinute: Int,
)

/**
 * Per-IP ingest throttling (abuse defense Stage 1).
 * Keyed on the trusted-proxy-validated client IP. Defaults enabled — this is the primary
 * anti-pollution control for publishable API keys (F-Droid etc.). See
 * docs/security/publishable-api-key.md.
 */
data class IpRateLimitConfig(
    val enabled: Boolean = true,
    val requestsPerSecond: Int = 5,
    val burstPerMinute: Int = 60,
)

/**
 * Per-install ingest throttling + ramp-up detector (abuse defense Stage 2). Keyed on the
 * salt-hashed `anonymousId`. A brand-new install that fires more than [rampEventThreshold]
 * events within [rampWindowMinutes] is auto-revoked. See docs/security/publishable-api-key.md.
 */
data class InstallRateLimitConfig(
    val enabled: Boolean = true,
    val requestsPerSecond: Int = 1,
    val burstPerMinute: Int = 30,
    val rampEventThreshold: Long = 500L,
    val rampWindowMinutes: Long = 10L,
)

fun ApplicationConfig.toAppConfig(): AppConfig = AppConfig.from(this)