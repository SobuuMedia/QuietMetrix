package com.quietmetrix.server.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val profile: Profile,
    val db: DbConfig,
    val auth: AuthConfig,
    val rateLimit: RateLimitConfig,
    val ipRateLimit: IpRateLimitConfig = IpRateLimitConfig(),
    val cors: CorsConfig = CorsConfig(),
    val trustedProxies: Set<String> = emptySet(),
    val counters: CountersConfig = CountersConfig(),
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
                    adminEmail = config.propertyOrNull("quietmetrix.auth.adminEmail")?.getString()?.takeIf { it.isNotBlank() },
                    adminPassword = config.propertyOrNull("quietmetrix.auth.adminPassword")?.getString()?.takeIf { it.isNotBlank() },
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
                cors = CorsConfig(
                    allowedOrigins = parseOrigins(config.propertyOrNull("quietmetrix.cors.allowedOrigins")),
                ),
                trustedProxies = config.propertyOrNull("quietmetrix.security.trustedProxies")?.getList()?.toSet() ?: emptySet(),
                counters = CountersConfig(
                    kThreshold = config.propertyOrNull("quietmetrix.counters.kThreshold")?.getString()?.toInt() ?: 5,
                    maxDistinctCellsPerMetric = config.propertyOrNull("quietmetrix.counters.maxDistinctCellsPerMetric")?.getString()?.toInt() ?: 500,
                ),
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
    val adminEmail: String? = null,
    val adminPassword: String? = null,
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
 * Aggregate-ingest tuning. [kThreshold] is the k-anonymity read gate: a counter cell is
 * invisible through every read path until at least this many distinct devices have
 * contributed to it (see `CounterRepository.readCells`). [maxDistinctCellsPerMetric] caps how
 * many distinct dims combinations one metric may accumulate per project, so an unbounded
 * dimension value can't grow the table without limit; cells beyond the cap are quarantined
 * rather than dropped silently.
 */
data class CountersConfig(
    val kThreshold: Int = 5,
    val maxDistinctCellsPerMetric: Int = 500,
)

fun ApplicationConfig.toAppConfig(): AppConfig = AppConfig.from(this)