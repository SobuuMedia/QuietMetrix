package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import org.jetbrains.compose.resources.painterResource

/**
 * Where a [Tooltip] bubble renders relative to its anchor (the ⓘ icon):
 * [Above] is the spec's default, [Below] is the flip used when there isn't
 * enough headroom near the top of the viewport (see [tooltipPlacement]).
 */
enum class TooltipPlacement { Above, Below }

/**
 * Vertical breathing room (px) kept between the anchor and the tooltip
 * bubble's nearest edge. The design hand-off's Spacing & shape section
 * gives no tooltip-specific clearance value, so this is a documented
 * judgment call rather than an invented one: 8px is the README's smallest
 * named spacing unit (matches [QmShapeTokens.chip]'s "9-10px" sibling scale
 * and the 8dp `MaterialTheme.shapes.small` radius already used throughout
 * this codebase for compact elements), so it reads as "a little clearance",
 * consistent with how small gaps are sized elsewhere here.
 */
private const val TOOLTIP_FLIP_GAP = 8f

/**
 * Decides whether a [Tooltip] bubble fits above its anchor or must flip
 * below it, per the dashboard-redesign spec's "positioned above by default,
 * flips below if too close to the top of the viewport" rule
 * (design_handoff_dashboard_redesign/README.md > Interactions & Behavior >
 * "Tooltips").
 *
 * "Too close to the top" is defined here as: an above-placed tooltip bubble
 * of [tooltipHeight], plus a [gap] of breathing room, would clip off the
 * viewport's top edge (y = 0) if placed above [anchorY]. That is the only
 * condition that can make an above-placed tooltip get cut off at the top,
 * so this intentionally takes no viewport-height parameter — the spec's
 * flip rule is a top-edge-only check (nothing in the README asks for a
 * symmetrical bottom-edge flip when the tooltip is already below), and a
 * dimension the decision never reads would only read as a design smell to
 * a future maintainer wondering what it's for.
 *
 * @param anchorY the anchor's (the ⓘ icon's) y position in px, measured
 * from the top of the viewport/window.
 * @param tooltipHeight the tooltip bubble's own measured height in px.
 * @param gap minimum clearance (px) to keep between the anchor and the
 * tooltip's nearest edge; defaults to [TOOLTIP_FLIP_GAP].
 */
fun tooltipPlacement(
    anchorY: Float,
    tooltipHeight: Float,
    gap: Float = TOOLTIP_FLIP_GAP,
): TooltipPlacement {
    val spaceNeededAbove = tooltipHeight + gap
    return if (anchorY >= spaceNeededAbove) TooltipPlacement.Above else TooltipPlacement.Below
}

/**
 * [PopupPositionProvider] that places the tooltip bubble directly above or
 * below [anchorBounds] per [tooltipPlacement], horizontally centered on the
 * anchor (clamped so the bubble never renders outside [windowSize]). Doing
 * the placement math against the anchor's real screen bounds and the
 * bubble's real measured size (both of which only [Popup] can supply) is
 * why this provider exists as a separate class instead of inlining
 * [tooltipPlacement]'s result directly as a `Modifier.offset` — a
 * `Modifier`-based offset is relative to the composable's own layout slot,
 * which would make the bubble participate in layout and push surrounding
 * content around; [Popup] renders it as a true overlay, matching the
 * spec's "fixed-position tooltip" wording (README's state-model section:
 * "`tip`: currently-hovered tooltip target + position").
 */
private class TooltipPositionProvider(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val placement = tooltipPlacement(
            anchorY = anchorBounds.top.toFloat(),
            tooltipHeight = popupContentSize.height.toFloat(),
            gap = gap.toFloat(),
        )
        val y = when (placement) {
            TooltipPlacement.Above -> anchorBounds.top - popupContentSize.height - gap
            TooltipPlacement.Below -> anchorBounds.bottom + gap
        }
        val xCentered = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = xCentered.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/**
 * The spec's ⓘ info icon: hovering it reveals a small plain-language
 * explanation bubble (design_handoff_dashboard_redesign/README.md >
 * Typography: "the technical term available via an ⓘ tooltip"; Interactions
 * & Behavior > "Tooltips": "every ⓘ icon shows a fixed-position tooltip on
 * hover with a plain-language explanation of that metric; positioned above
 * by default, flips below if too close to the top of the viewport").
 *
 * This is distinct from the existing click-to-open "tab info" dialogs in
 * `DashboardScreen`/`FunnelsScreen`/etc. (`ic_info` + `tab_info_*` strings,
 * opened via click) — those are per-screen explainer dialogs; this is the
 * per-metric hover bubble the spec describes separately. It reuses the same
 * `ic_info` glyph since both are "ⓘ" per the spec's iconography, but the
 * trigger (hover vs. click) and content (one short sentence vs. a longer
 * screen explainer) differ, so this is its own composable rather than a
 * variant of those dialogs.
 *
 * Hover detection uses `collectIsHoveredAsState()` off a
 * [MutableInteractionSource] via `Modifier.hoverable` — the same primitive
 * [KpiCard] already uses for its brighten-on-hover state and
 * `table/DataTable.kt` uses for row highlighting. It is a real pointer-hover
 * signal on desktop/web (wasmJs) targets; on touch-only platforms there is
 * no hover pointer, so it simply never fires and the icon renders inert
 * (not broken) — the same degradation [handCursor] already accepts for
 * its own hover-only affordance, needing no platform-specific branching
 * here either.
 *
 * @param explanation the plain-language text shown in the bubble.
 * @param modifier applied to the ⓘ icon itself.
 */
@Composable
fun Tooltip(explanation: String, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Icon(
        painter = painterResource(Res.drawable.ic_info),
        contentDescription = explanation,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(2.dp).hoverable(interactionSource),
    )

    if (hovered) {
        Popup(popupPositionProvider = TooltipPositionProvider(gap = TOOLTIP_FLIP_GAP.toInt())) {
            Surface(
                shape = QmShapeTokens.chip,
                color = extended.chipFill,
                border = BorderStroke(1.dp, extended.hairline),
                modifier = Modifier.widthIn(max = 240.dp),
            ) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}
