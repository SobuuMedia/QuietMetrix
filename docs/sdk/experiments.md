# Experiments (A/B testing)

> This page documents the legacy `getVariant` / `trackExperimentInteraction` API. New
> app-element experiments created in the dashboard use the v2 API below. The two APIs coexist;
> use the v2 placement API for Compose or SwiftUI elements and `trackEvent` goal events.
>
> See [Metric semantics and privacy](metrics-and-privacy.md) for fixed-window installation counts and limits.

## App-element experiments (v2)

Create an experiment in **Visibility** mode to show the tested element to the configured share
of eligible installations (for example, 25% see B and 75% see the control/A view). The app
chooses the placement key; the dashboard controls its percentage, optional country and language
filters, whether both filters must match (`all`, the default) or either may match (`any`), and
the optional goal event key. Country and language are independent app-supplied values. The SDK
does not infer a country from the device locale and sends neither value with analytics events.

Call `QuietMetrix.init` with `QuietMetrixConfig(..., countryCode = "FR", languageTag = "fr")`
when those dimensions are available. Both are optional; pass `null` when unavailable. Country
uses a two-letter ISO code and language accepts a two- or three-letter base language code.

The v2 configuration is fetched asynchronously. Until it is available, or when the installation
is ineligible, app code renders the control/A view. Once a Ready assignment reaches the UI render
boundary, call `recordExperimentExposure`; this records at most one aggregate exposure per local
installation, experiment revision and variant. Do not count `resolveExperiment` itself as an
exposure because resolving is not proof the placement was displayed.

```kotlin
import com.quietmetrix.analytics.compose.ExperimentElement
import com.quietmetrix.analytics.trackEvent

ExperimentElement(
    key = "login_message",
    control = { /* normal login screen */ },
    content = { /* challenger element */ },
)

Button(onClick = {
    trackEvent("login_message_success") // configure this as the experiment goal
}) { Text("Continue") }
```

`ExperimentVariants(key, variantA, variantB)` is available when both full views should be
authored explicitly. The equivalent SwiftUI `ExperimentElement` and `ExperimentVariants` sample
is in `samples/ios/QuietMetrixSample/ExperimentElement.swift`; include the SDK framework when
building that sample. The wrappers pin the first Ready assignment
while mounted, render the control/A fallback before resolution, and record exposure after the
view appears. Variants should therefore remain safe to show for the full lifetime of that view.

The v2 dashboard reports revision-scoped aggregate installations exposed and installations
reaching the configured goal. It intentionally avoids device or user identifiers. Metrics are
deduplicated locally, so deleting app data or reinstalling creates a new installation for these
counts. Results are cumulative since that revision started; date comparison is unavailable for
these metrics. Low-volume results remain marked as collecting evidence rather than presenting a
misleading confidence claim.

An experiment shows one of two versions of a screen, or a piece of a screen, to different
devices, and reports how often people interact with each — without QuietMetrix ever learning
which device saw which variant. It follows the same aggregate-only, on-device philosophy as the
rest of the SDK: assignment is computed locally, from a random id that never leaves the device,
and only rollup counts (how many impressions, how many interactions) ever reach the server.

## What an experiment is

An experiment has:

- A **key**, e.g. `checkout_cta` — what your app code refers to it by.
- Exactly **two variants** — a baseline and a challenger, e.g. `a`/`b` — each with a weight
  (they must sum to 100).
- A **traffic percentage** — the share of eligible devices that enter the experiment at all;
  everyone else sees your normal version, unaffected.
- An optional **country list** — if set, only devices whose device language region is in the
  list are eligible (see [Country targeting](#country-targeting) below).

You create and manage experiments in the dashboard's **Experiments** tab — unlike funnels,
which are declared in app code, an experiment's definition lives on the server. That's
deliberate: the whole point is being able to pause a bad experiment or reweight the split
without shipping a new app release. Your code only ever asks "what variant am I in?" and
reports "did they interact?" — it never defines the experiment itself.

## Mark a screen, or part of one

`getVariant(key)` returns the variant name to render — one of your two declared names, or
`"none"` when the device isn't enrolled (not eligible for the current traffic percentage,
excluded by country, or the experiment isn't active). Branch on it however fits: swap the
whole screen, or just one element.

```kotlin
import com.quietmetrix.analytics.getVariant

// A whole screen
when (getVariant("onboarding_v2")) {
    "b" -> NewOnboardingScreen()
    else -> OldOnboardingScreen() // "a", or "none" if not enrolled
}

// One element on an otherwise-unchanged screen
val buttonColor = if (getVariant("checkout_cta") == "b") Color.Green else Color.Blue
```

`getVariant` is synchronous and never touches the network — it reads from a snapshot fetched
at the *previous* app launch (see [Config timing](#config-timing) below), so it's safe to call
from anywhere, including composition/render code.

## Record the interaction

Call `trackExperimentInteraction(key)` when the user does the thing you're measuring — taps the
button, completes the flow, whatever the experiment is testing:

```kotlin
import com.quietmetrix.analytics.trackExperimentInteraction

Button(onClick = {
    trackExperimentInteraction("checkout_cta")
    proceedToCheckout()
}) { Text("Checkout") }
```

This is recorded at most once per experiment per session, and only counts if `getVariant` was
called for that key first this session (otherwise there's no impression to attribute it to).

## Config timing

The SDK fetches the current experiment config once per app launch and caches it for the *next*
launch — a variant never changes mid-session, even if you edit the experiment in the dashboard
while the app is open. Concretely:

- **First launch after install** (no cache yet): `getVariant` returns `"none"` for every
  experiment, for the whole session.
- **Every launch after that**: the SDK uses whatever was cached from the previous session,
  while quietly fetching fresh config in the background for the *next* one.

Pausing, ending, closing or shipping takes effect in a subsequent app session after a
successful config refresh. Offline devices or failed refreshes continue using cached config;
existing sessions never change assignment. The endpoint may be cached for up to five minutes.

## Country targeting

Country targeting uses only the optional country code supplied by the app through
`QuietMetrixConfig.countryCode` or `updateAudience(countryCode, languageTag)`. The SDK does not
infer country from IP, GPS, or locale region, and it does not send this targeting value with
analytics events. If an experiment has a country list and the app has not supplied a matching
country, the device is not enrolled and `getVariant` returns `"none"` for that experiment.

## What gets measured

Every real (non-`"none"`) `getVariant` call records one **impression** for that variant, and
every `trackExperimentInteraction` call records one **interaction** — both are plain aggregate
counters, deduplicated per session, with no install or session identifier attached. The
dashboard's results view shows impressions, interactions, and the interaction rate per variant,
plus a significance indicator once each variant has enough sessions — before that, it shows
"not enough data yet" rather than a misleading 0%. As with every other QuietMetrix number, a
variant's results stay hidden until enough distinct devices have contributed to it.

## Finish, ship or close

The dashboard displays Running (`active`), Draft, Paused, Finished (`ended`), Shipped and Closed.
Finished records preserve compatibility with existing experiments and can subsequently ship or close.
From Running or Paused, **Ship winner** chooses the declared variant supported by an available
significant comparison; **Keep baseline** explicitly chooses the first declared variant when
there is no supported winner. Both persist `status: "shipped"` and `selected_variant` atomically.
The API rejects missing or undeclared rollout choices. **Close without rollout** persists
`closed` and removes the experiment from future refreshed SDK configs. Shipped and closed
records cannot be edited or transitioned again.

Shipping is a full rollout across all countries and 100% of traffic. The SDK config retains
the two original variant names but assigns weights 100/0 to the selected/unselected variant.
The stored original split, traffic and country targeting remain unchanged for reference.
Results continue to reflect the selected date range and may include post-rollout traffic;
the shipped medal identifies the persisted choice, while confidence reflects live results.
The confidence score is `(1 − p-value) × 100`, not a posterior probability that a variant is best.
Counts, Wilson intervals and z/p statistics are available in Expert mode.

## Plan limits

A free-plan project may have up to 2 experiments in draft, active or paused state at a time;
ended, shipped and closed experiments do not consume slots.
**Self-hosted QuietMetrix has no limit at all**, regardless of plan — this only applies to the
hosted/cloud offering.

## Reference

| Call | Returns | Notes |
|---|---|---|
| `getVariant(key: String)` | `String` | One of the two declared variant names, or `"none"`. Synchronous, never throws. |
| `trackExperimentInteraction(key: String)` | — | Fire-and-forget. No-op without a prior `getVariant` call this session. |

Both are plain functions in the `com.quietmetrix.analytics` package — same as `trackEvent` — on
every SDK target: Android, iOS, JVM, Web/JS, and desktop (macOS/Windows/Linux).
