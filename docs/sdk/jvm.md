# SDK: JVM

## Installation

Add the QuietMetrix dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.quietmetrix:quietmetrix-core:0.2.0")
}
```

For Maven projects, add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.quietmetrix</groupId>
    <artifactId>quietmetrix-core-jvm</artifactId>
    <version>0.2.0</version>
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