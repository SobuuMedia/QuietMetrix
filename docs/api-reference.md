# API Reference

The QuietMetrix API is defined by an OpenAPI 3.1 specification at [`openapi.yaml`](openapi.yaml).

Both the PHP+MySQL and Ktor+Postgres backends implement this contract identically.

## Authentication

### API key (Tracking)

Tracking endpoints use the `X-QM-Api-Key` header. The API key is **publishable**: write-only and project-scoped, safe to embed in client code (browser bundles, mobile apps, F-Droid builds). It cannot read analytics or access admin routes. Rotate it via `POST /api/v1/projects/{id}/regenerate-key` (Bearer auth) when a published key is abused. See [Threat model & abuse defense](security/publishable-api-key.md).

```
X-QM-Api-Key: qm_ak_abc123def456ghi789
```

### Session Token (Admin/Dashboard)

Admin endpoints use Bearer authentication obtained via login:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### Personal Access Token (Agents/CLIs)

`POST` and `GET /api/v1/projects` also accept a long-lived personal access token (`qm_pat_…`),
sent the same way as a session token — `Authorization: Bearer <token>`. Unlike the session
token, it's scoped (`projects:create`, `projects:read`) and revocable, and is meant to live in
an agent's or CLI's environment rather than a browser session. Minted via `POST /api/v1/tokens`
(session auth only — a token can never mint another token). See
[Agent-driven setup](agents/setup.md).

```
Authorization: Bearer qm_pat_9f3c1a2b4e5d6f708192a3b4c5d6e7f8
```

## Endpoints

### Health Check

`GET /api/v1/health` — No auth required.

### Submit Counters

`POST /api/v1/counters` — API key auth. The device computes `(metric, dims) -> n` deltas
itself and sends only those; there is no raw event or install identifier on this route. See
[`openapi.yaml`](openapi.yaml) for the full request/response shape.

```json
{
  "sdk": {"platform": "android", "version": "0.5.0"},
  "app": {"version": "2.4.0"},
  "day": "2026-09-04",
  "counters": [
    {"m": "screen_transition", "d": {"from": "Library", "to": "BookDetail"}, "n": 3, "u": 1}
  ]
}
```

Response: `202 Accepted` with `{ "ok": true, "accepted": 1, "quarantined": 0 }`

### Admin Login

`POST /api/v1/auth/login` — Email + password → session token.

### Projects CRUD

- `GET /api/v1/projects` — List projects (supports `?owner_only=true`). Session or PAT auth.
  Project rows include ingest-derived `activity_status` (`receiving_data`, `no_recent_data`, or
  `waiting_for_first_data`) and `last_received_at`. `platforms` and `sdk_versions` are present only
  when a project-level installation window has at least five contributors; empty lists mean the
  metadata is unavailable or below that threshold, not that the project is configured for none.
- `POST /api/v1/projects` — Create project (returns API key). Session (admin) or PAT auth.
  Accepts an optional `Idempotency-Key` header — a retried create with the same key returns
  the existing project (`200`) instead of minting a duplicate.
- `GET /api/v1/projects/:id` — Get project details
- `PATCH /api/v1/projects/:id` — Update project name (owner/admin only)
- `DELETE /api/v1/projects/:id` — Delete project (owner only)

### Access Tokens (Agents/CLIs)

Session-authenticated only — see [Agent-driven setup](agents/setup.md).

- `POST /api/v1/tokens` — Mint a `qm_pat_…` token. `{ name, scopes?, expires_in_days? }`.
  Requesting `projects:create` requires an admin session. Returns the plaintext token once.
- `GET /api/v1/tokens` — List the caller's non-revoked tokens. Never includes plaintext.
- `DELETE /api/v1/tokens/:id` — Revoke a token.

### Project Members

- `GET /api/v1/projects/:id/members` — List project members
- `POST /api/v1/projects/:id/members` — Add a member (owner/admin only)
- `DELETE /api/v1/projects/:id/members/:userId` — Remove a member (owner/admin only)

Roles: `owner` (full control), `admin` (manage members and settings), `viewer` (read-only dashboard access)

### Project Update & Delete

- `PATCH /api/v1/projects/:id` — Update project name. Requires owner or admin role. Returns the updated `ProjectResponse`.
- `DELETE /api/v1/projects/:id` — Delete project and all associated data. Only the project owner can delete. Returns `{ deleted: true, project_id: "proj_..." }`.

### Plans

| Hosted account plan | Retention | Organization projects |
| --- | --- | --- |
| Free | 30 days | 1 |
| Hobby | 90 days | 3 |
| Startup | 365 days | 10 |
| Business | 730 days | 50 |

Plans are operator-managed; a public billing integration is not supplied. The hosted
physical ingestion budget defaults to 10,000 requests per project per UTC day and counts
retries. Configured request-rate controls apply independently of plan. Self-hosted project
counts and analytics retention remain unlimited unless configured. See
[public hosting](operations/public-hosting.md).

### Dashboard

- `GET /api/v1/projects/:id/aggregates` — Aggregated metrics
- `GET /api/v1/projects/:id/transitions` — Screen-to-screen navigation flow counts
- `GET /api/v1/projects/:id/sessions` — Session counts and average duration
- `GET /api/v1/projects/:id/retention` — Cohort retention (day 1/3/7/14/30)
- `GET /api/v2/projects/:id/report?range=today|7d|30d|90d&anchor=YYYY-MM-DD&compare=true|false&platform=all|android|ios|web|desktop` — Authenticated typed report contract. Current implementation reports fixed-window v2 People with explicit state/source and coverage, plus `charts.people`: elapsed UTC-hour bins for the current Today, all 24 hours for a historical single day, daily bins for 7d/30d, and exact seven-day blocks (with a shorter final block) for 90d. With comparison enabled, `comparison_charts.people` returns the equal-length preceding bins; current-day hourly comparison stops at the same elapsed UTC hour. Chart-bin People values are distinct within each bin and are never summed into the range KPI. Adjacent full-window People KPI comparison is available for 7d/30d/90d. Today KPI comparison and other v2 report sections remain unsupported until their privacy witnesses and query projections are implemented.

### SDK counter protocol

- `GET /api/v1/sdk/capabilities` — API key auth (`X-QM-Api-Key`). Returns supported server capabilities and minimum SDK versions. SDKs cache the response for 24 hours; a missing route means legacy server compatibility mode.
- `POST /api/v2/counters` — API key auth. Additive hourly/daily aggregate ingestion with idempotent batch receipts. The current v2 wire DTO uses `batch_id`, `day`, nullable UTC `hour`, `sdk`, `app`, and `counters`; it is not yet the complete segment-based envelope specified by the dashboard fidelity plan.

### Funnels

See the [Funnels developer guide](sdk/funnels.md) for concepts, matching rules, and a worked
example of the results payload.

- `GET /api/v1/projects/:id/funnels` — Bearer auth. List active funnels for the project.
- `POST /api/v1/projects/:id/funnels` — Bearer auth, admin/developer role. Create a funnel.
- `PATCH /api/v1/projects/:id/funnels/:funnelKey` — Bearer auth, admin/developer role. Update a funnel; locks it against further SDK auto-registration.
- `DELETE /api/v1/projects/:id/funnels/:funnelKey` — Bearer auth, admin/developer role. Archive a funnel; unlocks the key for SDK re-registration.
- `GET /api/v1/projects/:id/funnels/:funnelKey/results` — Bearer auth. Query params: `range` (seconds, default 7 days), `breakdown` (`country` \| `platform` \| `device_class` \| `language`), `trend` (`1` to include a daily trend series).
- `POST /api/v1/funnels/register` — API key auth (`X-QM-Api-Key`). Upserts funnel definitions declared by the SDK; a no-op for any funnel already locked by a dashboard edit.

A step's `props` are string→string exact-match filters compared against the matching event's own props (stringified), not the freeform, mixed-type `props` an event itself carries — an empty filter is always `{}` on the wire, never `[]`.

### Experiments (A/B testing)

See the [Experiments developer guide](sdk/experiments.md) for concepts, assignment timing, and
country targeting. Unlike funnels, an experiment's definition lives entirely on the server —
app code only asks `getVariant`/reports `trackExperimentInteraction`.

- `GET /api/v1/projects/:id/experiments` — Bearer auth (session, or PAT with `analytics:read`). List the project's experiments, newest first. Response includes `limit` — the max non-ended experiments the project's plan allows, or `null` if unlimited.
- `POST /api/v1/projects/:id/experiments` — Bearer auth, admin/developer role. Creates a `draft` experiment. `403 experiment_limit_reached` if the plan's limit is already met (cloud/free plan only — self-hosted and paid-plan projects are never capped).
- `PATCH /api/v1/projects/:id/experiments/:experimentKey` — Bearer auth, admin/developer role. Partial update; also used for status transitions (`draft→active→paused→active→ended`). `variants`/`countries` may only change while `draft`; nothing changes once `ended`.
- `DELETE /api/v1/projects/:id/experiments/:experimentKey` — Bearer auth, admin/developer role. Only a `draft` experiment may be deleted; end a live one instead.
- `GET /api/v1/projects/:id/experiments/:experimentKey/results` — Bearer auth (session, or PAT with `analytics:read`). Query params: `range` (`1h`\|`1d`\|`7d`\|`30d`\|`90d`, default `7d`). Returns per-variant impressions/interactions/rate plus a two-proportion significance `comparison` (`null` until each variant has at least 30 impressions).
- `GET /api/v1/experiments/config` — API key auth (`X-QM-Api-Key`). SDK-facing: returns only `active` experiments, with only the fields needed to assign a variant on-device (key, traffic percentage, variants, countries) — never a name, screen label, or timestamp.

A device's variant assignment and its bucketing id never reach the server — only aggregate
`experiment{exp, variant, action}` impression/interaction counters do, subject to the same
k-anonymity gate as every other counter.

## Error Responses

All errors follow `{ "error": "code", "message": "description" }`.

| Status | Error code | Meaning |
|--------|-----------|---------|
| 400 | `invalid_json` / `schema_violation` | Bad request |
| 401 | `unauthorized` | Missing or invalid auth |
| 413 | `payload_too_large` / `batch_too_large` | Request too large |
| 429 | `rate_limit_exceeded` | Rate limit hit |
| 503 | `service_unavailable` | Server under backpressure |
