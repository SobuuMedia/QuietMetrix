# QuietMetrix

Lightweight, privacy-respecting analytics SDK for Kotlin Multiplatform.

Targets: wasmJs, Android, JVM, iOS.

## Add to your project

```kotlin
implementation("com.quietmetrix:quietmetrix-core:0.1.0")
```

## Initialize once at startup

```kotlin
import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig

QuietMetrix.init(
    QuietMetrixConfig(
        storageKeyPrefix = "myapp_",
        trackingEndpoint = "https://example.com/analytics/track",
    )
)
```

## Track an event

```kotlin
import com.quietmetrix.analytics.trackEvent

trackEvent("login")
trackEvent("screen_view", screen = "home")
```

## License

MIT — see [LICENSE](LICENSE).
