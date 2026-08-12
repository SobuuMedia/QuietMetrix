# SDK: Web (JavaScript / TypeScript)

The web SDK is published to npm as `@sobuumedia/quietmetrix-sdk`. It is framework-agnostic — the same API
works in Vue, React, Svelte, or plain JavaScript. TypeScript definitions are bundled.

It is compiled from the shared Kotlin Multiplatform core, so the browser behaves identically to
the Android and iOS SDKs. All browser lookups (localStorage, navigator, window) degrade
gracefully, so importing the package during server-side rendering (Nuxt, Next-style SSR) does
not throw — tracking simply becomes a no-op until it runs in the browser.

## Installation

```bash
npm install @sobuumedia/quietmetrix-sdk
```

```javascript
import { init, trackEvent } from "@sobuumedia/quietmetrix-sdk";

init({
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1/track", // your ingest URL
    apiKey: "qm_ak_your_api_key",                              // your API key
});
```

You can also import the whole namespace:

```javascript
import * as QuietMetrix from "@sobuumedia/quietmetrix-sdk";
QuietMetrix.init({ /* ... */ });
```

> **CDN / `window.QuietMetrix` global:** not yet available. A UMD/IIFE build for
> `<script>`-tag usage is planned for a future release. For now, install from npm and bundle
> with your app.

## Initialization

`init` takes an options object. Only `storageKeyPrefix`, `trackingEndpoint`, and `apiKey` are
commonly needed; the rest have sensible defaults.

```javascript
init({
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1/track",
    apiKey: "qm_ak_your_api_key",
    flushIntervalMs: 30000,             // default 30000
    maxQueueSize: 1000,                 // default 1000
    autoTrackInitialPageView: true,     // default true
    trackingAllowedByDefault: false,    // default false
    debug: false,                       // default false
});
```

`trackingEndpoint` must be `https://` (or omitted for offline-only mode).

## Track Events

```javascript
trackEvent("page_view", { screen: "home" });
trackEvent("button_click", { screen: "pricing", props: { plan: "startup" } });
trackEvent("signup_complete", { screen: "onboarding" });
```

`trackEvent` returns a `Promise`; you can `await` it if you need to know the event was queued.

## Funnels

Pass funnels to `init` and they auto-register with the server — no dashboard setup required.
See the [Funnels guide](funnels.md) for the full concept, matching rules, and worked example.

```typescript
import { init } from "@sobuumedia/quietmetrix-sdk";

init({
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1/track",
    apiKey: "qm_ak_your_api_key",
    funnels: [
        {
            key: "signup",
            name: "Signup",
            steps: [
                { key: "view", event: "screen_view", screen: "signup" },
                { key: "submit", event: "signup_submitted" },
            ],
        },
    ],
});
```

If you'd rather reference a step by key instead of calling `trackEvent` directly, use
`defineFunnel` to get a handle back:

```typescript
import { defineFunnel } from "@sobuumedia/quietmetrix-sdk";

const signupFunnel = defineFunnel({
    key: "signup",
    name: "Signup",
    steps: [
        { key: "view", event: "screen_view", screen: "signup" },
        { key: "submit", event: "signup_submitted" },
    ],
});

await signupFunnel.step("submit", { plan: "pro" });
```

## Using it in Vue

Initialize once in your entry file, then track route changes with the router:

```ts
// main.ts
import { createApp } from "vue";
import { init } from "@sobuumedia/quietmetrix-sdk";
import App from "./App.vue";
import router from "./router";

init({
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1/track",
    apiKey: "qm_ak_your_api_key",
});

createApp(App).use(router).mount("#app");
```

```ts
// router.ts — track page views on navigation
import { trackEvent } from "@sobuumedia/quietmetrix-sdk";

router.afterEach((to) => {
    trackEvent("page_view", { screen: to.path });
});
```

## Using it in React

Identical package, identical calls — just wire them into your app entry and router:

```tsx
// index.tsx
import { init } from "@sobuumedia/quietmetrix-sdk";

init({
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1/track",
    apiKey: "qm_ak_your_api_key",
});
```

```tsx
// track page views on route change (react-router)
import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import { trackEvent } from "@sobuumedia/quietmetrix-sdk";

export function usePageViews() {
    const location = useLocation();
    useEffect(() => {
        trackEvent("page_view", { screen: location.pathname });
    }, [location.pathname]);
}
```

## Consent

```javascript
import { setCookieConsent, setAnalyticsEnabled, isTrackingAllowed } from "@sobuumedia/quietmetrix-sdk";

// After the user accepts cookies via your consent banner
setCookieConsent(true);

// Master kill switch — separate from cookie consent
setAnalyticsEnabled(true);

// Check if tracking is currently allowed
if (isTrackingAllowed()) {
    // safe to track
}
```

Consent state is persisted in `localStorage` under the configured `storageKeyPrefix`.

## Force Flush

```javascript
import { flush } from "@sobuumedia/quietmetrix-sdk";

await flush();
```

Call this before the page unloads to ensure pending events are sent:

```javascript
window.addEventListener("beforeunload", () => {
    flush();
});
```

## Offline Support

Events are buffered in `localStorage` when the browser is offline and sent when connectivity is
restored. The `was_offline` flag is set on events captured while disconnected. The SDK listens
for `online`/`offline` browser events and flushes automatically when the connection returns.

## Kotlin/Wasm (Multiplatform)

If you are sharing Kotlin code across Android, iOS, and Web targets, the `wasmJs` target is also
included in `quietmetrix-sdk`:

```kotlin
dependencies {
    implementation("io.github.sobuumedia:quietmetrix-sdk:0.4.0")
}
```

The same `trackEvent`, `setCookieConsent`, and `setAnalyticsEnabled` calls work across all
targets.
