package com.quietmetrix.dashboard.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material3 corner-radius scale for the dashboard-redesign spec
 * (design_handoff_dashboard_redesign/README.md > Design System > Spacing &
 * shape). Material3's `Shapes` has exactly 5 slots, so only the values that
 * map cleanly onto extraSmall..extraLarge live here:
 * - `extraSmall`/`small` (4/8dp): below the spec's named scale, kept as-is
 *   for the smallest affordances (e.g. progress-bar fill, tiny indicators).
 * - `medium`/`large` (12/16dp): buttons/inputs/inner tiles per the Design
 *   Tokens summary ("12-16px (buttons/inputs/inner tiles)").
 * - `extraLarge` (20dp): card radius ("Card radius 20px" in Spacing &
 *   shape; "20-24px (cards/modals/drawers)" in Design Tokens).
 *
 * Pill/chip/modal/drawer radii have no Material3 slot of their own and live
 * on [QmShapeTokens] instead.
 */
val QmShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * Named corner-radius constants from the dashboard-redesign spec that fall
 * outside Material3's 5-slot [Shapes] scale (pill, chip, modal, drawer).
 * Named `QmShapeTokens` — not `QmShapes` — because [QmShapes] above already
 * claims that identifier for the Material3 `Shapes` instance; reusing the
 * name for a second top-level declaration would collide.
 *
 * Source: design_handoff_dashboard_redesign/README.md > Design System >
 * Spacing & shape ("Card radius 20px; small chips/pills 999px (full pill)
 * or 9-12px for rectangular chips; modal/drawer radius 24px/20px"),
 * reconciled with the Design Tokens summary near the end of the README
 * ("999px (pills/avatars), 20-24px (cards/modals/drawers), 12-16px
 * (buttons/inputs/inner tiles), 9-10px (small chips)"). The two sections
 * agree on pill/card/modal/drawer; they differ slightly on the chip range
 * (9-12px vs. 9-10px) — 10dp satisfies both, so that's the value used here.
 */
object QmShapeTokens {
    /** Full pill shape (999dp) for small chips/pills/avatars. */
    val pill = RoundedCornerShape(999.dp)

    /** Rectangular chip radius (10dp; within both the 9-12px and 9-10px README ranges). */
    val chip = RoundedCornerShape(10.dp)

    /** Card radius (20dp), aliasing [QmShapes.extraLarge] for call sites that reach for shapes by name. */
    val card = RoundedCornerShape(20.dp)

    /** Modal radius (24dp), the upper end of the "20-24px (cards/modals/drawers)" range. */
    val modal = RoundedCornerShape(24.dp)

    /** Drawer radius (20dp); the README gives modal/drawer as "24px/20px", so drawer coincides with card. */
    val drawer = RoundedCornerShape(20.dp)
}
