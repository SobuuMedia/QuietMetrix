# Publishable API Key — Threat Model & Abuse Defense

The QuietMetrix **API key** (header `X-QM-Api-Key`, format `qm_ak_…`) is a **publishable,
write-only, project-scoped** credential. It is *not a secret* and is expected to ship inside
client applications — including browser bundles, mobile apps, and **F-Droid** builds.

This document is the authoritative threat model for that key and the abuse-defense controls
the platform applies so the key can be shared publicly (including committed to a public
repository) without compromising data integrity or other projects.

---

## TL;DR

- The key can only **write counters** to **one project**. It cannot read analytics, list or
  modify projects, regenerate itself, access other projects, or user accounts.
  Read/admin routes require a JWT obtained via login.
- Because it ships in client code, assume it is **public** the moment a build is published.
  Design around that — do not rely on its secrecy.
- Abuse is bounded by **per-project** and **per-IP** throttling, a fixed **dimension
  registry** (an unknown metric or undeclared dimension is rejected, not stored), and a
  **cardinality cap** per metric. There is no per-install identifier to throttle or hash —
  the aggregate-only wire format never carries one.
- Use a **dedicated** project (separate from production) for any consumer that publishes the
  key (F-Droid, public demo repos, open-source apps). Treat its key as **burnable** and
  **rotate** it via `POST /api/v1/projects/{id}/regenerate-key` on abuse.

---

## Scope of the key — what publishing it grants

An attacker holding a published key can do exactly one thing to that one project:

1. `POST /api/v1/counters` — submit a batch of counter deltas: `(metric, dims) -> n`.

| Capability | Granted by API key? |
|---|---|
| Submit counters into the key's own project | Yes |
| Read aggregates / dashboard data | No (JWT or PAT with `analytics:read`) |
| Create / delete projects, list projects | No (JWT) |
| Add/remove project members | No (JWT) |
| Regenerate the API key | No (JWT) |
| Access *other* projects' data | No (per-project scoping) |
| User account / admin routes | No (JWT) |

Storage:
- The DB stores a **bcrypt hash + SHA-256 hash**, never the raw key.
- Hot-path validation uses the indexed SHA-256 column — fast lookup, no per-row BCrypt.
- Last 4 characters (`api_key_last4`) are stored for display only.

---

## F-Droid publishing guidance

For an Android app distributed via F-Droid (or any sideloadable / reproducible build):

1. **Create a dedicated F-Droid project** separate from production analytics. Only that
   project's key ships in the APK. If it is abused, real analytics are untouched.
2. **Rotate the key** via `POST /api/v1/projects/{id}/regenerate-key` (JWT-protected) when
   you suspect abuse, then ship a new build.
3. **Document it for F-Droid reviewers.** Add a short *Privacy & Abuse Defense* note in
   your app's listing referencing this doc, so the embedded `qm_ak_…` string is read as a
   publishable, write-only, project-scoped analytics key — not an undocumented secret.

Note that F-Droid builds cannot use Google Play Integrity / SafetyNet. The platform
therefore does **not** depend on app attestation. Trust is built from behavioural signals
server-side (per-IP throttling, the dimension registry, the cardinality cap), not from
proving which app made a request.

---

## Abuse-defense controls

### Per-project rate limiting

A token bucket keyed `counters:$projectId` bounds total ingest volume per project.

### Per-IP rate limiting

A second token bucket keyed `counters:ip:<clientIp>` (using the trusted-proxy-validated
`clientIp()`) bounds ingest per source address. A single-host attacker cannot saturate the
project's shared bucket.

Env vars: `QM_INGEST_IP_RPS`, `QM_INGEST_IP_BURST` (Ktor);
`INGEST_IP_RPS`, `INGEST_IP_BURST` (PHP).

### Dimension registry + cardinality cap

Every counter item is validated against a fixed, server-declared per-metric registry (see
`CounterRegistry.kt` / `counterRegistry.php`): an unknown metric, an undeclared dimension
key, or a value outside the allowed charset is rejected before it is ever stored. A
per-metric distinct-cell cardinality cap (`COUNTERS_MAX_DISTINCT_CELLS_PER_METRIC` /
`config.counters.maxDistinctCellsPerMetric`) additionally bounds how many distinct dims
combinations one metric may accumulate, so an attacker cannot grow the table without limit
by inventing new dimension values. Anything rejected by either check lands in
`counters_quarantine` for operator review rather than the `counters` table.

### Origin baseline

For non-mobile traffic (when `Origin`/`Referer` is present), the project may enforce an
`allowed_origins` allowlist server-side independent of CORS — collapsing cross-origin abuse
for any web consumer reusing the same backend. For mobile (no `Origin`), this check is
skipped.

### k-anonymity

Every read path filters a counter cell out until at least `k` distinct devices have
contributed to it (`counters.devices >= k`, default 5, per-project configurable). This isn't
an abuse-defense control in the same sense as the above — it exists to make retroactive
re-identification of a small group of users unattractive, not to reject bad writes.

---

## Privacy note

There is no per-install identifier anywhere in this pipeline. The old event-stream ingest
(`/track`) carried a per-install `anonymousId`, salt-hashed server-side before storage; that
entire mechanism — the identifier, the salt, the hashing, and the tables it landed in — was
removed when QuietMetrix moved to aggregate-only ingest. A counter item's `u` flag (`1` on
the first flush of a given cell on a given day, `0` after) is the SDK's own signal, summed
server-side into a distinct-device count (`counters.devices`) with no identifier ever
existing to hash, log, or correlate across API-key rotations.

---

## Operational recovery

If a published key is abused:

1. `POST /api/v1/projects/{id}/regenerate-key` — old key stops working immediately.
2. Ship a new app build with the rotated key.

There is no per-install revoke and no per-install purge: with no install identifier in the
wire format, there is nothing to target one attacker's contribution by. Rotating the key is
the whole recovery path — bad data already ingested ages out with the rest of the project's
counters (see the k-anonymity purge policy) rather than being individually removable.

---

## What this model deliberately does NOT do

- It does **not** attempt to keep the key secret in client code. That is impossible on
  F-Droid and dishonest to claim.
- It does **not** rely on app attestation / Play Integrity. Incompatible with F-Droid.
- It does **not** prevent determined attackers from hitting the configured rate limit. It
  bounds the blast radius (per-project, per-IP, per-metric-cardinality) and makes it cheap to
  reject structurally invalid input, but it cannot distinguish a fabricated counter delta
  from a real one the way an event-name allowlist once could — there is no free-form event
  name left to allowlist against.
