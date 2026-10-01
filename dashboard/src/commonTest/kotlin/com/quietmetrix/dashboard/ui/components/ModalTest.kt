package com.quietmetrix.dashboard.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-logic coverage for [modalFooterSlots], the leading/secondary/primary button
 * layout behind [ModalShell]'s footer (dashboard-redesign README > Interactions &
 * Behavior > "Modals": "header with title + ✕, Cancel/primary footer"; the 3-step
 * experiment wizard's "← Back ... Cancel Next →" row is the same function with a
 * [ModalFooterAction] in the `leading` slot). No screenshot golden is provided for
 * this task (per the task-14 brief), so this logic test plus a manual visual check
 * against the HTML prototype's modal footers is the gate.
 */
class ModalTest {

    private fun action(label: String, emphasis: ModalFooterEmphasis = ModalFooterEmphasis.Secondary) =
        ModalFooterAction(label = label, emphasis = emphasis, onClick = {})

    @Test
    fun cancelAndPrimaryOnlyGetASingleLeadingSpacer() {
        // The plain "New app"/"New token"/"New funnel" modals (no back button): the HTML
        // prototype packs Cancel+primary to the row's trailing edge via `justify-content:flex-end`
        // (QuietMetrix Prototype.dc.html:683) — modelled here as one spacer before the first slot.
        val cancel = action("Cancel")
        val primary = action("Create app", ModalFooterEmphasis.Primary)
        val slots = modalFooterSlots(leading = null, secondary = cancel, primary = primary)

        assertEquals(2, slots.size)
        assertEquals(cancel, slots[0].action)
        assertTrue(slots[0].spacerBefore)
        assertEquals(primary, slots[1].action)
        assertTrue(!slots[1].spacerBefore)
    }

    @Test
    fun primaryOnlyStillGetsPushedToTheTrailingEdge() {
        val primary = action("Done", ModalFooterEmphasis.Primary)
        val slots = modalFooterSlots(leading = null, secondary = null, primary = primary)

        assertEquals(1, slots.size)
        assertEquals(primary, slots[0].action)
        assertTrue(slots[0].spacerBefore)
    }

    @Test
    fun backCancelAndPrimaryMatchTheWizardFooter() {
        // The 3-step experiment wizard's footer (QuietMetrix Prototype.dc.html:740):
        // "← Back" sits alone at the leading edge, then one flex:1 spacer, then
        // Cancel and Next packed together at the trailing edge with no gap between them.
        val back = action("← Back")
        val cancel = action("Cancel")
        val next = action("Next →", ModalFooterEmphasis.Primary)
        val slots = modalFooterSlots(leading = back, secondary = cancel, primary = next)

        assertEquals(3, slots.size)
        assertEquals(back, slots[0].action)
        assertTrue(!slots[0].spacerBefore)
        assertEquals(cancel, slots[1].action)
        assertTrue(slots[1].spacerBefore)
        assertEquals(next, slots[2].action)
        assertTrue(!slots[2].spacerBefore)
    }

    @Test
    fun backAndPrimaryWithNoSecondaryStillSeparates() {
        val back = action("← Back")
        val next = action("Next →", ModalFooterEmphasis.Primary)
        val slots = modalFooterSlots(leading = back, secondary = null, primary = next)

        assertEquals(2, slots.size)
        assertEquals(back, slots[0].action)
        assertTrue(!slots[0].spacerBefore)
        assertEquals(next, slots[1].action)
        assertTrue(slots[1].spacerBefore)
    }

    @Test
    fun actionIdentityAndEmphasisAreNeverMixedUp() {
        val cancel = action("Cancel", ModalFooterEmphasis.Secondary)
        val primary = action("Ship it", ModalFooterEmphasis.Primary)
        val slots = modalFooterSlots(leading = null, secondary = cancel, primary = primary)

        assertEquals(ModalFooterEmphasis.Secondary, slots[0].action.emphasis)
        assertEquals(ModalFooterEmphasis.Primary, slots[1].action.emphasis)
        assertEquals("Cancel", slots[0].action.label)
        assertEquals("Ship it", slots[1].action.label)
    }

    @Test
    fun onClickWiringInvokesTheRightCallback() {
        var cancelled = false
        var confirmed = false
        val cancel = ModalFooterAction("Cancel", ModalFooterEmphasis.Secondary) { cancelled = true }
        val primary = ModalFooterAction("Create app", ModalFooterEmphasis.Primary) { confirmed = true }
        val slots = modalFooterSlots(leading = null, secondary = cancel, primary = primary)

        slots[0].action.onClick()
        assertTrue(cancelled)
        assertTrue(!confirmed)

        slots[1].action.onClick()
        assertTrue(confirmed)
    }
}
