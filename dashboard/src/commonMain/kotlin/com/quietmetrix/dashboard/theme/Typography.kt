package com.quietmetrix.dashboard.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.bricolage_grotesque_bold
import com.quietmetrix.dashboard.resources.bricolage_grotesque_extrabold
import com.quietmetrix.dashboard.resources.bricolage_grotesque_medium
import com.quietmetrix.dashboard.resources.bricolage_grotesque_regular
import com.quietmetrix.dashboard.resources.bricolage_grotesque_semibold
import com.quietmetrix.dashboard.resources.jetbrains_mono_bold
import com.quietmetrix.dashboard.resources.jetbrains_mono_medium
import com.quietmetrix.dashboard.resources.jetbrains_mono_regular
import org.jetbrains.compose.resources.Font

/**
 * Bundled Bricolage Grotesque in every weight the design hand-off shipped
 * (design_handoff_dashboard_redesign/README.md > Design System > Typography:
 * "Headings/UI: Bricolage Grotesque (400/500/600/700/800)"). Every
 * [androidx.compose.material3.Typography] slot in [QmTypography] uses this
 * family — "Headings/UI" covers all UI copy, not just headings.
 *
 * Each [Font] call is given its [FontWeight] explicitly rather than relying
 * on the embedded font-table metadata: per Task 4's finding, Google's CDN
 * slices a single variable font into static per-weight files whose internal
 * names don't reliably map back to a logical weight, so the weight passed
 * here is the only source of truth the text layout engine uses to pick a
 * face.
 *
 * `@Composable` because loading a bundled font through Compose
 * Multiplatform's resource system ([org.jetbrains.compose.resources.Font])
 * is itself `@Composable` — it resolves through the current composition
 * (synchronously on the JVM target, asynchronously on wasmJs).
 */
@Composable
private fun bricolageGrotesque(): FontFamily = FontFamily(
    Font(Res.font.bricolage_grotesque_regular, FontWeight.Normal),
    Font(Res.font.bricolage_grotesque_medium, FontWeight.Medium),
    Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(Res.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(Res.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

/**
 * Bundled JetBrains Mono in every weight the design hand-off shipped
 * (README > Design System > Typography: "Monospace (keys, code, exact
 * metrics): JetBrains Mono (400/500/700)"). Used only by
 * [QmTypographyExtras] — nothing in [QmTypography]'s 13 Material3 slots is
 * monospace.
 */
@Composable
private fun jetBrainsMono(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * The dashboard's Material3 [Typography], wired to the bundled Bricolage
 * Grotesque family and sized from
 * design_handoff_dashboard_redesign/README.md > Design System > Typography.
 * Material3 exposes exactly 13 fixed slots; the README names 6 size tiers
 * (hero, card value, section title, body, captions/meta, micro labels), so
 * each tier is spread across the slots it best fits, picking one concrete
 * value inside each cited px range (treated as sp per the README's own
 * "exact pixel parity not required, proportions should match" framing):
 *
 * - `displayLarge` — hero H1, README "46–52px/700 tracking -0.025em" → 48sp.
 * - `displayMedium` / `displaySmall` — card values, README "34–44px/700" →
 *   40sp / 34sp (upper/lower end, for primary vs. denser stat tiles).
 * - `headlineLarge` / `headlineMedium` — section titles, README
 *   "20–22px/700" → 22sp / 20sp (upper/lower end).
 * - `headlineSmall` — a lighter tertiary heading Material3 expects but the
 *   README doesn't separately size: same 20sp, one Bricolage weight step
 *   down (600, SemiBold).
 * - `titleLarge` / `titleMedium` — emphasized body-as-title, README
 *   "body 14–16px" → 16sp / 14sp, Medium weight.
 * - `titleSmall` — captions/meta, README "captions/meta 13–14px" (upper
 *   end) → 14sp, Medium weight.
 * - `bodyLarge` / `bodyMedium` — body copy, README "body 14–16px" → 16sp /
 *   14sp, Normal weight.
 * - `bodySmall` — captions/meta, README "captions/meta 13–14px --ink2"
 *   (lower end) → 13sp. The `--ink2` color pairing is a
 *   `MaterialTheme.colorScheme.onSurfaceVariant` concern applied by call
 *   sites, not something baked into a [TextStyle] here.
 * - `labelLarge` / `labelMedium` / `labelSmall` — micro labels, README
 *   "11–13px" → 13sp / 12sp / 11sp (upper/mid/lower end).
 *
 * This is a `@Composable` property, not a plain constant, for the same
 * reason [bricolageGrotesque] is `@Composable`. Reading it from
 * [QuietMetrixTheme] (already `@Composable`) works exactly like reading
 * `MaterialTheme.colorScheme`, so [QuietMetrixTheme]'s
 * `typography = QmTypography` call site needs no change.
 */
val QmTypography: Typography
    @Composable
    get() {
        val heading = bricolageGrotesque()
        return Typography(
            displayLarge = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                letterSpacing = (-0.025).em,
            ),
            displayMedium = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 48.sp,
            ),
            displaySmall = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
            ),
            headlineLarge = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            titleSmall = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            bodySmall = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            labelLarge = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = heading,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
        )
    }

/**
 * Supplementary text styles outside Material3 [Typography]'s 13 fixed
 * slots. The design hand-off calls for a monospace face "for keys, code,
 * exact metrics" (README > Design System > Typography) that none of those
 * slots can hold — Material3 has no monospace concept — so it lives here
 * instead, in the bundled JetBrains Mono family.
 */
object QmTypographyExtras {

    /**
     * Default monospace style for exact metric figures, masked API keys,
     * and code snippets — README: "Monospace (keys, code, exact metrics):
     * JetBrains Mono (400/500/700)". Sized against the body tier (README:
     * "body 14–16px") since the README gives JetBrains Mono's weights but
     * not a dedicated size; Medium (500) distinguishes it from plain body
     * copy at a glance.
     */
    val metricMono: TextStyle
        @Composable
        get() = TextStyle(
            fontFamily = jetBrainsMono(),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

    /**
     * Emphasized variant of [metricMono] for a precise figure that needs to
     * stand out next to plain-language copy (e.g. an exact count shown
     * alongside the AI-written hero sentence, or a raw stat in Expert
     * mode). Same family, Bold (700) per README's "JetBrains Mono
     * (400/500/700)", upper end of the body range.
     */
    val metricMonoEmphasis: TextStyle
        @Composable
        get() = TextStyle(
            fontFamily = jetBrainsMono(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
}
