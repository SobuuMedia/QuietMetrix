package com.quietmetrix.server.funnels

import kotlinx.serialization.Serializable

/**
 * One step in a funnel: an existing event name, optionally narrowed by an exact-match
 * `screen` and/or `props` filter. Declaring a funnel emits no new events — steps reference
 * events the app already sends.
 *
 * [props] being typed `Map<String, String>` is this backend's mirror of
 * `php-hosting/src/funnelValidation.php`'s "props values must be scalar" rule — a JSON body
 * with a nested-object prop value fails to decode before validation ever runs (isLenient is
 * false, see Serialization.kt), which is strictly stronger than PHP's runtime array check.
 */
@Serializable
data class FunnelStepDefinition(
    val key: String,
    val event: String,
    val name: String? = null,
    val screen: String? = null,
    val props: Map<String, String> = emptyMap(),
)

/** A funnel definition, as stored in the `funnels` table. */
@Serializable
data class FunnelDefinition(
    val funnelKey: String,
    val name: String,
    val description: String? = null,
    val steps: List<FunnelStepDefinition>,
    val windowSeconds: Long = FunnelValidation.DEFAULT_WINDOW_SECONDS,
    val source: String = "dashboard",
    val locked: Boolean = false,
    val countMode: String = "actor",
    val identityScope: String = "install_or_session",
    val correlationProperty: String? = null,
)
