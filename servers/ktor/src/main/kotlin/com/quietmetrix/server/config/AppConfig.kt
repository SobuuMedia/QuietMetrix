package com.quietmetrix.server.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val profile: Profile,
    val db: DbConfig,
    val auth: AuthConfig,
    val rateLimit: RateLimitConfig,
    val billing: BillingConfig?,
    val cors: CorsConfig = CorsConfig(),
) {
    enum class Profile { SELFHOST, CLOUD }

    val isCloud: Boolean get() = profile == Profile.CLOUD

    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            val profileStr = config.propertyOrNull("quietmetrix.profile")?.getString() ?: "selfhost"
            val profile = Profile.entries.firstOrNull {
                it.name.equals(profileStr, ignoreCase = true)
            } ?: Profile.SELFHOST

            val billingConfig = if (profile == Profile.CLOUD) {
                BillingConfig(
                    provider = config.property("quietmetrix.billing.provider").getString(),
                    stripeSecretKey = config.propertyOrNull("quietmetrix.billing.stripeSecretKey")?.getString(),
                    stripeWebhookSecret = config.propertyOrNull("quietmetrix.billing.stripeWebhookSecret")?.getString(),
                    adyenApiKey = config.propertyOrNull("quietmetrix.billing.adyenApiKey")?.getString(),
                    adyenMerchantAccount = config.propertyOrNull("quietmetrix.billing.adyenMerchantAccount")?.getString(),
                    adyenHmacKey = config.propertyOrNull("quietmetrix.billing.adyenHmacKey")?.getString(),
                )
            } else null

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
                    sessionTtlHours = config.propertyOrNull("quietmetrix.auth.sessionTtlHours")?.getString()?.toInt() ?: 24,
                ),
                rateLimit = RateLimitConfig(
                    enabled = config.propertyOrNull("quietmetrix.rateLimit.enabled")?.getString()?.toBoolean() ?: (profile == Profile.CLOUD),
                    requestsPerSecond = config.propertyOrNull("quietmetrix.rateLimit.requestsPerSecond")?.getString()?.toInt() ?: 10,
                    burstPerMinute = config.propertyOrNull("quietmetrix.rateLimit.burstPerMinute")?.getString()?.toInt() ?: 60,
                ),
                billing = billingConfig,
                cors = CorsConfig(
                    allowedOrigins = config.propertyOrNull("quietmetrix.cors.allowedOrigins")?.getList() ?: emptyList(),
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
            if (appConfig.isCloud) {
                require(!appConfig.billing?.stripeWebhookSecret.isNullOrBlank() || !appConfig.billing?.adyenHmacKey.isNullOrBlank()) {
                    "CLOUD profile requires QM_STRIPE_WEBHOOK_SECRET or QM_ADYEN_HMAC_KEY"
                }
            }
            return appConfig
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

data class BillingConfig(
    val provider: String,
    val stripeSecretKey: String?,
    val stripeWebhookSecret: String?,
    val adyenApiKey: String?,
    val adyenMerchantAccount: String?,
    val adyenHmacKey: String?,
)

fun ApplicationConfig.toAppConfig(): AppConfig = AppConfig.from(this)