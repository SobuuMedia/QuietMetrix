package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_dismiss
import com.quietmetrix.dashboard.resources.ic_close
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.viewmodel.Toast
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/**
 * Renders [toast] as the bottom-center notification card the dashboard-redesign spec describes
 * (design_handoff_dashboard_redesign/README.md > Interactions & Behavior > "Toasts":
 * "bottom-center, auto-dismiss ~5s, optional action button, close (✕) button. Used for every
 * completed action ... and for refresh/error feedback").
 *
 * Positioning (bottom-center within the screen) and the ~5s auto-dismiss timer are *not* this
 * composable's job — they live on [com.quietmetrix.dashboard.viewmodel.DashboardViewModel]
 * (`showToast`/`dismissToast`, see `ToastQueueTest`) and whatever screen/shell hosts this
 * component is expected to place it with e.g. `Box(Modifier.align(Alignment.BottomCenter))`.
 * This composable only renders the card itself and reports the close-button tap via [onDismiss].
 *
 * [Toast.err] renders the emoji/title in [ExtendedColors.coral] (the spec's fixed accent for
 * "errors/destructive actions" — see `theme/Color.kt`'s [LocalExtendedColors] KDoc) instead of
 * the normal on-surface color; everything else about the card's layout is identical for both.
 *
 * @param toast the toast content to show.
 * @param onDismiss invoked when the ✕ close button is pressed; the caller is expected to call
 * [com.quietmetrix.dashboard.viewmodel.DashboardViewModel.dismissToast] in response, the same way
 * [ConfirmDialog]'s `onDismiss` callers clear whatever state made that dialog visible.
 */
@Composable
fun Toast(toast: Toast, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val accentColor = if (toast.err) extended.coral else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = QmShapeTokens.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        modifier = modifier.widthIn(max = 420.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EmojiVector(toast.emoji, Modifier.size(22.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(toast.title, style = MaterialTheme.typography.titleSmall, color = accentColor)
                toast.body?.let { body ->
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val actionLabel = toast.actionLabel
            val action = toast.action
            if (actionLabel != null && action != null) {
                Pill(text = actionLabel, variant = PillVariant.Lime, onClick = action)
            }

            IconButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
