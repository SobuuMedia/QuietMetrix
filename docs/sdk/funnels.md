# Funnels

A funnel answers a question your raw event log can't: *of everyone who started X, how many
finished, and where did the rest leave?* QuietMetrix funnels are an ordered list of steps —
each step is an event you already track — plus a conversion window. They analyze retroactively
over data you already have, and apps can declare them in code so they show up in the dashboard
with no manual setup.

## What a funnel is

A funnel definition is just metadata: a name, an ordered list of steps, and a time window.
**Declaring a funnel emits no events of its own.** Each step references an event name (and
optionally a screen or exact-match props) that your app already sends. This means:

- A funnel can be defined *after* the events it analyzes already exist in your data.
- Declaring or editing a funnel costs nothing in event volume or quota.
- You can iterate on step definitions from the dashboard without shipping an app update.

## Define a funnel

```kotlin
import com.quietmetrix.analytics.Funnel
import com.quietmetrix.analytics.FunnelStep
import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig

val signupFunnel = Funnel(
    key = "signup",
    name = "Signup",
    steps = listOf(
        FunnelStep(key = "view", event = "screen_view", screen = "signup"),
        FunnelStep(key = "submit", event = "signup_submitted"),
    ),
    windowSeconds = 7L * 24 * 3600, // default: 7 days
)

QuietMetrix.init(
    QuietMetrixConfig(
        storageKeyPrefix = "myapp_",
        trackingEndpoint = "https://your-server.com/api/v1",
        apiKey = "qm_ak_your_api_key",
        funnels = listOf(signupFunnel),
    )
)
```

`key` is a slug (`^[a-z0-9_-]{1,64}$`) your app uses to reference the funnel — it also becomes
the SDK's upsert key when re-registering. `name` is what shows up in the dashboard. A funnel
needs 2–10 steps.

## Registration lifecycle

On `QuietMetrix.init`, the SDK fingerprints the funnels in `QuietMetrixConfig.funnels` and
compares that fingerprint against what it last successfully registered (persisted across app
launches). If nothing changed, **no network call happens** — most launches of the same app
version register nothing. If a funnel was added, removed, or edited in code, the SDK POSTs the
new definitions once, in the background.

Registration is fire-and-forget: it never blocks `init`, never throws into your app, and a
failure (offline, server error) simply leaves the stored fingerprint unchanged so the next
launch retries automatically. Once registered, the funnel appears in the dashboard immediately
— no dashboard-side setup required.

## Tracking steps: two ways

**You usually don't need to change your tracking code at all.** Since steps reference event
names you already send, a funnel matches against your existing event stream the moment it's
registered — including events sent *before* the funnel was ever defined.

If you'd rather reference a step by a short key instead of repeating the raw event name and
props at every call site, `Funnel.step(...)` is a typed convenience that emits the step's
declared event for you:

```kotlin
// Equivalent to: trackEvent("signup_submitted", props = mapOf("plan" to "pro"))
signupFunnel.step("submit", props = mapOf("plan" to "pro"))
```

Props passed to `step()` are merged with the step's own declared props (see
[Filtering a step](#filtering-a-step)), with the call-site value winning on conflict. Calling
`step()` with a key the funnel doesn't declare is a silent no-op.

## Matching rules

- **Strict order.** Steps must occur in the declared sequence — step 2 must happen after
  step 1, not before or at the same time.
- **The window starts at step 1.** Every subsequent step must land within `windowSeconds` of
  the *first* matching event, not the previous step.
- **A step must occur after the previous match**, but repeated events of the same type don't
  break anything — the matcher always looks for the *next* occurrence after the last match, so
  extra step-1 events before step-2 fires are simply ignored.
- **An actor is counted once, at their first entry.** If someone's first attempt expires
  before finishing, a second unrelated pass through the same events later doesn't resurrect
  them into a completion — their result is fixed at their first entry.

## Choosing good steps

A funnel is only useful if the steps mean something to a human reading the dashboard:

- **3–6 steps** is the sweet spot. Fewer than 2 isn't a funnel; more than 10 stops being
  readable (and the server enforces that cap).
- **Name steps after user intent, not UI internals** — `"viewed_pricing"` tells you more than
  `"screen_view"` with a prop filter buried in the definition.
- **Put the broadest, earliest moment first.** If you start too late (e.g. at "form
  submitted" instead of "form viewed"), you lose visibility into people who never got that
  far.
- **Don't mix unrelated actions into one funnel** unless the sequence is genuinely the flow you
  care about — a funnel spanning unrelated screens just to "see everything" produces numbers
  nobody can act on.

## Filtering a step

A step can narrow which events count by `screen` and/or exact-match `props`:

```kotlin
FunnelStep(key = "view", event = "screen_view", screen = "signup")
FunnelStep(key = "started_pro_trial", event = "trial_started", props = mapOf("plan" to "pro"))
```

The `screen_view` + `screen` combination above is the most common shape for mobile funnels,
since `screen_view` fires automatically wherever you already call `trackScreen(...)`.

## Editing in the dashboard

A funnel registered by the SDK shows a **"From app"** badge in the dashboard. Editing it there
**locks** the funnel — from that point on, SDK registration silently skips it rather than
overwriting the analyst's edit, even if the app's code still declares the old version. To hand
control back to the app, delete the funnel from the dashboard; the next matching app launch
re-registers it unlocked.

## Reading the results

Given a funnel `view → submit` over the last 7 days with 1,000 people reaching `view`, 230
finishing `submit`:

| Field | Meaning | Example |
|---|---|---|
| `entered` | Actors whose *first* step landed in the requested time range | 1,000 |
| `converted` | Actors who reached the last step | 230 |
| `overall_conversion` | `converted / entered` | 23% |
| Per step `conversion_from_entry` | Share of everyone who *entered*, still at this step | step 2: 23% |
| Per step `conversion_from_previous` | Share of the *previous step's* actors who made it here | step 2: 23% (only 2 steps here — with 3+ steps these two numbers diverge and both matter) |
| Per step `dropped` / `drop_rate` | How many/what fraction left at this step, relative to the previous step | 770 / 77% |
| `median_ms_from_previous`, `p90_ms_from_previous` | Typical and slow time-to-next-step, per step | step 2: 45s median, 6m p90 |
| `median_total_ms` | Typical time from entry to full completion, among those who completed | 2m |

A **large gap between the median and p90** time-to-next-step is often more actionable than the
conversion rate itself — it means most people move quickly, but a meaningful minority get
stuck, which usually points at a specific UX friction point rather than a broad drop-off.

**Breakdown** splits the same numbers by `country`, `platform`, `device_class`, or `language`,
attributing each actor once — using the dimension value from their *entry* event — so nobody is
double-counted across breakdown values. **Trend** buckets actors by the day of their entry
event, so you can see whether a release moved the conversion rate.

## Limits

- Up to 20 funnels per project, 10 steps per funnel.
- Conversion window: 60 seconds to 90 days.
- A very high-traffic project over a long time range can exceed the results endpoint's
  internal row cap; the response's `truncated` field is `true` when this happens; the dashboard
  displays a warning to use a shorter range.

## Troubleshooting

**A funnel I declared in code never appears in the dashboard.**
Check that `apiKey` and `trackingEndpoint` are set on `QuietMetrixConfig` — registration is
silent by design, but it needs both to run at all. If the fingerprint hasn't changed since a
previous successful registration, the SDK correctly does nothing; delete the app's local
storage (or bump the funnel's definition) to force a retry.

**A funnel shows zero entries even though the app is definitely sending the step events.**
The most common causes: the step's `event` name doesn't exactly match what's sent (case- and
character-sensitive), or the user declined tracking consent so nothing was ever recorded.

**Conversion looks lower than I'd expect.**
Check `counted_by` in the results — `"session"` means some or all of the matched events
predate the install-identity migration and fell back to session-scoped counting, which
under-reports any funnel that spans more than one app session. Also check that
`windowSeconds` is generous enough for the real flow — a 1-hour window on a multi-day
consideration funnel (e.g. free trial → paid conversion) will systematically under-report.

## Privacy note

Funnels are counted per **install** — a per-project, salted, non-reversible hash of a
device-local id — never by a user id. See
[the publishable-API-key security note](../security/publishable-api-key.md) and
[GDPR notes](../operations/gdpr.md) for how QuietMetrix handles identity generally.
