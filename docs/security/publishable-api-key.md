# Publishable API Key — Threat Model & Abuse Defense

The QuietMetrix **API key** (header `X-QM-Api-Key`, format `qm_ak_…`) is a **publishable,
write-only, project-scoped** credential. It is *not a secret* and is expected to ship inside
client applications — including browser bundles, mobile apps, and **F-Droid** builds.

This document is the authoritative threat model for that key and the abuse-defense controls
the platform applies so the key can be shared publicly (including committed to a public
repository) without compromising data integrity or other projects.

---

## TL;DR

- The key can only **write** events to **one project**. It cannot read analytics, list or
  modify projects, regenerate itself, access other projects, or user accounts.
  Read/admin routes require a JWT obtained via login.
- Because it ships in client code, assume it is **public** the moment a build is published.
  Design around that — do not rely on its secrecy.
- Abuse is bounded by **per-project**, **per-IP**, and **per-install** throttling plus an
  opt-in **event-name/schema allowlist**, and **quarantine** of suspicious events.
- Use a **dedicated** project (separate from production) for any consumer that publishes the
  key (F-Droid, public demo repos, open-source apps). Treat its key as **burnable** and
  **rotate** it via `POST /api/v1/projects/{id}/regenerate-key` on abuse.

---

## Scope of the key — what publishing it grants

An attacker holding a published key can do exactly two things to that one project:

1. `POST /api/v1/track` — inject one event.
2. `POST /api/v1/track/batch` — inject up to 100 events per request, ≤1 MB body.

| Capability | Granted by API key? |
|---|---|
| Ingest events into the key's own project | Yes |
| Read aggregates / dashboard data | No (JWT) |
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
2. **Enable the event-name/schema allowlist** on that project (`strict_schema`) with the
   small set of events the SDK actually emits (`page_view`, `screen_view`, `click`,
   `consent_granted`, `consent_revoked`, `session_start`, …). Anything an attacker invents
   is rejected at the door.
3. **Rotate the key** via `POST /api/v1/projects/{id}/regenerate-key` (JWT-protected) when
   you suspect abuse, then ship a new build.
4. **Document it for F-Droid reviewers.** Add a short *Privacy & Abuse Defense* note in
   your app's listing referencing this doc, so the embedded `qm_ak_…` string is read as a
   publishable, write-only, project-scoped analytics key — not an undocumented secret.

Note that F-Droid builds cannot use Google Play Integrity / SafetyNet. The platform
therefore does **not** depend on app attestation. Trust is built from behavioural signals
server-side (per-IP, per-install, quarantine), not from proving which app made a request.

---

## Abuse-defense controls

### Per-project rate limiting

Existing control: a token bucket keyed `wm:$projectId` bounds total ingest volume per
project. Default ~60/min burst.

### Per-IP rate limiting (Stage 1)

A second token bucket keyed `wm:ip:<clientIp>` (using the trusted-proxy-validated
`clientIp()`) bounds ingest per source address. Default 5 rps / 60/min burst per IP. A
single-host attacker cannot saturate the project's shared bucket.

Env vars: `QM_INGEST_IP_RPS`, `QM_INGEST_IP_BURST` (Ktor);
`INGEST_IP_RPS`, `INGEST_IP_BURST` (PHP).

### Per-install throttling + ramp-up detection (Stage 2)

A third bucket keyed `wm:install:<projectId>:<anonymousIdHash>` plus a ramp-up detector
constrains brand-new installs (low initial burst that grows as the install proves itself).
Installs that exceed the project's p99 event volume × 10 are auto-flagged and routed to
quarantine. Per-install revoke lets you kill one attacker without affecting real users.

### Event-name / schema allowlist (Stage 3, opt-in)

When `strict_schema=true` on a project, events whose `name` is not in the allowlist are
rejected with `422 unknown_event` (422 for single, `schema_violation` for batch). Optional
per-event `properties` shape enforcement. Recommended for any consumer publishing the key.

### Quarantine (Stage 4)

Events that fail heuristic checks (ramp-up, schema-miss for non-strict projects) land in
`events_quarantine` instead of `events`. They are excluded from aggregates/dashboards by
default. An admin reviews them via `GET /api/v1/projects/{id}/quarantine` and can release
or discard them.

### Origin / User-Agent baseline (Stage 5)

For non-mobile traffic (when `Origin`/`Referer` is present), the project may enforce an
`allowed_origins` allowlist server-side independent of CORS — collapsing cross-origin abuse
for any web consumer reusing the same backend. For mobile (no `Origin`) minimal User-Agent
sanity is logged; rejection is configurable per profile.

### Audit log

Every ingest attempt is recorded with `apiKeyLast4 + validated clientIp + anonymousIdHash +
timestamp + disposition` so you can answer "when did this project start being polluted,
from where, with which install?".

---

## Privacy note

- The SDK sends a per-install `anonymousId` (the `${prefix}anonymous_id` store value) with
  every event. The server **salt-hashes** it before storage — it is never logged in the
  clear in `install_meta` or audit tables.
- The salt is **per-project** and **rotated on key rotation**, so an install-tracker cannot
  be rebuilt across rotations by correlating hashes.
- No new cross-install identifier is introduced. `anonymousId` already existed in the wire
  format; abuse-defense only hashes it for storage.

---

## Operational recovery

If a published key is abused:

1. `POST /api/v1/projects/{id}/regenerate-key` — old key stops working immediately.
2. (Optional) Purge events in the polluted time window for the offending install(s) via the
   dashboard Installs screen (Stage 6).
3. Release any false positives from quarantine via the Quarantine screen.
4. Ship a new app build with the rotated key (or keep the old key if the polluted window is
   small and quarantine absorbed it).

---

## What this model deliberately does NOT do

- It does **not** attempt to keep the key secret in client code. That is impossible on
  F-Droid and dishonest to claim.
- It does **not** rely on app attestation / Play Integrity. Incompatible with F-Droid.
- It does **not** prevent determined attackers from hitting the configured rate limit.
  It bounds the blast radius, makes polluted data reviewable/removable, and makes
  distinguishing real from fake events tractable via the schema allowlist.
- Per-install enrollment (Ed25519 signing) is held in reserve as an optional upgrade if
  abuse materializes beyond what the above absorbs; it is intentionally not built yet to
  avoid an SDK contract change before it is justified.