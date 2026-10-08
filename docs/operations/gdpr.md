# Operations: GDPR

## What We Store

QuietMetrix is aggregate-only: the device computes `(metric, dims) -> n` counters itself and
sends only those. No raw event stream, no per-session trail, and no per-install identifier
exist anywhere in the pipeline (see `docs/security/publishable-api-key.md`).

- **No cookies** (beyond your own cookie consent banner integration)
- **IP addresses are never stored** — they are resolved to country codes (ISO 3166-1 alpha-2)
  and then discarded
- Analytics ingestion contains no installation identifiers or raw session trails. Dashboard accounts store email/password hashes and revocable authentication sessions separately.
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

Use `DELETE /api/v1/projects/{id}` with an authorized dashboard session. This removes
the project, legacy and v2 analytics, retry receipts, funnels/revisions, experiments,
memberships, and project-linked AI results/operations. Manual counter-only SQL does not
erase the whole project. Backups are separate copies: document their expiry and ensure a
restore reapplies deletion requests before serving traffic.

Ktor runs data maintenance every minute. PHP runs it on requests; an idle installation
must schedule a trusted CLI task every minute that loads its deployment config,
`src/db.php`, `src/helpers.php`, `src/auth.php`, and `src/maintenance.php`, then calls
`dataMaintenance(getDb(), true)`. Set `QM_RETENTION_DAYS` to a nonnegative number to
override analytics retention (0 means unlimited for self-hosted installations).
Hosted defaults follow the table above, using 365/730 days for Startup/Business.
Exact People windows crossing the retained boundary are unavailable rather than partial.
Rejected diagnostics are redacted and expire after seven days; retry receipts expire after
14 days. Account sessions and abuse-control hashes have their own short expiry.

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
