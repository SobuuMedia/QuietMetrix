package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/**
 * Visual treatment for [Pill] — the four rounded-capsule variants the
 * dashboard-redesign spec reuses across the header, vitals cards and
 * filters (design_handoff_dashboard_redesign/README.md > Header /
 * Overview / Flow sections). Each case is a fixed (bg, text) pairing; see
 * [Pill] for how they're applied.
 */
sealed interface PillVariant {

    /**
     * Selected nav tab. Spec: "Active pill is filled solid (`--ink` bg /
     * `--bg` text)" — reuses `MaterialTheme.colorScheme.primary`/`onPrimary`
     * directly rather than [LocalExtendedColors], since `theme/Color.kt`
     * already maps that exact rule onto the primary role (see its
     * `DarkColors`/`LightColors` KDoc) and themes correctly as a result.
     */
    data object NavActive : PillVariant

    /**
     * Subtle informational capsule — the non-positive half of a delta pill,
     * an AI suggestion chip, a status pill. Spec: `--chip` subtle fill with
     * secondary text, theme-swapping like the rest of the neutral palette.
     */
    data object Neutral : PillVariant

    /**
     * Positive-delta / success highlight. Spec: fixed lime accent, "primary
     * CTA, positive delta, success" — same literal color in both themes.
     */
    data object Lime : PillVariant

    /**
     * Active filter chip (e.g. "Showing only iOS ✕"). Spec: fixed lavender
     * accent — same literal color in both themes.
     */
    data object Lavender : PillVariant
}

/**
 * A small rounded-capsule label used for nav tabs, delta/status pills and
 * filter chips throughout the dashboard-redesign spec. Shape and color are
 * driven entirely by [variant]; all four variants share the same fully
 * rounded [QmShapeTokens.pill] (999dp) silhouette the spec's screenshots
 * show for each of them, so no per-variant shape override exists.
 *
 * Pass [onClick] to make the pill tappable (an active nav tab, a
 * dismissible filter chip); omit it for a purely informational pill such as
 * a delta pill inside a KPI card.
 */
@Composable
fun Pill(
    text: String,
    variant: PillVariant,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: DrawableResource? = null,
    trailingIcon: DrawableResource? = null,
    colorfulIcon: Boolean = false,
    enabled: Boolean = true,
) {
    val extended = LocalExtendedColors.current
    val (background, foreground) = when (variant) {
        PillVariant.NavActive -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        PillVariant.Neutral -> extended.chipFill to MaterialTheme.colorScheme.onSurfaceVariant
        PillVariant.Lime -> extended.lime to extended.onLime
        PillVariant.Lavender -> extended.lavender to extended.onLime
    }

    val label: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (leadingIcon != null) Icon(painterResource(leadingIcon), contentDescription = null,
                tint = if (colorfulIcon) androidx.compose.ui.graphics.Color.Unspecified else foreground,
                modifier = Modifier.size(18.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = foreground)
            if (trailingIcon != null) Icon(painterResource(trailingIcon), contentDescription = null,
                tint = foreground, modifier = Modifier.size(14.dp))
        }
    }

    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, color = background, shape = QmShapeTokens.pill,
            modifier = modifier.then(if (enabled) Modifier.handCursor() else Modifier)) {
            label()
        }
    } else {
        Surface(color = background, shape = QmShapeTokens.pill, modifier = modifier) {
            label()
        }
    }
}
