package com.quietmetrix.dashboard.nav

import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_twemoji_1f3e0
import com.quietmetrix.dashboard.resources.ic_twemoji_1f465
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4e6
import com.quietmetrix.dashboard.resources.ic_twemoji_1f501
import com.quietmetrix.dashboard.resources.ic_twemoji_1f9ea
import com.quietmetrix.dashboard.resources.ic_twemoji_1f9ed
import com.quietmetrix.dashboard.resources.ic_twemoji_1fa9c
import com.quietmetrix.dashboard.resources.ic_twemoji_2699
import com.quietmetrix.dashboard.resources.nav_experiments
import com.quietmetrix.dashboard.resources.nav_flow
import com.quietmetrix.dashboard.resources.nav_funnels
import com.quietmetrix.dashboard.resources.nav_overview
import com.quietmetrix.dashboard.resources.nav_projects
import com.quietmetrix.dashboard.resources.nav_retention
import com.quietmetrix.dashboard.resources.nav_settings
import com.quietmetrix.dashboard.resources.nav_team
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** A top-level dashboard destination with a shared vector icon and localized label. */
enum class NavDestination(
    val icon: DrawableResource,
    val labelRes: StringResource,
    val adminOnly: Boolean = false,
) {
    Overview(icon = Res.drawable.ic_twemoji_1f3e0, labelRes = Res.string.nav_overview),
    Flow(icon = Res.drawable.ic_twemoji_1f9ed, labelRes = Res.string.nav_flow),
    Retention(icon = Res.drawable.ic_twemoji_1f501, labelRes = Res.string.nav_retention),
    Funnels(icon = Res.drawable.ic_twemoji_1fa9c, labelRes = Res.string.nav_funnels),
    Experiments(icon = Res.drawable.ic_twemoji_1f9ea, labelRes = Res.string.nav_experiments),
    Projects(icon = Res.drawable.ic_twemoji_1f4e6, labelRes = Res.string.nav_projects),
    // Corresponds to the prototype's "team" nav entry (label "Team"); the
    // enum entry is named `Users` to match the existing admin-only user
    // management screen.
    Users(icon = Res.drawable.ic_twemoji_1f465, labelRes = Res.string.nav_team, adminOnly = true),
    Settings(icon = Res.drawable.ic_twemoji_2699, labelRes = Res.string.nav_settings);

    companion object {
        /** Destinations visible to a user with the given global [role]. */
        fun visibleFor(role: String?): List<NavDestination> =
            entries.filter { !it.adminOnly || role == UserRole.ADMIN }
    }
}
