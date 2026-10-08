package com.quietmetrix.dashboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_compare
import com.quietmetrix.dashboard.resources.state_loading
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/**
 * Fraction a [KpiCard]'s container color is blended towards white while
 * hovered. Compose has no literal CSS `filter: brightness()`, so this
 * approximates the design hand-off's `filter: brightness(1.12)` hover rule
 * (README > Interactions > "Hover/press states") by lightening the surface
 * color 12% towards white instead of scaling every channel — visually very
 * close for the dark, low-saturation `--sf` surface this spec uses, and
 * reads as a well-defined constant rather than a magic number.
 */
private const val HOVER_BRIGHTEN_FRACTION = 0.12f

/**
 * Scale applied to a [KpiCard] while pressed, matching the design
 * hand-off's literal `transform: scale(.97)` press rule used on every
 * clickable card/button in the prototype.
 */
private const val PRESS_SCALE = 0.97f

/**
 * Elevation (shadow lift) a [KpiCard] gains while hovered, approximating
 * the hand-off's `box-shadow: 0 12px 30px rgba(0,0,0,.18)` hover rule for
 * "number cards" (README > Interactions: "number cards additionally lift
 * with a shadow on hover"). Passed straight into
 * `CardDefaults.cardElevation(hoveredElevation = HOVER_ELEVATION)` —
 * deliberately *not* driven through a manual `animateDpAsState` of our
 * own: Material3's `Card(onClick = ...)` already animates its elevation
 * off the same `interactionSource` internally (see [KpiCard]'s KDoc), so
 * adding a second, independently-animated `Dp` on top of it would have
 * one animation chasing a target that is itself still moving — two
 * competing animations instead of one. Letting Material3 own the one
 * elevation animation and only supplying the hover/press/drag targets is
 * the idiom later components should copy for elevation specifically; the
 * scale and color animations below remain manual because Material3 has no
 * built-in equivalent for either.
 */
private val HOVER_ELEVATION = 12.dp

/**
 * A single vitals metric tile: label, big number, optional delta pill,
 * one-line caption, and an optional lavender "was N (last week)" note shown
 * when Compare mode is on (design_handoff_dashboard_redesign/README.md >
 * Overview: "4 vital cards in a responsive grid ... each has label + ⓘ, big
 * number, delta pill (lime if positive, neutral chip otherwise), one-line
 * human caption ... When Compare is on, a lavender 'was N (last week)' note
 * appears under the caption").
 *
 * Pass [onClick] to make the card open the drill-down drawer (README:
 * "Clickable → opens the drill-down drawer"); this also turns on the
 * hover/press idiom documented on [HOVER_BRIGHTEN_FRACTION]/[PRESS_SCALE]/
 * [HOVER_ELEVATION] — a card with no [onClick] renders statically, since a
 * non-interactive tile shouldn't hint at hover/press affordances it doesn't
 * have. This hover/press idiom is the first use of this pattern in the
 * dashboard-redesign plan; later clickable components (nav pills,
 * experiment cards, etc.) should copy it rather than invent a new one. It
 * is a single shared [MutableInteractionSource], passed to
 * `Card(onClick = ..., interactionSource = ...)`, that drives:
 * - **scale** (press → [PRESS_SCALE]): manual, via `animateFloatAsState`
 *   reading `interactionSource.collectIsPressedAsState()` — Material3 has
 *   no built-in scale-on-press.
 * - **container color** (hover → brighten by [HOVER_BRIGHTEN_FRACTION]):
 *   manual, via `animateColorAsState` reading
 *   `interactionSource.collectIsHoveredAsState()` — Material3 has no
 *   built-in brighten-on-hover.
 * - **elevation/shadow lift** (hover → [HOVER_ELEVATION]): *not* manually
 *   animated — the same `interactionSource` is handed straight to `Card`,
 *   which already animates `CardDefaults.cardElevation`'s
 *   `hoveredElevation` off it internally. Wrapping that in a second,
 *   independent `animateDpAsState` would be two animations racing each
 *   other (one chasing a target that is itself still moving); see
 *   [HOVER_ELEVATION]'s KDoc for the full rationale. **This is the
 *   detail to get right when copying this idiom**: manually animate only
 *   the properties Material3 doesn't already animate for you off
 *   `interactionSource` (scale, color); let the framework animate the
 *   ones it owns (elevation on a `Card`).
 *
 * @param label short metric name (e.g. "People").
 * @param value the big headline number, already formatted by the caller.
 * @param sub optional one-line human caption shown under the value (e.g.
 * "About a full cinema, every day 🍿"); also used as the sole sub-line for
 * callers (Experiments/Funnels KPIs) that don't use [delta]/[compareNote].
 * @param delta optional delta text (e.g. "▲ 18%"), rendered as a [Pill]
 * next to [value]; omit to show no delta pill at all.
 * @param deltaPositive whether [delta] renders as the positive/lime pill
 * variant (`PillVariant.Lime`) or the neutral chip variant
 * (`PillVariant.Neutral`) — ignored when [delta] is null.
 * @param compareNote optional "was N (last week)" text shown in a lavender
 * note under the caption when Compare mode is on; omit (null) when Compare
 * is off.
 * @param onClick opens the drill-down drawer for this metric; when null the
 * card is a static, non-interactive tile with no hover/press styling.
 * @param highlighted renders the card filled with [LocalExtendedColors]'
 * `lime`/`onLime` tokens instead of the default surface/hairline-border
 * treatment, for the one "primary/positive" stat a row calls out — e.g. the
 * Funnels screen's "Success rate" card (design handoff: "3 stat cards
 * (Started / Made it to the end / Success rate — the last one lime-filled)").
 * Same token pairing [Pill]'s `PillVariant.Lime` already uses, so a
 * lime-filled [KpiCard] and a lime [Pill] never disagree about the color.
 * Hover/press still brightens the lime container towards white exactly like
 * the default surface color does — only the base container/text colors and
 * the (now omitted) hairline border change.
 */
@Composable
fun KpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    sub: String? = null,
    delta: String? = null,
    deltaPositive: Boolean = false,
    deltaIcon: DrawableResource? = null,
    compareNote: String? = null,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
) {
    val extended = LocalExtendedColors.current
    val shape = MaterialTheme.shapes.extraLarge
    val border = if (highlighted) null else BorderStroke(1.dp, extended.hairline)
    val restingContainer = if (highlighted) extended.lime else MaterialTheme.colorScheme.surface

    val body: @Composable () -> Unit = {
        KpiCardContent(
            label = label,
            leadingIcon = leadingIcon,
            value = value,
            sub = sub,
            delta = delta,
            deltaPositive = deltaPositive,
            deltaIcon = deltaIcon,
            compareNote = compareNote,
            highlighted = highlighted,
        )
    }

    if (onClick != null) {
        // Hover/press idiom (see this function's KDoc for the full
        // rationale): a single shared MutableInteractionSource is handed
        // to Card, which animates its own elevation off it internally
        // (hoveredElevation below); we only manually animate the two
        // properties Material3 doesn't own — press-scale and
        // hover-brighten — reading interaction state from that same
        // source so all three stay in lockstep with one real gesture.
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val hovered by interactionSource.collectIsHoveredAsState()

        val scale by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, label = "kpiCardScale")
        val containerColor by animateColorAsState(
            if (hovered) lerp(restingContainer, Color.White, HOVER_BRIGHTEN_FRACTION) else restingContainer,
            label = "kpiCardBrighten",
        )

        Card(
            onClick = onClick,
            modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale).handCursor(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                hoveredElevation = HOVER_ELEVATION,
                pressedElevation = 0.dp,
            ),
            border = border,
            interactionSource = interactionSource,
            content = { body() },
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = restingContainer),
            border = border,
        ) {
            body()
        }
    }
}

/**
 * Shared label/value/delta/caption/compare-note layout for [KpiCard],
 * factored out so the clickable and static `Card` branches render
 * identical content.
 */
@Composable
private fun KpiCardContent(
    label: String,
    leadingIcon: DrawableResource?,
    value: String,
    sub: String?,
    delta: String?,
    deltaPositive: Boolean,
    deltaIcon: DrawableResource?,
    compareNote: String?,
    highlighted: Boolean = false,
) {
    val extended = LocalExtendedColors.current
    val mutedColor = if (highlighted) extended.onLime.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (highlighted) extended.onLime else Color.Unspecified
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leadingIcon != null) Icon(
                painterResource(leadingIcon), contentDescription = null,
                tint = Color.Unspecified, modifier = Modifier.size(22.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(value, style = MaterialTheme.typography.displaySmall, color = valueColor)
            if (delta != null) {
                Pill(
                    text = delta,
                    variant = if (deltaPositive) PillVariant.Lime else PillVariant.Neutral,
                    leadingIcon = deltaIcon,
                )
            }
        }
        if (sub != null) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
            )
        }
        if (compareNote != null) {
            Surface(
                color = extended.lavender.copy(alpha = 0.18f),
                shape = QmShapeTokens.chip,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Icon(painterResource(Res.drawable.ic_compare), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Text(compareNote, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Loading placeholder shaped like a [KpiCard] so the layout doesn't jump when
 * data arrives. Flat tonal fill — cheap on wasmJs, no animation overhead.
 */
@Composable
fun KpiSkeleton(modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, extended.hairline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBox(Modifier.width(80.dp).height(14.dp))
            SkeletonBox(Modifier.width(120.dp).height(28.dp))
        }
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val loadingLabel = stringResource(Res.string.state_loading)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = modifier.semantics { contentDescription = loadingLabel },
    ) {}
}
