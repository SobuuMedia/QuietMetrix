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
| _(none yet — filled in by the tasks that create them)_ | | | |
