# SDK: Android

> Setting this up for the first time? An AI agent can create the project and fill in the
> values below for you — see [Agent-driven setup](../agents/setup.md).

## Installation

Add the QuietMetrix dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.sobuumedia:quietmetrix-sdk:0.8.3")
}
```

## Android SDK compatibility

| Artifact | Minimum consumer `compileSdk` | Device `minSdk` |
|---|---|---|
| Core (`quietmetrix-sdk`) | 24 | 24 |
| Compose adapter (`quietmetrix-sdk-compose`) | 34 | 24 |

QuietMetrix itself builds against SDK 37. Its explicit AAR `minCompileSdk` values
allow consumers to build against the baselines above or newer SDKs, following
[Android's library guidance](https://developer.android.com/build/publish-library/prep-lib-release#choose-minCompileSdk).
The published 0.8.2 artifacts still require compile SDK 37; these lower requirements
apply starting with 0.8.3.

The optional Compose adapter depends only on Compose Runtime. It does not bring in
Foundation, UI or tooling. The debug overlay has separate UI dependencies and is not
covered by the adapter's baseline. Your app's other dependencies can require a higher
compile SDK: align Compose tooling and UI versions separately if their AAR checks fail.
Lowering QuietMetrix's requirement cannot lower requirements declared by those artifacts.

The [independent consumer builds](../../tools/release-consumers/README.md) verify both
baselines against published Maven artifacts. Keep `minSdk` (device support) separate
from `compileSdk` (build APIs) and `minCompileSdk` (the AAR consumer requirement).

## Initialization

Initialize in your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        QuietMetrix.init(QuietMetrixConfig(
            storageKeyPrefix = "myapp_",
            applicationContext = applicationContext,
            trackingEndpoint = "https://your-server.com/api/v1",
            apiKey = "qm_ak_your_api_key",
            flushIntervalMs = 30_000L,
        ))
    }
}
```

## Track Events

```kotlin
trackEvent("button_click", screen = "settings", props = mapOf("id" to "save_btn"))
trackEvent("page_view", screen = "home")
```

## Funnels

Declare funnels on `QuietMetrixConfig` and they auto-register with the server — no dashboard
setup required. See the [Funnels guide](funnels.md) for the full concept, matching rules, and
worked example.

```kotlin
import com.quietmetrix.analytics.Funnel
import com.quietmetrix.analytics.FunnelStep

val signupFunnel = Funnel(
    key = "signup",
    name = "Signup",
    steps = listOf(
        FunnelStep(key = "view", event = "screen_view", screen = "signup"),
        FunnelStep(key = "submit", event = "signup_submitted"),
    ),
)

QuietMetrix.init(QuietMetrixConfig(
    storageKeyPrefix = "myapp_",
    trackingEndpoint = "https://your-server.com/api/v1",
    apiKey = "qm_ak_your_api_key",
    funnels = listOf(signupFunnel),
))
```

## Experiments (A/B testing)

Create an experiment in the dashboard's Experiments tab, then branch on it in code — no
config to declare here, unlike funnels. See the [Experiments guide](experiments.md) for the
full concept, timing, and country-targeting details.

```kotlin
import com.quietmetrix.analytics.getVariant
import com.quietmetrix.analytics.trackExperimentInteraction

when (getVariant("checkout_cta")) {
    "b" -> NewCheckoutButton(onClick = { trackExperimentInteraction("checkout_cta") })
    else -> OldCheckoutButton() // "a", or "none" if not enrolled
}
```

## Consent

```kotlin
// After user accepts cookies
setCookieConsent(true)

// Master kill switch — separate from cookie consent
setAnalyticsEnabled(false)
```

## Force Flush

```kotlin
lifecycleScope.launch {
    QuietMetrix.flush()
}
```

## Offline Support

Pending counters are in-memory only — there is no offline buffer, no connectivity detection, and no retry backoff. A flush that fails (offline, 5xx, timeout) simply drops that batch rather than queuing it. An app killed between flushes loses whatever was recorded since the last successful one.

## Friction (Rage-tap Detection)

QuietMetrix automatically detects "rage taps" — repeated fast taps in roughly the same spot, usually a sign the user is stuck or the UI didn't respond — and reports them as a `friction` counter, broken down by screen. No setup is required: the SDK wraps each `Activity`'s `Window.Callback` from the same lifecycle hook it already uses for screen-dwell tracking. (iOS requires a one-line opt-in — see [the iOS guide](ios.md#friction-rage-tap-detection); JVM/Linux/Windows/Web have no tap-capture signal at all.)
The 0.8.3 artifacts are built with Kotlin 2.4.21. Use a compatible Kotlin compiler;
AGP built-in Kotlin may otherwise select an older compiler. The standalone
[Android consumer](../../tools/release-consumers/README.md) shows the tested compiler
configuration and process-restart proof.
