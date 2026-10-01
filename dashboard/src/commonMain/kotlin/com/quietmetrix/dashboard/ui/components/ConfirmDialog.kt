package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.confirm_dialog_cancel
import com.quietmetrix.dashboard.theme.ExtendedColors
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * The confirm button's label and fill color for [ConfirmDialog], resolved
 * from the [danger] flag. Pulled out of the composable as a pure function so
 * the danger-vs-default color selection and label wiring are unit-testable
 * without a Compose runtime (see `ConfirmDialogTest`).
 */
internal data class ConfirmDialogButtonSpec(val label: String, val containerColor: Color)

/**
 * Resolves [ConfirmDialogButtonSpec] for [ConfirmDialog]: [confirmLabel]
 * passes through unchanged, and the fill color is [ExtendedColors.coral] for
 * destructive actions or [ExtendedColors.lime] otherwise (dashboard-redesign
 * README > Interactions & Behavior > "Confirmation dialogs": "Cancel /
 * colored confirm button (coral for destructive, lime otherwise)"). Both are
 * fixed accents that hold the same literal value in light and dark — see
 * `theme/Color.kt`'s [ExtendedColors] KDoc.
 */
internal fun confirmButtonSpec(confirmLabel: String, danger: Boolean, colors: ExtendedColors): ConfirmDialogButtonSpec =
    ConfirmDialogButtonSpec(
        label = confirmLabel,
        containerColor = if (danger) colors.coral else colors.lime,
    )

/**
 * The dashboard-redesign spec's generic confirmation pattern: a large emoji,
 * a title, a one-sentence consequence, and a Cancel / colored-confirm button
 * pair (design_handoff_dashboard_redesign/README.md > Interactions &
 * Behavior > "Confirmation dialogs" — "used for anything destructive or
 * state-changing with consequences: regenerate/delete app, revoke token,
 * remove team member, ship/pause/keep experiment result"). Reused as-is by
 * every future destructive/state-changing action in the plan (funnel,
 * experiment, project, team and settings screens) rather than each one
 * hand-rolling its own dialog.
 *
 * Built on Material3's [AlertDialog] rather than a raw `Dialog` — the
 * codebase already has an established `AlertDialog` idiom for confirmations
 * (see `ui/screens/UsersScreen.kt`'s remove-user dialog), and `AlertDialog`
 * already owns the behavior a hand-rolled `Dialog` would have to
 * reimplement (scrim, outside-click/back dismissal, focus, platform
 * positioning) — the same "let the framework own what it already owns"
 * idiom [KpiCard] established for hover/press elevation. Only the confirm
 * button's fill color (coral/lime) and the 24dp modal radius are the
 * dashboard-redesign spec's own additions on top of that.
 *
 * No hover/press styling here (unlike [Pill]/[KpiCard]): this is a modal,
 * not a card, and the HTML prototype's confirm-dialog markup applies no
 * hover/press affordance to the dialog surface itself — only its buttons
 * do, and Material3's [Button]/[TextButton] already animate their own
 * ripple/elevation off their internal `interactionSource`, so nothing extra
 * is wired here.
 *
 * @param emoji large glyph shown above [title] (e.g. "🗑️", "🏆").
 * @param title short question naming the action (e.g. "Delete MyApp?").
 * @param body one-sentence plain-language consequence of confirming.
 * @param confirmLabel label for the confirm button (e.g. "Delete app",
 * "Ship it").
 * @param danger true for a destructive action (confirm button renders
 * coral); false for a positive/neutral one (confirm button renders lime).
 * @param onConfirm invoked when the confirm button is pressed; the caller
 * is responsible for also dismissing the dialog (clearing whatever state
 * made it visible).
 * @param onDismiss invoked when Cancel is pressed, or the dialog is
 * dismissed by clicking outside it / pressing back.
 */
@Composable
fun ConfirmDialog(
    emoji: String,
    title: String,
    body: String,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extended = LocalExtendedColors.current
    val buttonSpec = confirmButtonSpec(confirmLabel = confirmLabel, danger = danger, colors = extended)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QmShapeTokens.modal,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(emoji, fontSize = 44.sp)
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonSpec.containerColor,
                    contentColor = extended.onLime,
                ),
            ) { Text(buttonSpec.label) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.confirm_dialog_cancel)) }
        },
    )
}
