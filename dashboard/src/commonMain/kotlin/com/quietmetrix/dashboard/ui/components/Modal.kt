package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_close
import com.quietmetrix.dashboard.resources.ic_close
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Visual emphasis a [ModalFooterAction] renders with: [Secondary] is a
 * chip-filled [TextButton] (Cancel, "← Back", "Done"), [Primary] is a
 * lime-filled [Button] (Create/Save/Next, the one call-to-action a footer
 * row always has). Mirrors the HTML prototype's two button styles
 * (`background:var(--chip)` vs. `background:#c8f25a`, QuietMetrix
 * Prototype.dc.html:683).
 */
enum class ModalFooterEmphasis { Secondary, Primary }

/**
 * One footer button a [ModalShell] caller wants rendered: its [label],
 * [emphasis], and tap handler. Kept as plain data (not a `@Composable`
 * lambda) so the leading/secondary/primary wiring in [modalFooterSlots] is
 * unit-testable without a Compose runtime — the same pattern
 * [confirmButtonSpec] established for `ConfirmDialog`'s button styling.
 */
data class ModalFooterAction(
    val label: String,
    val emphasis: ModalFooterEmphasis,
    val onClick: () -> Unit,
)

/**
 * One rendered slot in a [ModalFooter] row: [action] plus whether an
 * expanding spacer precedes it. [spacerBefore] is where the HTML
 * prototype's `<span style="flex:1">` sits (QuietMetrix
 * Prototype.dc.html:740) — true pushes this slot (and everything after it)
 * to the row's trailing edge, leaving anything earlier pinned to the
 * leading edge.
 */
data class ModalFooterSlot(val action: ModalFooterAction, val spacerBefore: Boolean)

/**
 * Pure function mapping a modal footer's up-to-three actions to the ordered
 * list of [ModalFooterSlot]s [ModalFooter] renders. Kept outside Compose so
 * this wiring is unit-testable without a Compose runtime (see `ModalTest`).
 *
 * Models both footer shapes the dashboard-redesign spec uses (README >
 * Interactions & Behavior > "Modals"):
 * - Plain Cancel/primary pair (New app/token/funnel modals, [leading] null):
 *   one spacer precedes the first trailing action, packing both to the
 *   row's trailing edge — the HTML prototype's `justify-content:flex-end`
 *   (QuietMetrix Prototype.dc.html:683).
 * - The 3-step experiment wizard's "← Back ... Cancel Next →" row
 *   ([leading] = the Back action once past step 1): [leading] renders
 *   alone at the leading edge with no spacer before it, then exactly one
 *   (expanding) spacer, then [secondary] and [primary] rendered back-to-back
 *   at the trailing edge — no *spacer* slot between them, just [ModalFooter]'s
 *   fixed 10dp `Arrangement.spacedBy` gap, the same fixed gap every adjacent
 *   pair in the row gets (matching the HTML prototype's own `gap:10px`,
 *   QuietMetrix Prototype.dc.html:740).
 *
 * There is always exactly one spacer whenever at least one trailing action
 * exists ([secondary] and/or [primary]), placed immediately before the
 * first trailing action — never before [leading] and never between
 * [secondary] and [primary] (those two only ever get the row's fixed
 * 10dp gap, never an expanding spacer).
 *
 * @param leading optional action pinned to the row's leading edge (e.g.
 * "← Back"); null when the modal has no multi-step flow.
 * @param secondary optional secondary trailing action (e.g. "Cancel",
 * "Done"); null when the modal has no dismiss affordance beyond the
 * header's ✕.
 * @param primary the modal's primary action (e.g. "Create app", "Next →"),
 * always present and always the last slot.
 */
fun modalFooterSlots(
    leading: ModalFooterAction?,
    secondary: ModalFooterAction?,
    primary: ModalFooterAction,
): List<ModalFooterSlot> {
    val slots = mutableListOf<ModalFooterSlot>()
    if (leading != null) {
        slots += ModalFooterSlot(leading, spacerBefore = false)
    }
    val trailing = listOfNotNull(secondary, primary)
    trailing.forEachIndexed { index, trailingAction ->
        slots += ModalFooterSlot(trailingAction, spacerBefore = index == 0)
    }
    return slots
}

/**
 * Renders a [ModalShell] footer row from [leading]/[secondary]/[primary]
 * via [modalFooterSlots] — the typical way a [ModalShell] caller fills its
 * `footer` slot, saving each call site from hand-building the Row/Spacer
 * layout itself. Callers whose footer doesn't fit this Cancel/primary (or
 * Back/Cancel/primary) shape can still pass their own composable directly
 * to [ModalShell]'s `footer` parameter instead of using this.
 *
 * @param leading see [modalFooterSlots].
 * @param secondary see [modalFooterSlots].
 * @param primary see [modalFooterSlots].
 */
@Composable
fun ModalFooter(
    leading: ModalFooterAction? = null,
    secondary: ModalFooterAction? = null,
    primary: ModalFooterAction,
    modifier: Modifier = Modifier,
) {
    val extended = LocalExtendedColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modalFooterSlots(leading, secondary, primary).forEach { slot ->
            if (slot.spacerBefore) {
                Spacer(modifier = Modifier.weight(1f))
            }
            when (slot.action.emphasis) {
                ModalFooterEmphasis.Secondary -> TextButton(
                    onClick = slot.action.onClick,
                    colors = ButtonDefaults.textButtonColors(containerColor = extended.chipFill),
                    modifier = Modifier.handCursor(),
                ) { Text(slot.action.label) }

                ModalFooterEmphasis.Primary -> Button(
                    onClick = slot.action.onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = extended.lime, contentColor = extended.onLime),
                    modifier = Modifier.handCursor(),
                ) { Text(slot.action.label) }
            }
        }
    }
}

/**
 * Shared chrome for the dashboard-redesign spec's centered modal dialogs
 * (design_handoff_dashboard_redesign/README.md > Interactions & Behavior >
 * "Modals": "centered overlay dialogs for New app, New token, New funnel
 * ..., New experiment (3-step wizard ...), and the generic confirm dialog.
 * All share the same card chrome (24px radius, header with title + ✕,
 * Cancel/primary footer)"). This is the general-purpose container later
 * tasks (new app/token/funnel modals, the experiment wizard) build their
 * specific form/wizard content on top of — unlike [ConfirmDialog], which
 * is a narrower, already-built special case of this same chrome (a fixed
 * emoji+title+body+Cancel/colored-confirm shape) and is intentionally left
 * as-is rather than refactored onto this shell, per this task's brief.
 *
 * Built on Material3's [AlertDialog] — the same "let the framework own
 * what it already owns" choice [ConfirmDialog] made (scrim, outside-click/
 * ESC/back dismissal, platform positioning) — rather than a hand-rolled
 * [androidx.compose.ui.window.Dialog]. [AlertDialog]'s `title`/`text`/
 * `confirmButton` slots are plain composable slots, not fixed single
 * buttons, so the header's title+✕ row goes in `title`, [content] goes in
 * `text`, and the whole [footer] row (which may hold up to three buttons —
 * see [modalFooterSlots]) goes in `confirmButton`; `dismissButton` is left
 * unset since [footer] already renders Cancel/Back itself.
 *
 * @param title the modal's header title.
 * @param onDismiss invoked when the ✕ is pressed, the scrim is clicked, or
 * ESC/back is pressed.
 * @param footer the footer row; typically a [ModalFooter] call, but left
 * as a free composable slot so content with no standard Cancel/primary
 * shape isn't forced into it.
 * @param content the modal's body — a form, a wizard step, a list, or
 * whatever the specific modal needs; this shell makes no assumption about
 * it beyond rendering it between the header and the footer.
 */
@Composable
fun ModalShell(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QmShapeTokens.modal,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = content,
        confirmButton = footer,
    )
}
