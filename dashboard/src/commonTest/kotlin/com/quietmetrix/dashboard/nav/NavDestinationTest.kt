package com.quietmetrix.dashboard.nav

import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ic_events
import com.quietmetrix.dashboard.resources.ic_flow
import com.quietmetrix.dashboard.resources.ic_live
import com.quietmetrix.dashboard.resources.ic_overview
import com.quietmetrix.dashboard.resources.ic_projects
import com.quietmetrix.dashboard.resources.ic_settings
import com.quietmetrix.dashboard.resources.nav_events
import com.quietmetrix.dashboard.resources.nav_flow
import com.quietmetrix.dashboard.resources.nav_live
import com.quietmetrix.dashboard.resources.nav_overview
import com.quietmetrix.dashboard.resources.nav_projects
import com.quietmetrix.dashboard.resources.nav_settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavDestinationTest {

    @Test
    fun hasSevenDestinationsInExpectedOrder() {
        assertEquals(
            listOf("Overview", "Events", "Flow", "Live", "Projects", "Users", "Settings"),
            NavDestination.entries.map { it.name },
        )
    }

    @Test
    fun usersIsAdminOnlyInNavigation() {
        // Admins see the Users entry; developers and reviewers don't.
        assertTrue(NavDestination.visibleFor(UserRole.ADMIN).contains(NavDestination.Users))
        assertFalse(NavDestination.visibleFor(UserRole.DEVELOPER).contains(NavDestination.Users))
        assertFalse(NavDestination.visibleFor(UserRole.REVIEWER).contains(NavDestination.Users))
        assertFalse(NavDestination.visibleFor(null).contains(NavDestination.Users))
        // Non-admin-only destinations are always present.
        assertTrue(NavDestination.visibleFor(UserRole.REVIEWER).contains(NavDestination.Projects))
    }

    @Test
    fun eachDestinationHasIconAndLabel() {
        // The drawable accessors are `by lazy` singletons, so identity/value
        // equality holds and verifies the icon is wired (never null/tofu).
        assertEquals(Res.drawable.ic_overview, NavDestination.Overview.icon)
        assertEquals(Res.drawable.ic_events, NavDestination.Events.icon)
        assertEquals(Res.drawable.ic_flow, NavDestination.Flow.icon)
        assertEquals(Res.drawable.ic_live, NavDestination.Live.icon)
        assertEquals(Res.drawable.ic_projects, NavDestination.Projects.icon)
        assertEquals(Res.drawable.ic_settings, NavDestination.Settings.icon)

        assertEquals(Res.string.nav_overview, NavDestination.Overview.labelRes)
        assertEquals(Res.string.nav_events, NavDestination.Events.labelRes)
        assertEquals(Res.string.nav_flow, NavDestination.Flow.labelRes)
        assertEquals(Res.string.nav_live, NavDestination.Live.labelRes)
        assertEquals(Res.string.nav_projects, NavDestination.Projects.labelRes)
        assertEquals(Res.string.nav_settings, NavDestination.Settings.labelRes)
    }
}
