package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.funnels.FunnelEvaluator

/**
 * The counter-native tail of every platform's `trackEvent` actual: records an `event{name}`
 * counter rather than enqueuing a raw event. The SDK's public `trackEvent(event, screen,
 * props)` signature is unchanged, but nothing it records is a reconstructable per-session
 * trail any more.
 *
 * [props] (and [screen] — the `event` metric itself declares only `{name}`, see
 * CounterRegistry.kt) are never sent as part of the `event` counter; in [debug] mode a dropped
 * non-empty [props] is logged so integrators notice during development rather than silently
 * losing data they thought they were sending. [screen] and [props] are still passed to
 * [FunnelEvaluator], which matches them locally against any configured funnel's next expected
 * step and reports only the depth reached — never the trail these values would otherwise be.
 */
internal suspend fun recordEventCounter(
    event: String,
    screen: String?,
    props: Map<String, Any?>,
    debug: Boolean = ConfigHolder.configOrNull?.debug == true,
) {
    if (debug && props.isNotEmpty()) {
        println("[QuietMetrix] trackEvent(\"$event\") props are not sent under aggregate-only ingest (dropped): ${props.keys}")
    }
    MetricGateway.record("event", mapOf("name" to event))
    FunnelEvaluator.onEvent(event, screen, props)
    ConfigHolder.configOrNull?.let { ActivationReporter.onEvent(it, event) }
}
