# Operations: GDPR

## What We Store

QuietMetrix is aggregate-only: the device computes `(metric, dims) -> n` counters itself and
sends only those. No raw event stream, no per-session trail, and no per-install identifier
exist anywhere in the pipeline (see `docs/security/publishable-api-key.md`).

- **No cookies** (beyond your own cookie consent banner integration)
- **IP addresses are never stored** — they are resolved to country codes (ISO 3166-1 alpha-2)
  and then discarded
- **No personal data, session IDs, or device/browser identifiers of any kind** are collected
- **k-anonymity on every read path**: a counter cell is invisible until at least `k` distinct
  devices have contributed to it (default 5, per-project configurable) — see
  `CounterRepository.readCells` / `counterReadCells()`

## Data Retention

| Plan | Retention |
|------|-----------|
| Self-hosted | Unlimited (you control it) |
| Free | 30 days |
| Hobby | 90 days |
| Startup | 12 months |
| Business | 24 months |

## Data Subject Access Requests (DSAR)

There is nothing to export or delete per individual, session, or device. A counter cell is a
sum across every device that contributed to it (`n`) plus a running distinct-device count
(`devices`) — neither carries an identifier, so there is no key to look a specific person up
by, and no way to subtract one person's contribution back out of an already-aggregated cell
without also affecting every other device that shares it. If a DSAR ever reaches you for
QuietMetrix data specifically, the accurate answer is that no individual-level record exists
to export or erase.

## Right to Erasure (whole project)

To delete all counter data for a project (e.g. the project itself is being removed, not a
single individual's data within it):

```bash
# Ktor/Postgres
psql -c "DELETE FROM counters WHERE project_id = <id>; DELETE FROM counters_quarantine WHERE project_id = <id>;"

# PHP/MySQL
mysql -e "DELETE FROM counters WHERE project_id = ?; DELETE FROM counters_quarantine WHERE project_id = ?;"
```

Or simply `DELETE /api/v1/projects/{id}` (Bearer auth, owner only), which removes the project
and cascades to its counter rows.

## Cookie Consent Integration

The QuietMetrix SDK provides built-in consent management:

```kotlin
setCookieConsent(true)   // user accepted
setCookieConsent(false)   // user declined
hasCookieConsent()        // check if user has made a choice
isTrackingAllowed()       // combined check (consent + default policy)
```

The consent state is stored in platform-native storage (SharedPreferences, NSUserDefaults, localStorage) and persists across app launches. Declining consent (or calling
`QuietMetrix.setAnalyticsEnabled(false)`) also discards any not-yet-flushed counters on the
device — see `MetricGateway.purge()`.
