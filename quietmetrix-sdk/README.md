# QuietMetrix Core SDK

Lightweight privacy-respecting analytics SDK for Kotlin Multiplatform.

## What is automatically collected

The SDK collects the following fields by default when events are sent:

| Field | Purpose | Storage |
|-------|---------|---------|
| `anonymous_id` | Stable per-device identifier for user-journey analytics | Persistent (SharedPreferences / NSUserDefaults / localStorage / disk file) |
| `platform` | Platform name (e.g. `android`, `ios`, `web`) | Collected per-event |
| `os_name` | Operating system name | Collected per-event |
| `os_version` | OS version string | Collected per-event |
| `browser_name` / `browser_version` | Browser detection (web target) | Collected per-event |
| `device_model` | Device model string | Collected per-event |
| `screen_width` / `screen_height` | Logical screen dimensions in pixels | Collected per-event |
| `language` | Device locale/language | Collected per-event |
| `user_agent` | HTTP User-Agent header (web target) | Collected per-event |
| `app_version` | Application version (if provided by integrator) | Collected per-event |
| `sdk_version` | QuietMetrix SDK version | Collected per-event |

### Privacy note

These fields are device-level metadata, not personal information by themselves. However, when combined they can form a stable fingerprint. Integrators should:

- Review this list against their privacy policy
- Disable specific fields via `DeviceContext` overrides on each platform if needed
- Set `trackingAllowedByDefault = false` and gate collection behind explicit user consent for GDPR/CCPA compliance

### Persistence model

- **`anonymous_id`**: Stored in platform-persistent storage (SharedPreferences on Android, NSUserDefaults on iOS, localStorage on web, disk file on desktop/JVM). Survives app restarts but is scoped per `storageKeyPrefix`.
- **All other fields**: Collected fresh on each event or on each init cycle. Not persisted beyond the event payload.
- **Event queue**: Flushed to the server within `flushIntervalMs` (default 30s). If the network is unavailable, events are buffered in memory up to `maxQueueSize` and retried with exponential backoff.
- **Consent state**: Tracked via `InMemoryStore` only — resets on app restart. Use `setAnalyticsEnabled()` after each init to restore the user's choice.
