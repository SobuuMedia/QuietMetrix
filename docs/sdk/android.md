# SDK: Android

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
            trackingEndpoint = "https://your-server.com/api/v1/track",
            apiKey = "qm_ak_your_api_key",
            flushIntervalMs = 30_000L,
            maxQueueSize = 1000,
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
    trackingEndpoint = "https://your-server.com/api/v1/track",
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

Events are automatically buffered locally when the device is offline and sent when connectivity is restored. The `was_offline` flag is set on events captured while disconnected.