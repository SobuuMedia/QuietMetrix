# Metric semantics and privacy

QuietMetrix records aggregate counters. The SDK does not send an installation ID, user ID,
email, device fingerprint, or per-user assignment record with counter batches. The legacy
`collectAnonymousId` configuration argument is retained for source compatibility, ignored, and
deprecated; initialization also removes its old locally stored value.

## People

“People” in the dashboard means distinct reported app installations active in the selected
fixed window. It does not identify distinct humans: one person using multiple devices or
reinstalling can count more than once. Clearing app storage can also make an installation count
again. Offline data older than the seven-day retry window may be lost, and contribution counts
are not cryptographic proof against a modified SDK.

The SDK keeps a local deduplication ledger and emits one anonymous contribution per project
window. Supported windows are 1–7, 30, and 90 days. Repeated activity within one window does not
increase that installation's contribution. The server displays an aggregate only after at least
five installation witnesses are present; smaller groups are hidden, not shown as zero. The
server stores aggregate cells (daily plus UTC-hour cells for the additive v2 protocol) and
idempotency receipts, never the local ledger or a mapping from a contribution to an installation.

Country is optional app configuration (`countryCode`), never inferred from IP, GPS, or language.
The server uppercases a two-letter value; absent or malformed input is stored as Unknown. Country
and platform breakdowns are separate projections and need not sum to the project total if an
installation changes its supplied country or platform.

## Explicit diagnostics

Call `reportCrash()` when the host application has a crash signal and `reportError()` for a
handled error worth counting. These functions record anonymous aggregate counts only. They do
not accept or transmit stack traces, exception messages, file paths, or identifiers; attach
diagnostic detail to your own privacy-reviewed logging system instead.

## Delivery and compatibility

Counter batches are persisted before send and keep a random batch ID and immutable body across
retries. New Ktor/PHP servers advertise ingestion, remote-funnel and experiment-config support
through API-key-authenticated `GET /api/v1/sdk/capabilities`; the public `GET /api/v1/_meta`
remains a dashboard/server-info endpoint. Capability responses are cached in memory for 24 hours.
New SDKs omit unsupported unique-window items and never send v2-only data to a v1 endpoint.
Legacy server fallback can therefore show legacy Sessions without a People value. Counter batches
expire locally after seven days and persistent queue state is capped at 8 MiB; if local
persistence fails, the SDK stops sending and exposes a local persistence-failure diagnostic.
Server retry receipts expire after 14 days, beyond the SDK's retry retention.
