# Visual-parity goldens

Golden images for `ScreenshotGolden.assertMatchesGolden` (see
`dashboard/src/jvmTest/kotlin/com/quietmetrix/dashboard/testutil/ScreenshotGolden.kt`),
used by the dashboard-redesign plan's screen/component tasks to gate visual
parity against the design handoff.

## Source material

`_source/` holds the original reference screenshots from
`design_handoff_dashboard_redesign/screenshots/` unmodified, copied in once
as the ground truth every later crop is derived from. Note: despite the
`.png` extension, these files are actually baseline JPEG (909x540,
3-component, verified with `file`) — re-derive crops from `_source/`, not
from any copy that may have been re-encoded.

Do not add new files directly under `_source/` later — if the design handoff
changes, replace the file there and note it in this README's log below.

## How a task derives its own golden

Task 3 (this task) intentionally does **not** pre-crop every component/region
up front: the exact pixel rect for e.g. "the Overview vitals row" depends on
the actual test canvas size the consuming task's `runComposeUiTest` renders
at, which isn't known until that task writes its Composable + test. Pre-crop
before that exists risks a golden sized for a guess, not the real render.

**Ruling (recorded so later tasks don't re-litigate this):** each
screen/component task that needs a golden crops it *at the time it writes its
screenshot test*, from the matching file in `_source/`, at the exact
dimensions its own `runComposeUiTest` content renders at. The task commits
the resulting crop under `goldens/<screen>/<region>.png` and appends one line
to the log below: source file, pixel rect (`x,y,w,h`) in the *source* image's
coordinate space, and the target test canvas size if it differs (e.g. after
scaling). This keeps every crop traceable back to `_source/` without
requiring this task to predict render sizes it can't yet know.

## Log

| Golden path | Source | Source rect (x,y,w,h) | Notes |
|---|---|---|---|
| `_infra/theme-dark-bg.png` | n/a (not a `_source/` crop) | n/a | Task 8. Solid-color reference, not a design-handoff crop: `QuietMetrixTheme(Dark) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) }` rendered at the default 1024x768 `runComposeUiTest` canvas, verified at `rgb(19,16,23)` = `#131017` (`QmDarkBg`). Recorded via `-Pgolden.record=true` from the real render (see Task 8 report for the record-then-verify steps), not hand-crafted bytes. |
| `_infra/theme-light-bg.png` | n/a (not a `_source/` crop) | n/a | Task 8. Same as above for `QuietMetrixTheme(Light)`, verified at `rgb(247,242,234)` = `#F7F2EA` (`QmLightBg`), 1024x768. |
| `pill/variants.png` | `_source/01-overview-normal.png` | `(20,125,440,90)` for the nav-pill shape/color reference (the `Overview` active pill); no delta-pill rect — the vitals row sits below this source screenshot's 909x540 frame (covered by the Ask-drawer overlay/cut off), so `Lime`/`Lavender` were derived from the already-locked `ExtendedColors.lime`/`.lavender` tokens (`theme/Color.kt`, spec-sourced) instead of a JPEG crop | Task 9. `Pill` composable's 4 variants (`NavActive`, `Neutral`, `Lime`, `Lavender`) rendered in one `Row` via `QuietMetrixTheme(Dark)`, full default 1024x768 `runComposeUiTest` canvas (matching Task 8's precedent), recorded with `-Pgolden.record=true`. Visually cross-checked against the `Overview`/`Flow`/`Retention` active-pill crops and the Ask-drawer suggestion-chip crops in `_source/01-overview-normal.png` and `05-flow-normal.png` for shape/fill plausibility — exact JPEG bytes not targeted, per this README's own ruling. |
| `kpi-card/with-delta-and-compare.png` | n/a (not a `_source/` crop) | n/a — same fallback as `pill/variants.png` above: the Overview vitals row sits below `_source/01-overview-normal.png`'s 909x540 frame (covered by the Ask-drawer overlay/cut off), confirmed by direct visual inspection before recording this golden | Task 10. `KpiCard` restyle (20dp radius via `MaterialTheme.shapes.extraLarge`, `extended.hairline` border, lime delta `Pill`, caption, lavender compare-note chip) derived from the already-locked design tokens (`theme/Color.kt`/`Shapes.kt`/`Typography.kt`) + the HTML prototype's `vitals` card markup (`design_handoff_dashboard_redesign/QuietMetrix Prototype.dc.html` lines ~93-99: `border-radius:20px`, `padding:22px`, `font-size:44px/700` value, delta pill, caption line, `rgba(185,167,255,.18)` compare note) + the README prose (Overview vitals bullet, Design System spacing/shape, Interactions hover/press rules). Rendered via `QuietMetrixTheme(Dark)` at the default 1024x768 `runComposeUiTest` canvas (card itself `Modifier.width(280.dp)`), recorded with `-Pgolden.record=true`, self-consistent regression baseline per the standing fallback ruling (same pattern as Task 9's `pill/variants.png` entry). |
