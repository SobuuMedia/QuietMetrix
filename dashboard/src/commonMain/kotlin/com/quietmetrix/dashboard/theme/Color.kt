package com.quietmetrix.dashboard.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Dashboard-redesign spec palette.
//
// Source of truth: design_handoff_dashboard_redesign/README.md > Design
// System > Colors. Every literal value below is copied byte-for-byte from
// that section; do not "round" or re-derive them — downstream tasks
// (Typography, Shapes, QuietMetrixTheme wiring, every screen) assume these
// exact values.
//
// Mapping decision (ColorScheme vs. ExtendedColors):
// - `--bg`/`--sf`/`--sf2`/`--ink`/`--ink2` map onto Material3 ColorScheme's
//   background/surface/surfaceVariant/onBackground(+onSurface)/
//   onSurfaceVariant slots respectively — they are exactly the
//   background/surface/text roles those slots exist for, and they DO
//   theme-swap (dark vs. light instance), which is what ColorScheme is for.
// - `--ln` (hairline), `--chip` (subtle fill) and `--hl` (bar-chart
//   highlight) have no corresponding Material3 ColorScheme slot, so they
//   live on [ExtendedColors] as `hairline`/`chipFill`/`barHighlight`. They
//   still theme-swap (their literal values differ between
//   [DarkExtendedColors] and [LightExtendedColors]) — only the three fixed
//   accents below are prohibited from doing that.
// - Lime/lavender/coral are "fixed, do not theme-swap" per the README, so
//   they must NOT be Material3 ColorScheme roles (primary/secondary/error
//   etc. are expected to swap between the dark/light instances by
//   Material3 convention). They live on [ExtendedColors] as
//   `lime`/`onLime`/`lavender`/`coral`, holding the *same* literal Color in
//   both [DarkExtendedColors] and [LightExtendedColors].
// - Material3 still requires primary/secondary/error roles to exist on
//   every ColorScheme even though the spec defines no theme-swapping
//   accent of its own. Rather than reuse a fixed accent there (which would
//   defeat its "does not theme-swap" guarantee), those roles are filled
//   from the neutral tokens the spec already defines for the equivalent
//   UI state: the README's "Active pill is filled solid (`--ink` bg /
//   `--bg` text)" description is reused for primary/onPrimary, `coral` is
//   reused for `error` (the README explicitly assigns coral to
//   "errors/destructive actions"), and `ink`/`onLime`'s dark text are used
//   where Material3 needs contrasting text on a light fill.
// ---------------------------------------------------------------------------

// --- Dark theme tokens (design_handoff_dashboard_redesign/README.md:22-30) --

/** `--bg` (dark): page background. */
internal val QmDarkBg = Color(0xFF131017)

/** `--sf` (dark): card/surface fill. */
internal val QmDarkSf = Color(0xFF1C1822)

/** `--sf2` (dark): nested surface / skeleton fill. */
internal val QmDarkSf2 = Color(0xFF2A2531)

/** `--ink` (dark): primary text. */
internal val QmDarkInk = Color(0xFFF5F0E8)

/** `--ink2` (dark): secondary text. */
internal val QmDarkInk2 = Color(0xFFB3ABBD)

/** `--ln` (dark): hairline border, `rgba(255,255,255,0.08)`. */
internal val QmDarkHairline = Color.White.copy(alpha = 0.08f)

/** `--chip` (dark): subtle chip/icon-button fill, `rgba(255,255,255,0.09)`. */
internal val QmDarkChip = Color.White.copy(alpha = 0.09f)

/**
 * `--hl` (dark): bar-chart highlight fill, `rgba(200,242,90,0.28)` — note
 * (200,242,90) is the lime accent's own RGB, at reduced alpha.
 */
internal val QmDarkHighlight = Color(0xFFC8F25A).copy(alpha = 0.28f)

// --- Light theme tokens (design_handoff_dashboard_redesign/README.md:32-33) -

/** `--bg` (light): page background. */
internal val QmLightBg = Color(0xFFF7F2EA)

/** `--sf` (light): card/surface fill. */
internal val QmLightSf = Color(0xFFFFFFFF)

/** `--sf2` (light): nested surface / skeleton fill. */
internal val QmLightSf2 = Color(0xFFEFE7DA)

/** `--ink` (light): primary text. */
internal val QmLightInk = Color(0xFF1B1720)

/** `--ink2` (light): secondary text. */
internal val QmLightInk2 = Color(0xFF5A5265)

/** `--ln` (light): hairline border, `rgba(40,25,50,0.11)`. */
internal val QmLightHairline = Color(0xFF281932).copy(alpha = 0.11f)

/** `--chip` (light): subtle chip/icon-button fill, `rgba(40,25,50,0.07)`. */
internal val QmLightChip = Color(0xFF281932).copy(alpha = 0.07f)

/** `--hl` (light): bar-chart highlight fill, `rgba(150,200,40,0.30)`. */
internal val QmLightHighlight = Color(0xFF96C828).copy(alpha = 0.30f)

// --- Fixed accents — identical in both themes, never theme-swapped --------
// design_handoff_dashboard_redesign/README.md:35-38

/** Lime accent: primary CTA, positive delta, success, "winning" states. */
internal val QmLime = Color(0xFFC8F25A)

/** Text color used on top of the lime accent fill (always this value). */
internal val QmOnLime = Color(0xFF16131A)

/** Lavender accent: AI/assistant surfaces, secondary comparison series, "B" variant. */
internal val QmLavender = Color(0xFFB9A7FF)

/** Coral accent: errors/destructive actions, crash indicators. */
internal val QmCoral = Color(0xFFFF8A5B)

/**
 * Material3 [ColorScheme] for dark mode, built from the dashboard-redesign
 * spec palette (see file-level KDoc for the ColorScheme-vs-[ExtendedColors]
 * mapping decision).
 */
val DarkColors: ColorScheme = darkColorScheme(
    primary = QmDarkInk,
    onPrimary = QmDarkBg,
    secondary = QmDarkInk2,
    onSecondary = QmDarkBg,
    secondaryContainer = QmDarkSf2,
    onSecondaryContainer = QmDarkInk,
    background = QmDarkBg,
    onBackground = QmDarkInk,
    surface = QmDarkSf,
    onSurface = QmDarkInk,
    surfaceVariant = QmDarkSf2,
    onSurfaceVariant = QmDarkInk2,
    error = QmCoral,
    onError = QmOnLime,
    errorContainer = QmDarkSf2,
    onErrorContainer = QmCoral,
)

/**
 * Material3 [ColorScheme] for light mode, built from the dashboard-redesign
 * spec palette (see file-level KDoc for the ColorScheme-vs-[ExtendedColors]
 * mapping decision).
 */
val LightColors: ColorScheme = lightColorScheme(
    primary = QmLightInk,
    onPrimary = QmLightBg,
    secondary = QmLightInk2,
    onSecondary = QmLightBg,
    secondaryContainer = QmLightSf2,
    onSecondaryContainer = QmLightInk,
    background = QmLightBg,
    onBackground = QmLightInk,
    surface = QmLightSf,
    onSurface = QmLightInk,
    surfaceVariant = QmLightSf2,
    onSurfaceVariant = QmLightInk2,
    error = QmCoral,
    onError = QmOnLime,
    errorContainer = QmLightSf2,
    onErrorContainer = QmCoral,
)

// ---------------------------------------------------------------------------
// Extended brand/chart tokens (not part of Material3 ColorScheme)
// ---------------------------------------------------------------------------

/**
 * App-specific color tokens that fall outside Material3's [ColorScheme]
 * slots: chart tokens, demo-mode badge, and the dashboard-redesign spec's
 * hairline/chip/highlight surface tokens plus its three fixed accents
 * (lime, lavender, coral), which must hold the same value in both
 * [DarkExtendedColors] and [LightExtendedColors].
 */
@Immutable
data class ExtendedColors(
    val demoBadge: Color,
    val onDemoBadge: Color,
    val chartAxis: Color,
    val chartGrid: Color,
    val chartSeries: List<Color>,
    /** Drop-off bar/wedge in the funnel chart — deliberately not part of [chartSeries] so a
     * project's series colors never accidentally collide with the "someone left here" signal. */
    val chartDropoff: Color,
    /** `--ln`: hairline border used for card separation in place of shadows. Theme-swaps. */
    val hairline: Color,
    /** `--chip`: subtle fill for chips and icon buttons. Theme-swaps. */
    val chipFill: Color,
    /** `--hl`: bar-chart highlight fill for the record/called-out bar. Theme-swaps. */
    val barHighlight: Color,
    /** Fixed lime accent — primary CTA, positive delta, success. Identical in both themes. */
    val lime: Color,
    /** Text color on top of [lime] fills. Identical in both themes. */
    val onLime: Color,
    /** Fixed lavender accent — AI/assistant surfaces, comparison series. Identical in both themes. */
    val lavender: Color,
    /** Fixed coral accent — errors/destructive actions, crash indicators. Identical in both themes. */
    val coral: Color,
)

/** [ExtendedColors] for dark mode. */
val DarkExtendedColors = ExtendedColors(
    demoBadge = Color(0xFFD29922),
    onDemoBadge = Color(0xFF1C2128),
    chartAxis = Color(0xFF30363D),
    chartGrid = Color(0xFF21262D),
    chartSeries = listOf(
        Color(0xFF58A6FF),
        Color(0xFF3FB950),
        Color(0xFFD29922),
        Color(0xFFF778BA),
        Color(0xFFA371F7),
        Color(0xFFF85149),
    ),
    chartDropoff = Color(0xFFF85149),
    hairline = QmDarkHairline,
    chipFill = QmDarkChip,
    barHighlight = QmDarkHighlight,
    lime = QmLime,
    onLime = QmOnLime,
    lavender = QmLavender,
    coral = QmCoral,
)

/** [ExtendedColors] for light mode. */
val LightExtendedColors = ExtendedColors(
    demoBadge = Color(0xFFBF8700),
    onDemoBadge = Color(0xFFFFFFFF),
    chartAxis = Color(0xFFD0D7DE),
    chartGrid = Color(0xFFE8EAED),
    chartSeries = listOf(
        Color(0xFF0969DA),
        Color(0xFF1A7F37),
        Color(0xFFBF8700),
        Color(0xFF8250DF),
        Color(0xFFDB61A4),
        Color(0xFFCF222E),
    ),
    chartDropoff = Color(0xFFCF222E),
    hairline = QmLightHairline,
    chipFill = QmLightChip,
    barHighlight = QmLightHighlight,
    lime = QmLime,
    onLime = QmOnLime,
    lavender = QmLavender,
    coral = QmCoral,
)

/** Composition local providing the active theme's [ExtendedColors]. */
val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> {
    error("ExtendedColors not provided. Wrap the tree in QuietMetrixTheme.")
}
