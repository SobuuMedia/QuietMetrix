package com.quietmetrix.analytics

/**
 * Configuration for QuietMetrix. Pass once to [QuietMetrix.init] at app startup.
 *
 * @param storageKeyPrefix Prefix for all persistent storage keys (localStorage on web,
 *   SharedPreferences on Android, NSUserDefaults on iOS, in-memory on JVM). Pick a unique
 *   value per app (e.g. "myapp_") so multiple consumers on the same origin do not collide.
 * @param trackingEndpoint URL that receives POSTed events. Used by the wasmJs target only.
 *   Null disables network sending; events still respect consent and run consent/banner logic.
 * @param autoTrackInitialPageView If true, the wasmJs tracker fires a single `page_view`
 *   event on init. Set to false if you'd rather emit it manually.
 * @param trackingAllowedByDefault If the user has not yet made a consent choice, should
 *   events fire? Default true preserves the privacy posture of "anonymous analytics until
 *   declined". Set to false for stricter opt-in semantics.
 * @param funnels Legacy unversioned funnels. Prefer [funnelManifest] for new integrations.
 * @param funnelManifest Versioned, code-owned funnels. Auto-registered with the server once per
 *   meaningful change (see [com.quietmetrix.analytics.internal.funnels.FunnelRegistrar]) —
 *   they then appear in the dashboard with no further setup. Declaring a funnel emits no
 *   events by itself.
 * @param collectAnonymousId Whether to generate and send a persistent per-install pseudonymous
 *   id (`ctx.anonymous_id`, salt-hashed server-side into a never-rotated `install_hash`). This
 *   id is what lets funnels and retention be counted per-install rather than per-session —
 *   without it, funnels spanning more than one app session under-report. Default `true`. Set
 *   `false` for a stricter anonymous posture with no persistent identifier of any kind; no id is
 *   generated or written to storage, and `ctx.anonymous_id` is omitted from every event.
 */
data class QuietMetrixConfig(
    val storageKeyPrefix: String,
    val trackingEndpoint: String? = null,
    val apiKey: String? = null,
    val flushIntervalMs: Long = 30_000L,
    val maxQueueSize: Int = 1000,
    val autoTrackInitialPageView: Boolean = true,
    val trackingAllowedByDefault: Boolean = false,
    val userAgent: String? = null,
    val applicationContext: Any? = null,
    val debug: Boolean = false,
    @Deprecated("Use funnelManifest so older app releases cannot overwrite newer definitions")
    val funnels: List<Funnel> = emptyList(),
    val funnelManifest: FunnelManifest? = null,
    val collectAnonymousId: Boolean = true,
)
