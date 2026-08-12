package com.quietmetrix.analytics

/**
 * One step in a [Funnel]: an event name the app already tracks, optionally narrowed by the
 * screen it fires on. `props` are exact-match filters used by server-side funnel matching —
 * they are NOT the props attached to an emitted event (see [Funnel.step] for that).
 */
data class FunnelStep(
    val key: String,
    val event: String,
    val name: String? = null,
    val screen: String? = null,
    val props: Map<String, String> = emptyMap(),
)

/** Whether a funnel measures one actor or a separately correlated attempt. */
enum class FunnelCountMode { ACTOR, ATTEMPT }

/** The identity space used to join funnel events. */
enum class FunnelIdentityScope { INSTALL_OR_SESSION, INSTALL, SESSION }

/**
 * A versioned, code-owned set of funnels. Increment [revision] whenever its definitions change.
 * The server ignores older manifests, which prevents an older app release from overwriting a
 * newer definition during a staged rollout.
 */
data class FunnelManifest(
    val namespace: String,
    val revision: Long,
    val funnels: List<Funnel>,
)

/**
 * A funnel an app declares in code. Pass a list of these to [QuietMetrixConfig.funnels] and
 * the SDK auto-registers the definition with the server once per app version — it then
 * appears in the dashboard with no further setup.
 *
 * Declaring a funnel emits no events by itself: steps reference event names the app already
 * sends, so a funnel matches retroactively over data you already have. [step] is an optional
 * typed convenience for the steps you'd rather call by key than by raw event name — most
 * apps do not need it.
 */
class Funnel(
    val key: String,
    val name: String,
    val steps: List<FunnelStep>,
    val windowSeconds: Long = 7L * 24 * 3600,
    val description: String? = null,
    val countMode: FunnelCountMode = FunnelCountMode.ACTOR,
    val identityScope: FunnelIdentityScope = FunnelIdentityScope.INSTALL_OR_SESSION,
    /** Required for [FunnelCountMode.ATTEMPT]; every step event must carry this property. */
    val correlationProperty: String? = null,
) {
    /** Emits the named step's event, merging its declared [FunnelStep.props] with [props] (which win on conflict). Silently does nothing if [stepKey] is not declared on this funnel. */
    suspend fun step(stepKey: String, props: Map<String, Any?> = emptyMap()) {
        val declared = steps.firstOrNull { it.key == stepKey } ?: return
        val merged: Map<String, Any?> = declared.props.mapValues { (_, v) -> v as Any? } + props
        trackEvent(declared.event, declared.screen, merged)
    }
}
