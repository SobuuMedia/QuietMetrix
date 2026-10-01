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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavDestinationTest {

    @Test
    fun hasEightDestinationsInExpectedOrder() {
        assertEquals(
            listOf("Overview", "Flow", "Retention", "Projects", "Funnels", "Experiments", "Users", "Settings"),
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
    fun eachDestinationHasEmojiAndLabel() {
        // Emoji mapping confirmed against the HTML prototype's rendered nav bar
        // (design_handoff_dashboard_redesign/QuietMetrix Prototype.dc.html:992),
        // not inferred from README prose. `Users` corresponds to the
        // prototype's "team" entry (label "Team", emoji U+1F465).
        assertEquals("🏠", NavDestination.Overview.emoji) // 🏠
        assertEquals("🧭", NavDestination.Flow.emoji) // 🧭
        assertEquals("🔁", NavDestination.Retention.emoji) // 🔁
        assertEquals("📦", NavDestination.Projects.emoji) // 📦
        assertEquals("🪜", NavDestination.Funnels.emoji) // 🪜
        assertEquals("🧪", NavDestination.Experiments.emoji) // 🧪
        assertEquals("👥", NavDestination.Users.emoji) // 👥
        assertEquals("⚙️", NavDestination.Settings.emoji) // ⚙️

        assertEquals(Res.string.nav_overview, NavDestination.Overview.labelRes)
        assertEquals(Res.string.nav_flow, NavDestination.Flow.labelRes)
        assertEquals(Res.string.nav_retention, NavDestination.Retention.labelRes)
        assertEquals(Res.string.nav_projects, NavDestination.Projects.labelRes)
        assertEquals(Res.string.nav_funnels, NavDestination.Funnels.labelRes)
        assertEquals(Res.string.nav_experiments, NavDestination.Experiments.labelRes)
        assertEquals(Res.string.nav_settings, NavDestination.Settings.labelRes)
    }
}
