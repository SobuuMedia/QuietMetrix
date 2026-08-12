package com.quietmetrix.dashboard.nav

import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_events
import com.quietmetrix.dashboard.resources.ic_flow
import com.quietmetrix.dashboard.resources.ic_funnel
import com.quietmetrix.dashboard.resources.ic_live
import com.quietmetrix.dashboard.resources.ic_overview
import com.quietmetrix.dashboard.resources.ic_projects
import com.quietmetrix.dashboard.resources.ic_settings
import com.quietmetrix.dashboard.resources.ic_users
import com.quietmetrix.dashboard.resources.nav_events
import com.quietmetrix.dashboard.resources.nav_flow
import com.quietmetrix.dashboard.resources.nav_funnels
import com.quietmetrix.dashboard.resources.nav_live
import com.quietmetrix.dashboard.resources.nav_overview
import com.quietmetrix.dashboard.resources.nav_projects
import com.quietmetrix.dashboard.resources.nav_settings
import com.quietmetrix.dashboard.resources.nav_users
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * A top-level destination in the dashboard. Replaces the old `Tab` enum and
 * carries the icon + localized label needed by the side menu so navigation
 * items never render without an icon or with a raw enum name.
 */
enum class NavDestination(
    val icon: DrawableResource,
    val labelRes: StringResource,
    val adminOnly: Boolean = false,
) {
    Overview(icon = Res.drawable.ic_overview, labelRes = Res.string.nav_overview),
    Events(icon = Res.drawable.ic_events, labelRes = Res.string.nav_events),
    Flow(icon = Res.drawable.ic_flow, labelRes = Res.string.nav_flow),
    Live(icon = Res.drawable.ic_live, labelRes = Res.string.nav_live),
    Projects(icon = Res.drawable.ic_projects, labelRes = Res.string.nav_projects),
    Funnels(icon = Res.drawable.ic_funnel, labelRes = Res.string.nav_funnels),
    Users(icon = Res.drawable.ic_users, labelRes = Res.string.nav_users, adminOnly = true),
    Settings(icon = Res.drawable.ic_settings, labelRes = Res.string.nav_settings);

    companion object {
        /** Destinations visible to a user with the given global [role]. */
        fun visibleFor(role: String?): List<NavDestination> =
            entries.filter { !it.adminOnly || role == UserRole.ADMIN }
    }
}
