# SDK: Desktop (macOS / Windows / Linux)

## Installation

Add the QuietMetrix dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.quietmetrix:quietmetrix-sdk:0.2.0")
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

## Identify

The `identify` call hashes the user ID before sending it to the server:

```kotlin
QuietMetrix.identify("user_123")
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