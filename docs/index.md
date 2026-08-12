# QuietMetrix

Privacy-respecting analytics for every platform. Open-source, self-hostable, GDPR-clean.

## 30-second install

=== "Docker (Ktor + Postgres)"

    ```bash
    git clone https://github.com/sobuumedia/quietmetrix.git
    cd quietmetrix
    docker compose up -d
    # Visit https://yourhost/setup to create your first admin user
    ```

=== "Shared hosting (PHP + MySQL)"

    ```bash
    # 1. Upload php-hosting/ contents to your public_html/
    # 2. Upload everything else above the webroot
    # 3. composer install --no-dev
    # 4. Copy .env.example → .env, set DB creds
    # 5. Visit /setup to run migrations and create admin
    # 6. Add cron: * * * * * php /path/to/bin/qm-worker.php
    ```

=== "Cloud (managed)"

    Sign up at [cloud.quietmetrix.com](https://cloud.quietmetrix.com) — no self-hosting needed.

## Features

- **Multi-platform SDK**: Android, iOS, macOS, Windows, Linux, Web, JVM
- **Funnels**: declare a step sequence in code, get drop-off, breakdown, and time-to-convert in the dashboard automatically
- **Consent-first**: Cookie consent and analytics kill switch built-in
- **Two interchangeable backends**: PHP+MySQL or Kotlin+Ktor+PostgreSQL
- **Offline support**: Events buffered locally, sent when connectivity returns
- **Open source**: MIT license, no vendor lock-in
- **Self-hostable**: Run on a $3/mo shared host or as a Docker container

## Quick start (SDK)

### Android / Kotlin Multiplatform

```kotlin
QuietMetrix.init(QuietMetrixConfig(
    storageKeyPrefix = "myapp_",
    trackingEndpoint = "https://your-server.com/api/v1/track",
    apiKey = "qm_ak_your_api_key_here",
))

// Track an event
trackEvent("button_click", screen = "settings", props = mapOf("id" to "save_btn"))

// User consent
setCookieConsent(true)       // or false
setAnalyticsEnabled(true)     // master kill switch

// Force flush pending events
suspend fun onSave() { QuietMetrix.flush() }
```

See [Funnels](sdk/funnels.md) for how to declare a step sequence and get it analyzed
automatically.

### iOS (Swift)

```swift
let config = QuietMetrixConfig(storageKeyPrefix: "myapp_", trackingEndpoint: "https://your-server.com/api/v1/track", apiKey: "qm_ak_...")
QuietMetrix.shared.initialize(config: config)
QuietMetrix.shared.trackEvent(event: "button_click", screen: "settings")
```

### Web (JavaScript/Wasm)

```html
<script src="quietmetrix.js"></script>
<script>
  QuietMetrix.init({ storageKeyPrefix: 'myapp_', trackingEndpoint: 'https://your-server.com/api/v1/track', apiKey: 'qm_ak_...' });
  QuietMetrix.trackEvent('page_view', { screen: 'home' });
</script>
```

## Architecture

```
   ┌──────────────┐     HTTPS      ┌─────────────────────────┐
   │  KMP SDK     │ ─────────────► │  Self-hosted PHP        │
   │ (7 targets)  │                │  (Apache/Nginx + MySQL) │
   └──────────────┘                └─────────────────────────┘
                                   ┌─────────────────────────┐
                                   │  Self-hosted Ktor        │
                                   │  (Docker + Postgres)     │
                                   └─────────────────────────┘
```

Same wire protocol, same dashboard semantics, three deployment shapes.