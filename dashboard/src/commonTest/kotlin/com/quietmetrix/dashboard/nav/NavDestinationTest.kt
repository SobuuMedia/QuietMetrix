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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavDestinationTest {

    @Test
    fun hasEightDestinationsInExpectedOrder() {
        assertEquals(
            listOf("Overview", "Flow", "Retention", "Funnels", "Experiments", "Projects", "Users", "Settings"),
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
        assertTrue(NavDestination.visibleFor(UserRole.REVIEWER).contains(NavDestination.Funnels))
        assertTrue(NavDestination.visibleFor(UserRole.REVIEWER).contains(NavDestination.Retention))
    }

    @Test
    fun eachDestinationHasSharedVectorIconAndLabel() {
        assertEquals(Res.drawable.ic_twemoji_1f3e0, NavDestination.Overview.icon)
        assertEquals(Res.drawable.ic_twemoji_1f9ed, NavDestination.Flow.icon)
        assertEquals(Res.drawable.ic_twemoji_1f501, NavDestination.Retention.icon)
        assertEquals(Res.drawable.ic_twemoji_1fa9c, NavDestination.Funnels.icon)
        assertEquals(Res.drawable.ic_twemoji_1f9ea, NavDestination.Experiments.icon)
        assertEquals(Res.drawable.ic_twemoji_1f4e6, NavDestination.Projects.icon)
        assertEquals(Res.drawable.ic_twemoji_1f465, NavDestination.Users.icon)
        assertEquals(Res.drawable.ic_twemoji_2699, NavDestination.Settings.icon)

        assertEquals(Res.string.nav_overview, NavDestination.Overview.labelRes)
        assertEquals(Res.string.nav_flow, NavDestination.Flow.labelRes)
        assertEquals(Res.string.nav_retention, NavDestination.Retention.labelRes)
        assertEquals(Res.string.nav_funnels, NavDestination.Funnels.labelRes)
        assertEquals(Res.string.nav_experiments, NavDestination.Experiments.labelRes)
        assertEquals(Res.string.nav_projects, NavDestination.Projects.labelRes)
        assertEquals(Res.string.nav_team, NavDestination.Users.labelRes)
        assertEquals(Res.string.nav_settings, NavDestination.Settings.labelRes)
    }
}
