# SDK: Android

> Setting this up for the first time? An AI agent can create the project and fill in the
> values below for you — see [Agent-driven setup](../agents/setup.md).

## Installation

Add the QuietMetrix dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.quietmetrix:quietmetrix-sdk:0.4.0")
}
```

## Initialization

Initialize in your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        QuietMetrix.init(QuietMetrixConfig(
            storageKeyPrefix = "myapp_",
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