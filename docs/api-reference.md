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

## Endpoints

### Health Check

`GET /api/v1/health` — No auth required.

### Track Single Event

`POST /api/v1/track` — API key auth.

```json
{
  "event": "page_view",
  "screen": "home",
  "props": {"language": "en"},
  "sid": "abc123",
  "ts": "2026-04-30T12:34:56Z",
  "was_offline": false,
  "sdk": {"platform": "android", "version": "0.4.0"},
  "ctx": {"language": "en", "ua": "...", "viewport": "412x914"}
}
```

Response: `202 Accepted` with `{ "ok": true, "queued": 1 }`

### Track Batch

`POST /api/v1/track/batch` — Up to 100 events per request.

### Admin Login

`POST /api/v1/auth/login` — Email + password → session token.

### Projects CRUD

- `GET /api/v1/projects` — List projects (supports `?owner_only=true`)
- `POST /api/v1/projects` — Create project (returns API key)
- `GET /api/v1/projects/:id` — Get project details
- `PATCH /api/v1/projects/:id` — Update project name (owner/admin only)
- `DELETE /api/v1/projects/:id` — Delete project (owner only)

### Project Members

- `GET /api/v1/projects/:id/members` — List project members
- `POST /api/v1/projects/:id/members` — Add a member (owner/admin only)
- `DELETE /api/v1/projects/:id/members/:userId` — Remove a member (owner/admin only)

Roles: `owner` (full control), `admin` (manage members and settings), `viewer` (read-only dashboard access)

### Project Update & Delete

- `PATCH /api/v1/projects/:id` — Update project name. Requires owner or admin role. Returns the updated `ProjectResponse`.
- `DELETE /api/v1/projects/:id` — Delete project and all associated data. Only the project owner can delete. Returns `{ deleted: true, project_id: "proj_..." }`.

### Plans

| Plan | Events/Month | RPS | Retention | Max Projects |
|------|-------------|-----|-----------|-------------|
| Free | 10,000 | 10 | 30 days | 1 |
| Hobby | 100,000 | 50 | 90 days | 3 |
| Startup | 1,000,000 | 200 | 365 days | 10 |
| Business | 10,000,000 | 1,000 | 730 days | 50 |

### Dashboard

- `GET /api/v1/projects/:id/events` — Paginated raw events
- `GET /api/v1/projects/:id/aggregates` — Aggregated metrics

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

## Error Responses

All errors follow `{ "error": "code", "message": "description" }`.

| Status | Error code | Meaning |
|--------|-----------|---------|
| 400 | `invalid_json` / `schema_violation` | Bad request |
| 401 | `unauthorized` | Missing or invalid auth |
| 413 | `payload_too_large` / `batch_too_large` | Request too large |
| 429 | `rate_limit_exceeded` | Rate limit hit |
| 503 | `service_unavailable` | Server under backpressure |