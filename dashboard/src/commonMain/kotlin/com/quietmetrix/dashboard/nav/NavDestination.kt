package com.quietmetrix.dashboard.nav

import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.nav_experiments
import com.quietmetrix.dashboard.resources.nav_flow
import com.quietmetrix.dashboard.resources.nav_funnels
import com.quietmetrix.dashboard.resources.nav_overview
import com.quietmetrix.dashboard.resources.nav_projects
import com.quietmetrix.dashboard.resources.nav_retention
import com.quietmetrix.dashboard.resources.nav_settings
import com.quietmetrix.dashboard.resources.nav_users
import org.jetbrains.compose.resources.StringResource

/**
 * A top-level destination in the dashboard. Replaces the old `Tab` enum and
 * carries the emoji + localized label needed by the side menu so navigation
 * items never render without an icon or with a raw enum name.
 *
 * The icon was originally a custom `DrawableResource` vector per destination;
 * it is now a plain emoji string to match the dashboard redesign's prototype
 * nav bar, which renders `{{ n.emoji }} {{ n.label }}` directly (see
 * `design_handoff_dashboard_redesign/QuietMetrix Prototype.dc.html:992`). The
 * field is renamed from `icon` to `emoji` since it no longer identifies a
 * drawable resource.
 */
enum class NavDestination(
    val emoji: String,
    val labelRes: StringResource,
    val adminOnly: Boolean = false,
) {
    Overview(emoji = "🏠", labelRes = Res.string.nav_overview), // 🏠
    Flow(emoji = "🧭", labelRes = Res.string.nav_flow), // 🧭
    Retention(emoji = "🔁", labelRes = Res.string.nav_retention), // 🔁
    Projects(emoji = "📦", labelRes = Res.string.nav_projects), // 📦
    Funnels(emoji = "🪜", labelRes = Res.string.nav_funnels), // 🪜
    Experiments(emoji = "🧪", labelRes = Res.string.nav_experiments), // 🧪
    // Corresponds to the prototype's "team" nav entry (label "Team"); the
    // enum entry is named `Users` to match the existing admin-only user
    // management screen.
    Users(emoji = "👥", labelRes = Res.string.nav_users, adminOnly = true), // 👥
    Settings(emoji = "⚙️", labelRes = Res.string.nav_settings); // ⚙️

    companion object {
        /** Destinations visible to a user with the given global [role]. */
        fun visibleFor(role: String?): List<NavDestination> =
            entries.filter { !it.adminOnly || role == UserRole.ADMIN }
    }
}
