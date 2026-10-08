# Plan: A/B Testing for Web & App (dashboard, both backends, SDKs)

> Superseded for new app-element experiments by [the dashboard fidelity plan](dashboard-fidelity-plan.md), section 10. That plan uses direct B visibility allocation, app-supplied country/language targeting, v2 config, and native Compose/SwiftUI wrappers. This document remains as historical design context for the legacy session-based experiment path; do not implement its proposed traffic gate or counter dimensions for v2.

## 1. Goal

Let a QuietMetrix customer run an experiment (e.g. "new onboarding flow" vs "old
onboarding flow"), have the SDK assign each device/visitor to a variant, have both
backends aggregate existing counters *segmented by variant*, and let the dashboard
show conversion/retention per variant plus whether the difference is statistically
significant.

This is built as an **additive extension of the existing aggregate-only counter
pipeline** (`CounterRegistry.kt` / `counterRegistry.php`), not a return to raw
per-session events. See `docs/Next/attribution-plan.md` for the separate,
independent install/visit-source plan — the two are designed to compose (an
experiment result can later be sliced by acquisition source) but neither blocks
the other.

## 2. Current-state constraints this plan must respect

- Every counter metric has an **exact, fixed dimension set**, enforced in
  `CounterRegistry.validate()`
  (`servers/ktor/src/main/kotlin/com/quietmetrix/server/counters/CounterRegistry.kt:56`):
  `dims.keys != spec.requiredDims` → rejected as `dims_mismatch`. Adding a new
  dimension to a metric is a **breaking schema change** for that metric — every
  SDK call site producing it must be updated in lockstep with the registry, on
  both backends.
- `php-hosting/src/counterRegistry.php` must mirror `CounterRegistry.kt` exactly
  (canonicalization + `metrics` map) — the file's own comment says so
  (`counterRegistry.php:4-8`). Both are touched in every task below.
- Counters are aggregate + k-anonymity gated (`MetricRecorder.kt:11-15`,
  `PendingCounter.isNewDevice`) — no per-device experiment assignment record may
  ever be transmitted or stored. Variant assignment must be deterministic and
  computed the same way independently by every device (no server round-trip
  needed for the MVP — see Task 2 open question for a v2 server-driven option).
- SDK is Kotlin Multiplatform (`quietmetrix-sdk/src/commonMain`, actuals per
  platform: android/ios/jvm/js). New experiment API must be added once in
  `commonMain` with platform actuals only where unavoidable (e.g. stable
  device/anon id already exists per platform — reuse it, don't invent a new one).
- Dashboard is Compose Multiplatform (`dashboard/src/commonMain`), currently wasmJs
  target per `Main.wasmJs.kt`. New screens follow existing patterns (see
  `dashboard jvmTest` i18n-parity + no-hardcoded-literal enforcement noted in
  memory — every new string needs a `Res.string.<key>` + its own import).

## 3. Design decisions (resolved up front, see rationale)

### 3.1 Where variant assignment happens: **client-side deterministic hashing (v1)**

`variant = hash(experimentKey + stableDeviceId) % numVariants`, computed entirely
in the SDK, no network call required to get a variant. The experiment's
*definition* (key, variant names, weights, active/inactive, start/end date) is
still fetched from the backend and cached, so the customer can pause/reweight an
experiment without an app release — but assignment math runs on-device.

Why not fully server-side (server decides variant per request)? It would need a
new stateful assignment endpoint + would require *not* being aggregate-only for
that call (the server would need to remember "device X got variant B" to keep it
sticky across sessions, which contradicts `k-anonymity`/no-install-id principle
already in force for the rest of the pipeline). Client-side hashing gives sticky,
reproducible assignment with zero server memory — consistent with the existing
architecture.

Why not fully offline (bundled experiment config, no fetch at all)? Loses the
"pause a bad experiment remotely" capability, which is the main practical reason
to want server-controlled experiments at all.

### 3.2 How variant is represented in counters: **new `variant` dimension on existing metrics**

Add `variant` (values: `"control"`, `"a"`, `"b"`, ... or the experiment's declared
variant names) to the dims of the metrics an experiment needs to segment:

| Metric | Current dims | New dims | Why |
|---|---|---|---|
| `funnel_step` | `f, rev, step` | `f, rev, step, variant` | primary conversion signal |
| `activation` | `cohort` | `cohort, variant` | "did more users activate" |
| `session` | `bucket` | `bucket, variant` | session count/engagement by variant |
| `retention` | `cohort, day` | `cohort, day, variant` | does the winning variant retain better |
| `event` | `name` | *unchanged* | generic events are cheap to leave un-segmented in v1; see Task 6 for opt-in extension |

`variant` defaults to the literal string `"none"` when a device is not part of
any active experiment, so the dimension is always present (registry requires
exact key sets) without breaking existing non-experiment counters — **this means
a full `dims_mismatch` migration is required for every metric row that adds
`variant`, see Task 3.**

### 3.3 Multiple concurrent experiments

v1 supports **one active experiment per "experiment slot"**, where a slot is just
a namespace (e.g. `"onboarding"`). `variant` dim value becomes `"<experimentKey>:<variantName>"`
(e.g. `"onboarding_v2:b"`) so two unrelated experiments don't collide in the same
counter cell, and `"none"` when no experiment is running in that slot. This keeps
the dimension a single string (registry values are `^[A-Za-z0-9_.:/-]{1,128}$`,
already allows `:`) rather than needing multiple new dims per metric.

## 4. Task breakdown (tests-first, gated, ordered by dependency)

Each task: write failing tests first, implement until green, all prior tasks'
tests still pass before moving on.

### Task 1 — Ktor: experiment domain model & repository (DB → server, no HTTP yet)

**Where:** `servers/ktor/src/main/kotlin/com/quietmetrix/server/persistence/`
(new `ExperimentRepository.kt`, extend `Tables.kt`), plus
`servers/ktor/src/main/kotlin/com/quietmetrix/server/domain/` for the data
classes.

**What:**
- New table `experiments`: `id, project_id, key (unique per project), name,
  variants (jsonb array of {name, weight}), status (draft|active|paused|ended),
  started_at, ended_at, created_at`.
- `ExperimentRepository`: `create`, `update` (name/status/variants),
  `findByProjectAndKey`, `listActiveForProject`.

**Tests first** (`servers/ktor/src/test/kotlin/.../persistence/ExperimentRepositoryTest.kt`):
- create + fetch round-trip.
- unique `(project_id, key)` constraint rejects duplicate key.
- `listActiveForProject` excludes `draft`/`ended`.
- variant weights validated to sum to 100 (or normalized) at write time — reject
  otherwise.

**Gate:** repository tests green, migration applies cleanly against a fresh test DB.

---

### Task 2 — Ktor: experiment config HTTP endpoints

**Where:** `servers/ktor/src/main/kotlin/com/quietmetrix/server/routes/`
(new `ExperimentRoutes.kt`), `domain/Requests.kt` for request/response DTOs.

**What:**
- `POST /projects/{id}/experiments` (dashboard-authenticated, creates in `draft`).
- `PATCH /projects/{id}/experiments/{key}` (update status/weights/name).
- `GET /projects/{id}/experiments` (dashboard list).
- `GET /sdk/experiments` (**SDK-facing, token-authenticated like other ingest
  endpoints**) — returns only `active` experiments' `{key, variants: [{name,
  weight}]}`, i.e. the minimal config the client needs to hash locally. No
  per-device state returned — stateless, cacheable, matches k-anonymity design.

**Tests first** (`ExperimentRoutesIntegrationTest.kt`, mirroring
`FunnelRoutesIntegrationTest.kt` / `AnalyticsTokenAuthIntegrationTest.kt`
patterns already in the repo):
- dashboard auth required for create/update/list; analytics-token auth (not
  dashboard) required for `/sdk/experiments`, and dashboard token is rejected
  there (mirrors existing token-scope tests).
- `/sdk/experiments` only returns `active` status, never `draft`/`ended`.
- weight validation surfaces as 400 with a clear error body.

**Gate:** integration tests green + Task 1 tests still green.

**Open question to confirm with product before Task 2 lands:** should experiment
config be push (webhook/long-poll) instead of pull-on-launch? v1 assumption is
pull-on-launch + short in-memory cache (see Task 5), reassessed only if customers
need sub-minute rollout of a pause.

---

### Task 3 — Registry: add `variant` dimension (Ktor + PHP, mirrored)

**Where:** `servers/ktor/src/main/kotlin/com/quietmetrix/server/counters/CounterRegistry.kt`,
`php-hosting/src/counterRegistry.php`, plus every analyzer that reads the
affected metrics (`ActivationCounterAnalyzer.kt`, `SessionCounterAnalyzer.kt`,
`RetentionCounterAnalyzer.kt`, the funnel step analyzer, and their PHP
counterparts: `activationCounterAnalyze.php`, `sessionCounterAnalyze.php`,
`retentionCounterAnalyze.php`, `funnelCounterAnalyze.php`).

**What:**
- Add `"variant"` to `requiredDims` for `funnel_step`, `activation`, `session`,
  `retention` in **both** registries — keep the shared canonicalization vectors
  test (`CounterRegistryTest.kt`) in sync per its own stated policy.
- Every analyzer's grouping key gains `variant` (defaulting to `"none"` bucket)
  so existing non-experiment dashboards keep working unchanged — they simply see
  a `variant="none"` row for all pre-experiment traffic.
- **Data migration:** existing rows in the `counters` table for these 4 metrics
  have a `dims_hash` computed *without* `variant`. Two options, pick one and
  document the choice in the migration file:
  1. Backfill: rewrite `dims_hash` for existing rows as if `variant="none"` had
     always been present (safe, preserves history, requires a one-off script
     recomputing the SHA-256 exactly like `CounterRegistry.canonicalize` does).
  2. Cutover: leave old rows as-is under the old hash scheme, start writing new
     rows only under the new scheme from a fixed date; dashboard queries treat
     pre-cutover history as `variant="none"` at the *query* layer instead of
     rewriting stored data.
  Recommend **option 2 (cutover)** — no risk of a backfill script silently
  corrupting historical counts, and it mirrors how `friction`/`search_zero_result`
  were introduced as net-new metrics rather than retrofitted.

**Tests first:**
- `CounterRegistryTest.kt` / `counterRegistryTest.php`: new vectors for
  `funnel_step`/`activation`/`session`/`retention` including `variant`; old
  4-key funnel_step dims (without `variant`) now rejected as `dims_mismatch` —
  assert the rejection explicitly so a future accidental revert is caught.
- Each analyzer test: two variants of the same cohort/bucket produce two
  separate result rows, not merged.

**Gate:** all registry + analyzer unit tests green, Tasks 1-2 tests still green.

---

### Task 4 — Ktor: experiment results endpoint (analysis + significance)

**Where:** new `servers/ktor/src/main/kotlin/com/quietmetrix/server/experiments/ExperimentAnalyzer.kt`,
route added to `ExperimentRoutes.kt` or a new `ExperimentResultsRoutes.kt`.

**What:**
- `GET /projects/{id}/experiments/{key}/results?metric=activation|funnel|retention&range=...`
  Joins the relevant per-variant counter cells (reusing `ActivationCounterAnalyzer`
  / funnel analyzer / `RetentionCounterAnalyzer` output, now variant-segmented
  from Task 3) into a comparison table: `{variant, n, converted, rate}[]`.
- Statistical significance: two-proportion z-test between each non-control
  variant and `control` (or the first declared variant if none is literally
  named "control"). Return `{zScore, pValue, significant: p < 0.05}` per
  comparison. This is pure arithmetic — no new dependency needed, implement
  directly (formula: `z = (p1-p2) / sqrt(p_pool*(1-p_pool)*(1/n1+1/n2))`).
- Respect existing k-anonymity gating: a variant cell below the existing
  minimum-device threshold is suppressed the same way other low-`n` cells
  already are (reuse whatever gate `CounterRepository.readCells` applies today
  — do not introduce a second threshold).

**Tests first** (`ExperimentAnalyzerTest.kt`):
- Known input → known z-score/p-value (use a textbook worked example as the
  fixture, e.g. 100/1000 vs 130/1000 conversions).
- A variant under the k-anonymity floor is omitted from results, not shown as a
  fake 0%.
- Ties (`control` not present) picks the lowest-weight-declared-order variant as
  baseline deterministically — document this rule in the doc comment per
  CLAUDE.md doc-comment rule.

**Gate:** analyzer tests green, Tasks 1-3 tests still green.

---

### Task 5 — SDK (commonMain): experiment config fetch, caching, and variant assignment

**Where:** `quietmetrix-sdk/src/commonMain/kotlin/com/quietmetrix/analytics/`
(new `experiments/ExperimentClient.kt`, `experiments/VariantAssigner.kt`), wired
from the existing config bootstrap (`ConfigHolder`-adjacent) and public API
(new `QuietMetrix.getVariant(experimentKey: String): String` alongside existing
`trackEvent`/`trackScreen`).

**What:**
- `VariantAssigner.assign(experimentKey: String, deviceId: String, variants:
  List<VariantSpec>): String` — pure function, deterministic hash (e.g.
  MurmurHash3 or a simple stable string hash already available in Kotlin
  stdlib/coroutines deps — avoid adding a new dependency for this) mod total
  weight, consistent across app restarts as long as `deviceId` and the
  experiment's variant list are unchanged.
- `ExperimentClient`: fetches `/sdk/experiments` on SDK init (same lifecycle
  point as other startup calls), caches in `PersistentStore` (existing
  interface, `PersistentStore.kt:3`) so a variant is available even fully
  offline after first launch, refreshes opportunistically (e.g. once per
  process start, not polled — matches "pull-on-launch" decision in Task 2).
- `getVariant(key)` returns cached assignment; if the experiment key is unknown
  (not yet fetched, or not active), returns `"none"` — callers must handle this
  gracefully, documented in the public API doc comment.
- Every `recordEventCounter`/funnel/activation/session/retention counter call
  site that now requires `variant` in dims (per Task 3) is updated to call
  `getVariant(activeExperimentSlotForThisMetric)` — needs a small mapping of
  "which experiment slot, if any, is currently active" since a device could
  technically be in zero or one experiment per slot; if in none, pass `"none"`.

**Tests first** (`quietmetrix-sdk/src/commonTest/kotlin/.../experiments/`):
- `VariantAssignerTest`: same `(experimentKey, deviceId, variants)` always
  produces the same variant (determinism); distribution across many synthetic
  device ids roughly matches declared weights (statistical tolerance test, not
  exact); different `deviceId` values spread across all variants (no
  degenerate always-same-bucket bug).
- `ExperimentClientTest`: cached config used when offline; unknown experiment
  key → `"none"`; inactive/ended experiment (per config) → `"none"` even if
  previously active in a stale cache.
- Updated `EventCounterBridgeTest`/`FunnelTest`/activation/session/retention
  bridge tests: assert `variant` dim is present and correct in the dims map
  passed to `MetricGateway.record`.

**Gate:** SDK commonTest green on `iosSimulatorArm64Test` (per memory: core has
no jvmTest, use iosSimulatorArm64Test) + Tasks 1-4 tests still green.

---

### Task 6 — SDK: platform actuals (Android, iOS, JS/Web) — public API surface only

**Where:** `quietmetrix-sdk/src/androidMain/.../Events.android.kt`,
`iosMain/.../Events.ios.kt`, `jsMain/.../Events.js.kt`, `jvmMain/.../Events.jvm.kt`.

**What:** Expose `QuietMetrix.getVariant(experimentKey)` through each platform's
existing public wrapper the same way `trackEvent` already is — this is thin
delegation to the `commonMain` implementation from Task 5, no new platform logic,
since `deviceId`/stable-id already exists per platform and `PersistentStore` has
actuals for all targets already.

**Tests first:** platform smoke tests already exist per-actual for `trackEvent`
equivalents (e.g. `Events.js.kt` covered by JS/node commonTest per the
`project_js_npm_sdk` memory's InMemoryStore fallback) — add the equivalent
one-line delegation test for `getVariant` on each platform test target that
currently exists. Do not add new platform test infrastructure beyond what
`trackEvent` already has.

**Gate:** per-platform tests green (android/ios/jvm/js as applicable), Tasks 1-5
tests still green.

---

### Task 7 — php-hosting: mirror the SDK-facing + analysis endpoints

**Where:** `php-hosting/src/routes/` (new experiment route file, following the
pattern of `funnelSteps.php`/existing route files), `php-hosting/src/` (new
`experimentAnalyze.php` mirroring `activationCounterAnalyze.php`'s structure).

**What:**
- `GET /sdk/experiments` and the dashboard CRUD endpoints from Task 2,
  reimplemented in flat-file PHP per the IONOS layout (per
  `reference_ionos_php_layout` memory — GhostPoll/CrashWatch flat-file pattern,
  not the removed Slim `servers/php/`).
- `experimentAnalyze.php`: same two-proportion z-test logic as
  `ExperimentAnalyzer.kt` from Task 4 — **port the formula exactly, add a shared
  test-vector file** (same pattern as `counterRegistryTest.php` mirroring the
  Kotlin registry tests) so both backends agree on a given input's p-value.

**Tests first** (`php-hosting/tests/experimentAnalyzeTest.php`,
`experimentRoutesTest.php` following existing php-hosting test file naming):
- Same z-score/p-value fixtures as `ExperimentAnalyzerTest.kt` Task 4 — literally
  shared numbers, asserting backend parity.
- Route auth tests mirroring `activationCounterAnalyzeTest.php`'s patterns.

**Gate:** PHP test suite green, Tasks 1-6 tests still green. **This is the task
that makes the feature live on both backends**, per the request's "in both
backends" requirement — do not consider the feature done before this lands,
even though Ktor was implemented first for TDD ergonomics.

---

### Task 8 — Dashboard: experiment list & creation screen

**Where:** `dashboard/src/commonMain/kotlin/com/quietmetrix/dashboard/` — new
`experiments/ExperimentListScreen.kt`, `experiments/ExperimentCreateScreen.kt`,
wired into whatever navigation host `Main.kt` already uses for other sections
(funnels, retention, etc. — follow that existing pattern exactly).

**What:**
- List screen: table of experiments (key, name, status, variant count, created
  date) per project, calling Task 2's `GET /projects/{id}/experiments`.
- Create screen: form for key, name, variant names + weights (client-side
  validates weights sum to 100 before submit, mirroring Task 1's server-side
  check so the user gets instant feedback), submits to `POST .../experiments`
  in `draft` status.
- Status controls (activate/pause/end) call `PATCH .../experiments/{key}`.

**Tests first:** per the dashboard's existing testing approach — push business
logic (weight-sum validation, status-transition legality: e.g. can't reactivate
an `ended` experiment) into plain testable functions/units covered by
`jvmTest`, per the global CLAUDE.md rule ("push logic into testable
functions/units even for UI work"). Also: every new UI string added via its own
`Res.string.<key>` + matching import (dashboard `jvmTest` enforces i18n parity +
no hardcoded literals, per memory — this is not optional).

**Gate:** dashboard jvmTest green (i18n parity + logic units), Tasks 1-7 still green.

---

### Task 9 — Dashboard: experiment results screen

**Where:** `dashboard/src/commonMain/kotlin/com/quietmetrix/dashboard/experiments/ExperimentResultsScreen.kt`.

**What:**
- Per-variant comparison table (n, converted, rate) plus a clear significance
  indicator (e.g. "B is winning, 95% confidence" / "not yet significant, keep
  running") sourced from Task 4/7's results endpoint.
- Metric selector (activation / funnel / retention) matching the `metric=`
  query param from Task 4.
- Empty/low-sample state: explicit "not enough data yet" rather than showing a
  misleading 0%, consistent with existing `RetentionCohort.activationRate`
  null-vs-zero convention already in the codebase (`Requests.kt:220-222`).

**Tests first:** unit-test the pure presentation-logic functions (picking which
badge/copy to show for a given significance result; is a variant below the
k-anonymity floor hidden) — same "push logic into testable functions" approach
as Task 8. i18n parity enforced the same way.

**Gate:** dashboard tests green, Tasks 1-8 still green. **Feature is complete
end-to-end at this point**: SDK assigns and reports variant → both backends
ingest, store, and analyze it → dashboard lets a user create, run, and read the
result of an experiment for both web and app traffic.

## 5. Cross-cutting items to verify once, at the end

- Docs: add a "Experiments" page under `docs/sdk/` and `docs/api-reference.md`
  per the `feedback_docs_are_a_deliverable` memory — setup + a runnable usage
  example (`getVariant("onboarding_v2")` branching sample) for each SDK
  platform page, not a one-line bullet.
- `openapi.yaml`: add the new `/sdk/experiments` and
  `/projects/{id}/experiments*` paths.
- Confirm `php-hosting/index.php` and `helpers.php` (currently showing as
  modified in git status at plan time) don't conflict with the new route
  registration — rebase this plan's Task 7 onto whatever that in-flight change
  turns out to be before starting.
