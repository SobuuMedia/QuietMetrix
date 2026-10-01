package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_close
import com.quietmetrix.dashboard.resources.ic_close
import com.quietmetrix.dashboard.theme.QmShapeTokens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Maximum width of a [DrawerShell] panel, matching the HTML prototype's
 * `width:min(460px,100vw)`/`min(520px,100vw)` (QuietMetrix
 * Prototype.dc.html:635,657) — 460dp covers the narrower Ask-drawer case;
 * the wider 520dp drill-down case is each call site's own concern (not
 * exposed here since no task using this shell yet needs it wider than the
 * shared default).
 */
private val DRAWER_MAX_WIDTH = 460.dp

/**
 * Scrim dim color behind a [DrawerShell] panel, matching the HTML
 * prototype's `rgba(10,8,14,.45)` (QuietMetrix Prototype.dc.html:634).
 */
private val DRAWER_SCRIM = Color(0xFF0A080E).copy(alpha = 0.45f)

/**
 * [QmShapeTokens.drawer]'s 20dp radius, but only on the panel's two
 * left-facing corners. A [DrawerShell] panel sits flush against the
 * screen's top, right, and bottom edges (`top:0;right:0;bottom:0` in the
 * HTML prototype, QuietMetrix Prototype.dc.html:635) — its top-right and
 * bottom-right corners coincide with the screen's own corners, where a
 * visible radius would look like a notch cut out of the window rather than
 * a rounded card, so only the left (inward-facing) edge is rounded. Derived
 * from [QmShapeTokens.drawer] itself (via [androidx.compose.foundation.shape.CornerBasedShape.copy])
 * rather than a second 20dp literal, so the radius stays a single source
 * of truth.
 */
private val DrawerPanelShape = QmShapeTokens.drawer.copy(
    topEnd = CornerSize(0.dp),
    bottomEnd = CornerSize(0.dp),
)

/**
 * Shared chrome for the dashboard-redesign spec's right-side drawers — the
 * global AI "Ask your data" panel and drill-down views
 * (design_handoff_dashboard_redesign/README.md > "AI 'Ask your data'
 * drawer"; "Drill-down drawer": "clicking any vital card ... opens a
 * right-side drawer with a big number, delta, plain explanation, ..."). As
 * with [ModalShell], this is the general-purpose container later tasks
 * build their specific drawer content (the Ask panel, drill-down
 * breakdowns) on top of; it makes no assumption about what [content] is
 * beyond rendering it between the header and the optional [footer].
 *
 * Implemented as a full-screen [Dialog] (`usePlatformDefaultWidth = false`)
 * holding a hand-rolled scrim [Box] plus a right-aligned [Surface] panel,
 * rather than [AlertDialog] as [ModalShell] uses — a drawer is a
 * full-height edge panel, not a centered card, which doesn't fit
 * [AlertDialog]'s shape at all. [Dialog] still gives the same
 * dismiss-on-ESC/back semantics [ModalShell]/[ConfirmDialog] get from
 * [AlertDialog] (both are built on it); dismiss-on-scrim-click is added
 * explicitly here since [Dialog]'s own `dismissOnClickOutside` only covers
 * clicks outside the *window*, and this dialog's window already fills the
 * screen.
 *
 * Width is `min(460dp, available width)` — [BoxWithConstraints] reads the
 * actual screen width so the panel never overflows a narrow viewport,
 * matching the HTML prototype's `width:min(460px,100vw)` (QuietMetrix
 * Prototype.dc.html:635). Content scrolls independently of the header/
 * footer so a long breakdown list doesn't push the footer off-screen.
 *
 * @param title the drawer's header title.
 * @param onDismiss invoked when the ✕ is pressed, the scrim is clicked, or
 * ESC/back is pressed.
 * @param footer optional footer row, rendered below the scrollable
 * [content]; null (the default) renders no footer slot *and no reserved
 * space for one* — both of the spec's named drawers (the Ask panel, the
 * drill-down view) have no footer row at all, so a null default must cost
 * zero extra height, not an empty padded strip.
 * @param content the drawer's scrollable body.
 */
@Composable
fun DrawerShell(
    title: String,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelWidth = if (maxWidth < DRAWER_MAX_WIDTH) maxWidth else DRAWER_MAX_WIDTH

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DRAWER_SCRIM)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            Surface(
                shape = DrawerPanelShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidth),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = stringResource(Res.string.action_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                    ) { content() }

                    if (footer != null) {
                        Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) { footer() }
                    }
                }
            }
        }
    }
}
