# Operations: request budgets and rate limits

Both backends bound aggregate bodies to 1 MiB and envelopes to 512 items. Hosted
startup requires rate limiting. Login uses shared database limits of 20 attempts per
source IP and five per account in a minute. Public API traffic has a shared IP budget
of at least 120 requests per minute in Ktor, and 120 in PHP. Only explicitly trusted
proxy addresses can supply the forwarded source IP.

Ktor ingestion additionally uses bounded in-process token buckets. Configure
`quietmetrix.rateLimit.enabled`, `requestsPerSecond`, and `burstPerMinute` in
`application.conf`; configure `quietmetrix.ipRateLimit` there for source-IP limits.
PHP uses shared database minute windows and reads `RATE_LIMIT_ENABLED`,
`RATE_LIMIT_RPS`, `RATE_LIMIT_BURST`, `INGEST_IP_ENABLED`, `INGEST_IP_RPS`, and
`INGEST_IP_BURST` from its protected configuration file. The PHP minute budget is
`min(burst, rps * 60)`; APCu is not required. Neither backend receives an install ID
for aggregate ingestion, so there is no per-install server limiter.

Cloud also defaults to 10,000 physical ingestion requests per project per UTC day.
Set `QM_COUNTER_REQUESTS_PER_DAY` to a positive integer to override it. Retries consume
this capacity budget even if receipts prevent duplicate analytics. Self-host defaults
to no daily budget. These limits are capacity controls, not monthly event billing.

A rejected request returns HTTP 429 and `Retry-After`. Shared minute limits use 60
seconds; the daily budget uses 86,400 seconds. Do not assume `X-RateLimit-*` response
headers exist. SDK outboxes retain rejected delivery for later retry.

See [public hosting](public-hosting.md) for project quotas, plan retention, deployment
settings, and the production reverse proxy.
