package com.quietmetrix.dashboard.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * Shows the pointer/hand cursor while hovering an interactive element. Apply to
 * every clickable (buttons, icon buttons, clickable cards, links) so the mouse
 * changes from the arrow to a hand, signalling the element can be clicked.
 *
 * On platforms without a pointer (touch) this is a no-op.
 */
fun Modifier.handCursor(): Modifier = this.pointerHoverIcon(PointerIcon.Hand)
