# SDK: JVM

> Setting this up for the first time? An AI agent can create the project and fill in the
> values below for you — see [Agent-driven setup](../agents/setup.md).

## Installation

Add the QuietMetrix dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.quietmetrix:quietmetrix-sdk:0.4.0")
}
```

For Maven projects, add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.quietmetrix</groupId>
    <artifactId>quietmetrix-sdk-jvm</artifactId>
    <version>0.4.0</version>
</dependency>
```

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
    // ... start your application
}
```

### Java

```java
public class MyApp {
    public static void main(String[] args) {
        QuietMetrixConfig config = new QuietMetrixConfig(
            "myapp_",
            "https://your-server.com/api/v1/track",
            "qm_ak_your_api_key",
            30_000L,
            1000
        );
        QuietMetrix.INSTANCE.init(config);
    }
}
```

## Track Events

```kotlin
trackEvent("api_request", screen = "users", props = mapOf("method" to "GET", "status" to "200"))
trackEvent("job_completed", screen = "worker", props = mapOf("duration_ms" to "1250"))
```

### Java

```java
Map<String, String> props = new HashMap<>();
props.put("method", "GET");
props.put("status", "200");
QuietMetrixKt.trackEvent("api_request", "users", props);
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

For Java, use `CountDownLatch` or call from a coroutine scope:

```java
CoroutineScopeKt.runBlocking(Void.INSTANCE, (scope, cont) -> {
    QuietMetrix.INSTANCE.flush(cont);
    return Unit.INSTANCE;
});
```

Or more practically, call flush at application shutdown:

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    runBlocking { QuietMetrix.flush() }
})
```

## Offline Support

Events are buffered to local disk when the server is unreachable and sent automatically on the next flush cycle. The `was_offline` flag is set on events captured while disconnected. On JVM, persistence uses a file-based store in the system temp directory (configurable via `storageKeyPrefix`).