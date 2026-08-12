package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ErrorKind
import com.quietmetrix.dashboard.api.UiError
import com.quietmetrix.dashboard.api.toClipboardReport
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_copy_error_details
import com.quietmetrix.dashboard.resources.action_dismiss
import com.quietmetrix.dashboard.resources.action_retry
import com.quietmetrix.dashboard.resources.error_kind_conflict
import com.quietmetrix.dashboard.resources.error_kind_forbidden
import com.quietmetrix.dashboard.resources.error_kind_incompatible
import com.quietmetrix.dashboard.resources.error_kind_invalid
import com.quietmetrix.dashboard.resources.error_kind_network
import com.quietmetrix.dashboard.resources.error_kind_not_found
import com.quietmetrix.dashboard.resources.error_kind_rate_limited
import com.quietmetrix.dashboard.resources.error_kind_server
import com.quietmetrix.dashboard.resources.error_kind_session_expired
import com.quietmetrix.dashboard.resources.error_kind_unknown
import com.quietmetrix.dashboard.resources.ic_close
import com.quietmetrix.dashboard.resources.ic_copy
import com.quietmetrix.dashboard.resources.snackbar_error_details_copied
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Maps this [ErrorKind] to its localized, user-facing sentence — never the raw exception text. */
@Composable
fun ErrorKind.friendlyMessage(): String = when (this) {
    ErrorKind.Network -> stringResource(Res.string.error_kind_network)
    ErrorKind.SessionExpired -> stringResource(Res.string.error_kind_session_expired)
    ErrorKind.Forbidden -> stringResource(Res.string.error_kind_forbidden)
    ErrorKind.NotFound -> stringResource(Res.string.error_kind_not_found)
    ErrorKind.Conflict -> stringResource(Res.string.error_kind_conflict)
    ErrorKind.RateLimited -> stringResource(Res.string.error_kind_rate_limited)
    ErrorKind.Invalid -> stringResource(Res.string.error_kind_invalid)
    ErrorKind.ServerError -> stringResource(Res.string.error_kind_server)
    ErrorKind.Incompatible -> stringResource(Res.string.error_kind_incompatible)
    ErrorKind.Unknown -> stringResource(Res.string.error_kind_unknown)
}

/**
 * Full-width banner for a user-initiated-action failure (create project, invite, regenerate
 * key, ...). Only the friendly sentence is ever shown on screen — the technical detail is
 * copy-only, via the trailing copy-error button.
 */
@Composable
fun ErrorBanner(error: UiError, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = error.kind.friendlyMessage(),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CopyErrorButton(error, tint = MaterialTheme.colorScheme.onErrorContainer)
            IconButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Section-scoped notice for a background-loader failure — sits inside the tab/panel it belongs
 * to instead of blanking the whole dashboard behind the global [ErrorBanner]. [onRetry] is null
 * when the section has no simple reload path.
 */
@Composable
fun InlineErrorNotice(error: UiError, onRetry: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = error.kind.friendlyMessage(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.action_retry))
                }
            }
            CopyErrorButton(error, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Copies [error]'s full technical report — never the friendly sentence — to the clipboard. */
@OptIn(ExperimentalTime::class)
@Composable
private fun CopyErrorButton(error: UiError, tint: Color) {
    val clipboard = LocalClipboardManager.current
    var justCopied by remember { mutableStateOf(false) }
    val copiedLabel = stringResource(Res.string.snackbar_error_details_copied)
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1500)
            justCopied = false
        }
    }
    if (justCopied) {
        Text(copiedLabel, style = MaterialTheme.typography.labelSmall, color = tint)
    }
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(error.toClipboardReport(Clock.System.now().toString())))
            justCopied = true
        },
        modifier = Modifier.handCursor(),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_copy),
            contentDescription = stringResource(Res.string.action_copy_error_details),
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}
