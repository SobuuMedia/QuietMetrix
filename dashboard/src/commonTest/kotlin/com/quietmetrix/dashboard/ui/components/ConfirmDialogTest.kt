package com.quietmetrix.dashboard.ui.components

import com.quietmetrix.dashboard.theme.DarkExtendedColors
import com.quietmetrix.dashboard.theme.LightExtendedColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pure-logic coverage for [confirmButtonSpec], the danger-vs-default styling
 * selection and label wiring behind [ConfirmDialog] (dashboard-redesign
 * README > Interactions & Behavior > "Confirmation dialogs": "Cancel /
 * colored confirm button (coral for destructive, lime otherwise)"). No
 * screenshot golden is provided for this task (see task-11 brief), so this
 * logic test plus a manual visual check against the HTML prototype's confirm
 * pattern is the gate.
 */
class ConfirmDialogTest {

    @Test
    fun dangerSelectsCoralAndKeepsTheConfirmLabel() {
        val spec = confirmButtonSpec(confirmLabel = "Delete app", danger = true, colors = DarkExtendedColors)
        assertEquals("Delete app", spec.label)
        assertEquals(DarkExtendedColors.coral, spec.containerColor)
    }

    @Test
    fun defaultSelectsLimeAndKeepsTheConfirmLabel() {
        val spec = confirmButtonSpec(confirmLabel = "Ship it", danger = false, colors = DarkExtendedColors)
        assertEquals("Ship it", spec.label)
        assertEquals(DarkExtendedColors.lime, spec.containerColor)
    }

    @Test
    fun colorSelectionNeverMixesUpTheTwoVariants() {
        val danger = confirmButtonSpec("Remove", danger = true, colors = DarkExtendedColors)
        val default = confirmButtonSpec("Remove", danger = false, colors = DarkExtendedColors)
        assertNotEquals(danger.containerColor, default.containerColor)
    }

    @Test
    fun fixedAccentsDoNotTheme_SwapBetweenLightAndDark() {
        // lime/coral are fixed accents (identical in both themes per
        // theme/Color.kt) — the selection must reuse that same literal value
        // rather than accidentally pick a theme-swapping ColorScheme role.
        assertEquals(
            LightExtendedColors.coral,
            confirmButtonSpec("x", danger = true, colors = LightExtendedColors).containerColor,
        )
        assertEquals(
            DarkExtendedColors.coral,
            confirmButtonSpec("x", danger = true, colors = DarkExtendedColors).containerColor,
        )
        assertEquals(LightExtendedColors.coral, DarkExtendedColors.coral)
    }
}
