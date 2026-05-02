# Operations: Rate Limits

## Default Limits (Self-hosted)

By default, rate limiting is disabled in self-hosted installations. You can enable it via environment variables:

**Ktor** (`application.conf`):
```
quietmetrix.rateLimit.enabled = true
quietmetrix.rateLimit.requestsPerSecond = 100
quietmetrix.rateLimit.burstPerMinute = 600
```

**PHP** (`.env`):
```
QM_RATE_LIMIT_ENABLED=true
QM_RATE_LIMIT_RPS=100
QM_RATE_LIMIT_BURST=600
```

## Cloud Plan Limits

| Plan | Requests/sec | Burst/min | Events/month |
|------|-------------|-----------|-------------|
| Free | 10 | 60 | 10,000 |
| Hobby | 50 | 300 | 100,000 |
| Startup | 200 | 1,200 | 1,000,000 |
| Business | 1,000 | 6,000 | 10,000,000 |

When a rate limit is exceeded, the server returns `429 Too Many Requests` with a `Retry-After` header indicating when the client should retry.

## Rate Limit Headers

All responses include:

- `X-RateLimit-Limit` — Maximum requests in the current window
- `X-RateLimit-Remaining` — Remaining requests
- `X-RateLimit-Reset` — Seconds until window resets