# SDK: Desktop (macOS / Windows / Linux)

> Setting this up for the first time? An AI agent can create the project and fill in the
> values below for you — see [Agent-driven setup](../agents/setup.md).

## Installation

Add the QuietMetrix dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.quietmetrix:quietmetrix-sdk:0.4.0")
}
```

The KMP SDK publishes multiplatform artifacts for `macosArm64`, `macosX64`, `windowsX64`, and `linuxX64`. Gradle resolves the correct variant automatically based on your target.

### Platform-specific notes

| Platform | Implementation detail |
|----------|----------------------|
| macOS | Uses `NSUserDefaults` for persistence, `Foundation` for HTTP |
| Windows | Uses file-based persistence, `HttpClient` (JVM) for HTTP |
| Linux | Uses file-based persistence, `HttpClient` for HTTP |

## Initialization

Initialize early in your application startup, before any events are tracked:

```kotlin
fun main() {
    QuietMetrix.init(QuietMetrixConfig(
        storageKeyPrefix = "myapp_",
        trackingEndpoint = "https://your-server.com/api/v1/track",
        apiKey = "qm_ak_your_api_key",
        flushIntervalMs = 30_000L,
        maxQueueSize = 1000,
    ))
    // ... start your UI
}
```

## Track Events

```kotlin
trackEvent("window_opened", screen = "main", props = mapOf("theme" to "dark"))
trackEvent("file_exported", screen = "editor", props = mapOf("format" to "pdf"))
trackEvent("shortcut_used", screen = "editor", props = mapOf("key" to "Ctrl+S"))
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

## Force Flush

```kotlin
runBlocking {
    QuietMetrix.flush()
}
```

Call this before your process exits to ensure pending events are sent:

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    runBlocking { QuietMetrix.flush() }
})
```

## Offline Support

Events are buffered to local disk storage when no network is available and sent automatically when connectivity is restored. The `was_offline` flag is set on events captured while disconnected.