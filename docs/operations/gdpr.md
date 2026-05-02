# Operations: GDPR

## What We Store

QuietMetrix is designed for privacy from the ground up:

- **No cookies** (beyond your own cookie consent banner integration)
- **IP addresses are never stored** — they are resolved to country codes (ISO 3166-1 alpha-2) and then discarded
- **User agents are classified** into device categories (desktop/mobile/tablet/bot) and then discarded
- **No personal data** is collected by default
- **Session IDs** are random, per-tab, and never linked to PII

## Data Retention

| Plan | Retention |
|------|-----------|
| Self-hosted | Unlimited (you control it) |
| Free | 30 days |
| Hobby | 90 days |
| Startup | 12 months |
| Business | 24 months |

## Data Subject Access Requests (DSAR)

The admin API provides endpoints for data export and deletion:

- Export all events for a session ID
- Delete all events for a session ID

These can be wired to your existing DSAR workflow.

## Right to Erasure

To delete all data for a project:

```bash
# Ktor/Postgres
psql -c "DELETE FROM events WHERE project_id = <id>; DELETE FROM events_inbox WHERE project_id = <id>;"

# PHP/MySQL
mysql -e "DELETE FROM events WHERE project_id = <id>; DELETE FROM events_inbox WHERE project_id = <id>;"
```

## Cookie Consent Integration

The QuietMetrix SDK provides built-in consent management:

```kotlin
setCookieConsent(true)   // user accepted
setCookieConsent(false)   // user declined
hasCookieConsent()        // check if user has made a choice
isTrackingAllowed()       // combined check (consent + default policy)
```

The consent state is stored in platform-native storage (SharedPreferences, NSUserDefaults, localStorage) and persists across app launches.