# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] - 2026-09-17

### Added
- **Value metrics** — `trackValue(name, amountMinorUnits)`, a new SDK function alongside
  `trackEvent`/`trackScreen` for reporting an amount (e.g. revenue) rather than just an
  occurrence count. Records a `value{name}` counter whose `n` sums the amounts themselves (in
  integer minor units — cents for USD — to avoid float drift); negative amounts are accepted
  for refunds/adjustments. Both backends gained `ValueCounterAnalyzer`/`valueCounterAnalyze()`,
  and `/aggregates` gained a `top_values` field, shown as a new "Top values" card on Overview.
- **Activation metric** — apps can now declare `QuietMetrixConfig.activationEvent` (+
  `activationWindowDays`, default 3): the first time that event fires within the window of
  first launch, the device reports `activation{cohort}` once, ever. Unlike retention's day-N
  marks, this is a hard window — firing late never counts. Extracted `ensureFirstLaunchDay()`
  out of `RetentionReporter` into a shared `FirstLaunch.kt` so activation and retention cohorts
  can never disagree about a device's first-launch date. Both backends gained
  `ActivationCounterAnalyzer`/`activationCounterAnalyze()`, joining activation cells against
  retention's existing `day="0"` cohort-size signal; `/retention` gained an `activation_rate`
  per cohort (`null`, not `0.0`, when the project's app hasn't configured an activation event —
  the server can't see the SDK's config, only whether any `activation` cells exist at all). The
  Retention tab gained an Activation column with a Wilson interval, reusing the existing
  per-day-column helper unchanged.
- **Zero-result search** — `trackSearch(screen, resultCount)` reports whether a search
  returned nothing, never the query text itself (free-form text is often sensitive and would
  blow up counter cardinality). Records `search{screen}` and, only when `resultCount == 0`,
  `search_zero_result{screen}`. Both backends gained a new `/projects/{id}/search` endpoint and
  `SearchCounterAnalyzer`/`searchCounterAnalyze()` computing a per-screen zero-result rate; the
  Flow tab gained a "Zero-result searches" table with a Wilson interval, sorted worst-first.
- **Friction scores (rage-tap detection)** — a new `RageTapDetector` (pure logic, thoroughly
  unit-tested: 3+ taps within 1.5s, each within a small radius of the previous one) backs a new
  `friction{screen, kind}` counter (`kind` fixed to `"rage_tap"` for now). **Automatic on
  Android** — a `Window.Callback` wrapper is installed from the same `ActivityLifecycleCallbacks`
  hook already used for screen-dwell tracking, with zero app code required. **Not automatic on
  iOS** — a new `QuietMetrixWindow` (`UIWindow` subclass) requires a one-line integration change
  in `SceneDelegate`/`AppDelegate` (see `docs/sdk/ios.md`); there is no safe way to intercept
  touches on iOS without either this opt-in subclass or fragile Objective-C method swizzling,
  which this SDK deliberately does not do. **No signal at all from JVM/Linux/Windows/Web** — an
  honest platform gap, not a stub. Both backends gained `FrictionCounterAnalyzer`/
  `frictionCounterAnalyze()` and a new `/projects/{id}/friction` endpoint; the Flow tab gained a
  "Rage taps" table. The Android `Window.Callback` wrapper and iOS `QuietMetrixWindow` are
  compile-verified only — this development environment has no device/simulator to confirm
  actual touch-dispatch behavior at runtime.
- **Debug overlay** — a new, separate, optional Gradle module, `quietmetrix-sdk-debug`,
  shipping `QuietMetrixDebugOverlay()`: a small Compose Multiplatform panel showing the counter
  pipeline's most recent flush (every cell sent, and whether it succeeded). Kept out of the
  core `quietmetrix-sdk` artifact so consumers who never use it pay nothing for a Compose
  dependency in an otherwise UI-free library. Available on **Android, iOS (arm64/simulator —
  not the discontinued `iosX64` Intel simulator, which Compose Multiplatform 1.11.1 doesn't
  publish for), JVM (desktop), and Web (wasmJs) only** — Compose Multiplatform has no rendering
  backend for bare Kotlin/Native `linuxX64`/`macosArm64`/`mingwX64`, a hard platform constraint.
  The core SDK gained a small new public surface, `QuietMetrixDebug` (`pending`/`lastFlush`
  `Flow`s plus one-shot `current*()` snapshots), backed internally by `DebugState` — updated by
  `CounterFlusher` only when `QuietMetrixConfig.debug == true`, so a production build never
  touches it, not even to publish an unread value. No visual/screenshot verification is
  possible from this development environment; the module is compile-verified on all four
  targets only.

  Explicitly out of scope for this pass (each is its own multi-session undertaking, and two
  need real external developer-account credentials this environment doesn't have): an
  experiment engine, acquisition connectors (Install Referrer, AdAttributionKit, Play
  Console/App Store Connect), and a compliance-doc generator. Flipping the SDK's consent
  default is also deliberately not done here — it's a compliance-relevant policy change that
  needs its own explicit go-ahead, not a bundled decision.

### Breaking
- **Removed `/track` and `/track/batch` — the raw event-stream ingest path — on both
  backends. Deleted the SDK's `EventQueue`/`HttpTransport`/`FlushManager`/`ConnectivityMonitor`
  and their wire DTOs.** This finishes the migration to aggregate-only ingest that M0–M4
  started: every dashboard read has been served from `counters` since M4, and nothing has
  called the old event-wire code since `trackEvent`/`trackScreen` were rewired in M2. Removing
  it now deletes real capability that will not come back:
  - **No raw per-event or per-session data of any kind, ever again.** `GET
    /projects/{id}/events` is gone (both backends) — there is nothing left to page through.
    An install running an SDK older than this release will get a 404 from `/track`; there is
    no server-side compatibility shim.
  - **`QuietMetrixConfig.maxQueueSize` is removed** — the field configured the now-deleted
    event queue's max size and has no replacement; the counter pipeline has no equivalent
    bound (see the existing "pending counters are in-memory only" limitation).
  - **One-way, destructive database migration.** Ktor's `V19__drop_events.sql` drops
    `events`, `events_inbox`, `events_quarantine`, `ingest_audit`, `install_meta`, and
    `sessions`, plus `projects.install_salt`/`analytics_salt`/`strict_schema`/`allowed_events`.
    PHP's `bootstrap.php` does the equivalent on next request (guarded, idempotent — see its
    new step 1d). **Any historical raw event or session row is deleted, not archived**, the
    first time either backend runs this version. Back up first if that data matters to you.
  - **The event-name allowlist (`strict_schema`/`allowed_events`) and per-install abuse
    defense (install-hash throttling, ramp-up detection, quarantine, audit log) are gone.**
    There is no per-install identifier left in the wire format to throttle, hash, or quarantine
    by — see the rewritten threat model at `docs/security/publishable-api-key.md`. Abuse
    defense is now per-project/per-IP rate limiting plus the counter dimension registry's
    fixed metric/dims/cardinality bounds, which is a smaller set of guarantees than before.
  - **`quietmetrix-cli`'s `analytics events` command and `quietmetrix-mcp`'s
    `quietmetrix_get_events` tool are removed**, since the endpoint they called no longer
    exists.
- **`/sessions` is real again**, on both backends — a genuine new counter producer, not just
  cleanup. The SDK's new `internal/counters/SessionTracker` times one app session from
  `QuietMetrix.init()` to `QuietMetrix.stop()` and reports it as a `session{bucket}` counter
  (bucketed by `SessionBucket.kt`/`sessionBucket()`, mirroring `screen_dwell`'s approach);
  both backends' `/sessions` handler now reads `session{bucket}` cells via a new
  `SessionCounterAnalyzer`/`sessionCounterAnalyze()` (bucket-midpoint-weighted
  `avg_duration_sec`, exact `total_sessions`). This replaces raw-`events`-table session
  grouping, which had already gone silently and permanently dead in this codebase — nothing
  called `EventRepository.startSession`/`endSession` anywhere, so `/sessions` had been
  returning all-zero data even before this release. **`avg_events` is dropped from the
  response entirely** (both backends, and the dashboard's `kpi_events_per_session` KPI card):
  no counter ties an event count to a session under aggregate-only ingest, so it would be a
  permanently-fake number rather than an honest gap.

### Added
- **A Retention destination in the dashboard**, reading the `/retention` endpoint that already
  existed (via `ApiClient.retention()`, the CLI, and the MCP tool) but had no UI rendering it.
  Each cohort's day-N figures show alongside a Wilson score confidence interval
  (`format/Wilson.kt`) — the honest-error-bars default this dashboard now applies everywhere a
  rate is shown, since a "40%" from 5 devices and a "40%" from 500 are not the same claim.
  Funnels' overall-conversion KPI and per-step conversion-from-entry column got the same
  treatment. Fixed a bug surfaced while wiring this up: the per-step "conversion from previous"
  column used to key off `medianMsFromPrevious == null` to hide the entry step's meaningless
  0% — harmless before, but now that field is *always* null under aggregate-only ingest, so it
  would have hidden every step's real conversion number. Now keyed off the step's own identity.

### Changed
- **The Funnels dashboard is now read-only.** Funnels are declared entirely in app code (the
  SDK's `FunnelManifest`) and evaluated on-device (see the earlier on-device-evaluation entry
  below) — the dashboard's create/edit UI let an analyst edit steps that the SDK would then be
  blocked from updating (the old `locked` flag), which no longer matches an architecture where
  the SDK is the sole source of truth. Removed `FunnelEditorDialog` and the "New funnel"/"Edit"
  actions; deleting a funnel definition is still possible. Also removed the breakdown and trend
  cards and the median-time KPI/column — all three depended on server-side data
  (`breakdown`/`trend`/`medianTotalMs`/`medianMsFromPrevious`) that is always null now, so
  showing them would have been a permanently-empty feature rather than an honest limitation.
- **Removed the Events and Live dashboard destinations** (`EventsTab`, `LiveTab`,
  `EventsLogic.kt`) — both showed the raw per-event stream, which `trackEvent`/`trackScreen`
  no longer produce. The underlying `/events` endpoint, `ApiClient.events()`, and `EventRow`
  are untouched (still real, self-hostable capability for anyone who wants to inspect it via
  the API directly) — only the two UI tabs that displayed it are gone.
- **`/transitions`, `/retention`, and `/aggregates` now read from counters instead of the raw
  `events`/`sessions` tables, on both backends.** `/transitions` and `/retention` map cleanly
  (screen_transition and retention{cohort,day} counters already carry exactly what these
  endpoints need — `RetentionCounterAnalyzer`/`retentionCounterAnalyze()` compute day-N
  percentages from the new day="0" cohort-size signal, itself a fix: the SDK's
  `RetentionReporter` originally had no way to report a cohort's size at all, and separately
  used exact-day-N matching, which would have systematically undercounted retention — both
  caught and fixed before shipping, now "at least N days later," matching every other
  retention tool's semantics). `/aggregates` ships what maps cleanly (top_events,
  screen_durations as bucket-midpoint approximations, a platform breakdown, and new
  day-preserving daily totals via `CounterRepository.dailyTotals`/`totalsByPlatform`) and
  top_screens is approximated from `screen_transition{to}` (no standalone "screen viewed"
  counter exists); `countries`/`device_classes` are empty rather than silently faked, since
  those dimensions aren't tracked yet. Also fixes a pre-existing contract bug: the Ktor
  `/aggregates` response sent the daily trend under `dau`/`event_counts` while the dashboard
  (and PHP) expected `daily` — the trend chart was silently empty on Ktor before this.
  Deleted as dead code: `EventRepository.findTransitions/findRetention/findTopEvents/
  findTopScreens/findScreenDurations/findDailyTotals/find*Breakdown/findOfflineStats/
  countErrorsByProjectIdAndRange` and PHP's raw-SQL equivalents.
- **Funnels are now evaluated on-device, and `/funnels/{key}/results` reads from counters
  instead of the raw event stream.** The SDK's new `internal/funnels/FunnelEvaluator` tracks
  each configured funnel's progress in real time as `trackEvent`/`trackScreen` fire, and
  reports only the depth reached as a `funnel_step{f, rev, step}` counter — the underlying
  event trail never leaves the device. `FunnelCountMode.ACTOR` persists one lifetime progress
  per funnel (so a multi-day `windowSeconds` genuinely survives an app restart, via the
  existing per-platform `PersistentStore`); `FunnelCountMode.ATTEMPT` tracks concurrent
  attempts keyed by `correlationProperty`, in-memory only for now (an app killed mid-attempt
  loses that attempt's progress — a known limitation, in the same spirit as the counter
  pipeline's own lack of persistence). Both backends' `/results` endpoint now aggregates these
  cells via `FunnelCounterAnalyzer`/`funnelCounterAnalyze()` (kept in sync, tests included) —
  step counts, conversion, and drop-off are unchanged in shape, but **per-step timing
  (`median_ms_from_previous`, `p90_ms_from_previous`, `median_total_ms`), the `breakdown`
  dimension, and the `trend` are now always null**: that data no longer exists server-side
  under aggregate-only ingest. A day-bucketed trend is a plausible future addition (the
  `counters` table's `day` column already exists for it); the others are gone for good.
  Also removed as dead code (no longer reachable from any route): `FunnelAnalyzer.kt`,
  `FunnelMatcher.kt`, `ActorKey.kt`, `Percentile.kt`, `EventRepository.findFunnelEvents`, and
  their PHP twins `funnelAnalyze.php`, `funnelMatch.php`, `funnelActor.php`.
- **SDK: `trackEvent` and `trackScreen` now record aggregate-only counters instead of enqueuing
  raw events.** The public signatures are unchanged and every existing call site keeps
  compiling, but `trackEvent(event, screen, props)` now records an `event{name}` counter and
  drops `screen`/`props` entirely (logged in `debug` mode so a non-empty `props` doesn't go
  missing silently); `trackScreen` additionally records `screen_transition{from,to}` and
  `screen_dwell{screen,bucket}` counters via the new on-device `MetricGateway` /
  `MetricRecorder` pipeline (`internal/counters/`, one implementation shared across all 8
  targets). `Funnel.step()` inherits this — its declared `screen`/`props` are no longer sent.
  `CounterFlusher` periodically drains the recorder and posts to the new `POST /api/v1/counters`
  endpoint via the existing `platformPost` primitive (no new per-platform transport). `/track`,
  `EventQueue`, and `FlushManager` are unaffected and keep running — this only changes what
  `trackEvent`/`trackScreen` themselves feed into the pipeline; deleting the raw-event path is
  a later migration step, once the dashboard reads from counters.
  Known limitation of this milestone: pending counters are in-memory only (not persisted
  across a process restart, unlike the event queue), so an app killed between flushes loses
  unsent counters rather than replaying them on next launch.

### Added
- `POST /api/v1/counters` on both the Ktor and PHP backends: the first step of QuietMetrix's
  migration to aggregate-only ingest. The device computes `(metric, dims) -> n` deltas itself
  (screen transitions, funnel steps, retention buckets, ...) and sends only those — no install
  identifier is accepted on this route. Each item is validated against a fixed per-metric
  dimension registry (`CounterRegistry.kt` / `counterRegistry.php`, kept bit-for-bit in sync —
  see their shared canonicalization vectors) and, on success, upserted into a new `counters`
  table keyed by `(project, day, metric, platform, app_version, country, dims_hash)`. Every
  read path enforces a k-anonymity threshold: a cell is invisible until enough distinct
  devices have contributed to it, tracked via a running `devices` count with no identifier
  ever stored. Invalid or over-cardinality items are quarantined into `counters_quarantine`
  rather than failing the batch. `/track` is unaffected and remains the ingest path until the
  dashboard reads from counters (a later migration step).
- Personal access tokens (`qm_pat_…`) so an agent or CLI can create and list projects
  without a user's password. Minted via `POST /api/v1/tokens` (session-authenticated,
  admin-only for the `projects:create` scope); manage them at Dashboard → Access tokens.
- A new `analytics:read` personal-access-token scope, so an agent holding one can read
  (never write) event/funnel analytics: `GET /aggregates`, `/events`, `/transitions`,
  `/sessions`, `/retention`, `/funnels`, and `/funnels/{key}/results`, on both the Ktor and
  PHP backends. Pick it in the Dashboard's "New token" dialog alongside the existing
  project scopes.
- `quietmetrix-cli` gained `analytics aggregates|events|transitions|sessions|retention` and
  `funnel list|results` commands, so an agent can answer questions like "how many times did
  the onboarding funnel complete this week?" from the command line.
- `quietmetrix-mcp` gained matching tools (`quietmetrix_get_aggregates`,
  `quietmetrix_get_events`, `quietmetrix_get_transitions`, `quietmetrix_get_sessions`,
  `quietmetrix_get_retention`, `quietmetrix_list_funnels`, `quietmetrix_get_funnel_results`)
  for MCP-aware agents.
- `@sobuumedia/quietmetrix-cli` — provisions projects from the command line
  (`quietmetrix project create`), for agents and scripts.
- `@sobuumedia/quietmetrix-mcp` — an MCP server exposing project creation/listing as tools,
  for MCP-aware agents.
- `Idempotency-Key` support on `POST /api/v1/projects` — a retried create returns the
  existing project instead of minting a duplicate.
- `GET /api/v1/_meta` on the Ktor backend, matching the PHP backend's existing endpoint.
- [Agent-driven setup](docs/agents/setup.md) documentation and a copy-pasteable Claude Code
  skill.

### Fixed
- `/transitions`, `/sessions`, and `/retention` on the Ktor dashboard API were unreachable
  even with a valid session — a stray closing brace excluded them from the `authenticate`
  block, so every request silently fell through unauthenticated and 404'd. Fixed as part of
  switching these routes to the shared JWT-or-PAT principal resolver.
- Restored the Ktor server's ability to compile at all: a prior dependency bump moved
  Exposed to its breaking 1.x release (new `org.jetbrains.exposed.v1.*` package layout)
  without updating any of the ~30 files using the old `org.jetbrains.exposed.sql` API.

## [0.4.0] - 2026-08-12

### Added
- Declarative funnel registration from app code across the supported Kotlin
  Multiplatform targets.
- Stable anonymous-install identity support for funnel analysis.
- Native HTTP transport support for funnel registration on Android, Apple,
  JVM, Linux, Windows, JavaScript, and Wasm targets.

### Changed
- Improved SDK queue restoration and batch transport behavior.
- Updated the published SDK coordinates and documentation for the 0.4 series.

## [0.2.0] - 2026-07-11

### Added
- One-command self-hosting via root `docker-compose.yml` (PostgreSQL → Flyway
  migrations → Ktor serving the API and the dashboard on port 8080).
- `scripts/check-repo-hygiene.sh` and a CI job that fail if private
  infrastructure strings leak into tracked files.
- Community health files: `CONTRIBUTING.md`, `SECURITY.md`,
  `CODE_OF_CONDUCT.md`, issue/PR templates.
- SDK now builds for all documented platforms: Android, iOS, JVM/desktop,
  Web (Wasm), and native (linux/macOS/Windows).

### Changed
- **Renamed the SDK module `quietmetrix-core` → `quietmetrix-sdk`.** Maven
  artifacts follow the Kotlin Multiplatform convention: `quietmetrix-sdk`,
  `quietmetrix-sdk-android`, `quietmetrix-sdk-jvm`, `quietmetrix-sdk-iosarm64`,
  etc. Update your dependency coordinates accordingly.
- SDK version bumped to `0.2.0`.

### Removed
- Commercial/billing code (Stripe/Adyen providers, checkout/usage routes) and
  paid-plan quota tiers. QuietMetrix is now a fully self-hostable, unlimited
  analytics server. The `plan_id` column is retained but imposes no limits.
- Internal commercial-strategy and payments documentation.

## [0.1.5] - Initial

- Initial internal release: KMP SDK, Ktor + PHP backends, Compose-Wasm dashboard.
