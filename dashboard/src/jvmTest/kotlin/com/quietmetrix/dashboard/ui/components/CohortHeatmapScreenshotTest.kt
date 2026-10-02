package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.ui.screens.epochDayFromIsoOrNull
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 25's cohort heatmap (`CohortHeatmap.kt`), derived per
 * `goldens/README.md`'s "How a task derives its own golden" process.
 *
 * **Region visibility check (performed before writing this test)**: `_source/06-retention-normal.png`
 * (909x540, same source `RetentionScreenshotTest.kt`'s hero/cards goldens use) cuts off after the
 * "🌅 Next day" vital card's label and a sliver of its "42%" — the heatmap sits entirely BELOW that,
 * fully covered by the pinned-open Ask-drawer overlay and the "MOCKUP ONLY" toolbar strip, same as
 * `RetentionScreenshotTest.cardsNormalStateMatchesGolden`'s own rejection finding one region up.
 * **Verdict: real-crop derivation REJECTED, not possible for ANY of this component's goldens.**
 * Every golden below is instead derived from the design prototype's own cohort-row/cell markup
 * (`QuietMetrix Prototype.dc.html:258-320`: `cellBase`, the `0.12 + v/50*0.88` alpha formula, the
 * `si===0`/`si===1` A/B checkbox coloring) plus this codebase's already-locked design tokens.
 * Rendered via `QuietMetrixTheme(Dark)` at the default 1024x768 `runComposeUiTest` canvas, content
 * `Modifier.width(900.dp)`, recorded with `-Pgolden.record=true`.
 *
 * Fixture cohorts are hand-picked to exercise every cell kind in one render: a confirmed-zero day7
 * cell (NOT a placeholder — the whole point of Task 24's `effectiveDayValue` reuse), a too-young
 * day30 placeholder, and ordinary positive percentages across the intensity ramp.
 */
@OptIn(ExperimentalTestApi::class)
class CohortHeatmapScreenshotTest {

    private val today = epochDayFromIsoOrNull("2026-10-02")!!

    /** Fixed "today" (2026-10-02) cohorts: one old aged-out cohort (confirmed day7/day30 zero and
     *  real values), one mid-age cohort, and one brand-new cohort (day7/day30 genuinely too young —
     *  must render as the dashed placeholder, never a fabricated 0%). */
    private val cohorts = listOf(
        RetentionCohort(cohortDate = "2026-08-01", size = 182, day1 = 0.44, day3 = 0.31, day7 = 0.22, day14 = 0.17, day30 = 0.0),
        RetentionCohort(cohortDate = "2026-09-10", size = 204, day1 = 0.41, day3 = 0.29, day7 = 0.21, day14 = 0.15, day30 = null),
        RetentionCohort(cohortDate = "2026-09-30", size = 96, day1 = 0.39, day3 = null, day7 = null, day14 = null, day30 = null),
    )

    @Suppress("DEPRECATION")
    @Test
    fun normalByWeekStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    CohortHeatmap(cohorts, modifier = Modifier.width(900.dp))
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("retention/heatmap-normal.png")
    }

    // Clicks the "By day" toggle pill, switching grouping away from the ByWeek default — exercises
    // the row-reshape path end to end (not just buildCohortHeatmapRows in isolation).
    @Suppress("DEPRECATION")
    @Test
    fun byDayToggleStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    CohortHeatmap(cohorts, modifier = Modifier.width(900.dp))
                }
            }
        }
        onNodeWithText("By day").performClick()
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("retention/heatmap-by-day.png")
    }

    // Ticks the two most recent rows' checkboxes (A then B) — exercises toggleCohortHeatmapSelection
    // end to end and confirms the checkbox fills lime/lavender per slot, per the design handoff's
    // "checkbox-like A/B compare selector".
    @Suppress("DEPRECATION")
    @Test
    fun twoRowsSelectedStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    CohortHeatmap(cohorts, modifier = Modifier.width(900.dp))
                }
            }
        }
        val checkboxes = onAllNodesWithContentDescription("Select", substring = true)
        checkboxes[0].performClick()
        waitForIdle()
        onAllNodesWithContentDescription("Select", substring = true)[0].performClick()
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("retention/heatmap-two-selected.png")
    }
}
