# SDK: Web (Wasm)

## Installation

### npm

```bash
npm install @quietmetrix/sdk
```

```javascript
import { init, trackEvent, setCookieConsent, setAnalyticsEnabled } from "@quietmetrix/sdk";

init({
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1/track",
    apiKey: "qm_ak_your_api_key",
});
```

### CDN

Include the script tag in your HTML. The SDK attaches to `window.QuietMetrix`:

```html
<script src="https://cdn.quietmetrix.com/sdk/v0.2.0/quietmetrix.js"></script>
<script>
    QuietMetrix.init({
        storageKeyPrefix: "myapp_",
        trackingEndpoint: "https://your-server.com/api/v1/track",
        apiKey: "qm_ak_your_api_key",
    });
</script>
```

### Kotlin/Wasm (Multiplatform)

If you are sharing Kotlin code across Android, iOS, and Web targets, the `wasmJs` target is already included in `quietmetrix-sdk`:

```kotlin
dependencies {
    implementation("com.quietmetrix:quietmetrix-sdk:0.2.0")
}
```

The same `trackEvent`, `setCookieConsent`, and `setAnalyticsEnabled` calls work across all targets.

## Initialization

```javascript
QuietMetrix.init({
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1/track",
    apiKey: "qm_ak_your_api_key",
    flushIntervalMs: 30000,
    maxQueueSize: 1000,
});
```

## Track Events

```javascript
QuietMetrix.trackEvent("page_view", { screen: "home" });
QuietMetrix.trackEvent("button_click", { screen: "pricing", props: { plan: "startup" } });
QuietMetrix.trackEvent("signup_complete", { screen: "onboarding" });
```

## Page Views

The SDK does **not** auto-track page views. To track page views on a single-page application, call `trackEvent` on route changes:

```javascript
// With a SPA router (React, Vue, etc.)
router.afterEach((to) => {
    QuietMetrix.trackEvent("page_view", { screen: to.path });
});
```

For traditional multi-page sites, call it on every page load:

```html
<script>
    QuietMetrix.init({ /* ... */ });
    QuietMetrix.trackEvent("page_view", { screen: window.location.pathname });
</script>
```

## Consent

```javascript
// After user accepts cookies via your consent banner
QuietMetrix.setCookieConsent(true);

// Master kill switch — separate from cookie consent
QuietMetrix.setAnalyticsEnabled(true);

// Check if tracking is currently allowed
if (QuietMetrix.isTrackingAllowed()) {
    // safe to track
}
```

The consent state is persisted in `localStorage` under the configured `storageKeyPrefix`.

## Identify

The `identify` call hashes the user ID before sending it to the server:

```javascript
QuietMetrix.identify("user_123");
```

## Force Flush

```javascript
await QuietMetrix.flush();
```

Call this before the page unloads to ensure pending events are sent:

```javascript
window.addEventListener("beforeunload", () => {
    QuietMetrix.flush();
});
```

## Offline Support

Events are buffered in `localStorage` when the browser is offline and sent when connectivity is restored. The `was_offline` flag is set on events captured while disconnected. The SDK listens for `online`/`offline` browser events and flushes automatically when the connection returns.

## Builder Pattern (CDN / Global)

When using the CDN build, you can also use the builder-style API:

```javascript
QuietMetrix.init({ /* ... */ })
    .trackEvent("page_view", { screen: "home" });
```