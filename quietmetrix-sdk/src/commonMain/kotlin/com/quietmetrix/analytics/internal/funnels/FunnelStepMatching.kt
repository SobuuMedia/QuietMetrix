package com.quietmetrix.analytics.internal.funnels

import com.quietmetrix.analytics.FunnelStep

/**
 * Whether an emitted `(event, screen, props)` matches [step]: same event name, the declared
 * screen if any, and every declared prop present with an equal stringified value. Mirrors
 * servers/ktor's `FunnelMatcher.matchesStep` exactly — the on-device evaluator must agree with
 * the historical server-side semantics devs already rely on. [props] values are stringified
 * before comparison because [FunnelStep.props] is declared as exact-match strings while a
 * call site's props can be any type (matching how the wire DTOs stringify non-string values).
 */
internal fun funnelStepMatches(step: FunnelStep, event: String, screen: String?, props: Map<String, Any?>): Boolean {
    if (event != step.event) return false
    if (step.screen != null && screen != step.screen) return false
    for ((key, value) in step.props) {
        if (props[key]?.toString() != value) return false
    }
    return true
}
