package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.toUiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DashboardState.sectionErrors] lets one failing background loader (e.g. funnels) show an
 * inline notice on its own tab without blanking the whole dashboard behind the global
 * [DashboardState.error] banner, which is reserved for user-initiated actions like creating a
 * project. These are the pure state transforms every background loader in [DashboardViewModel]
 * goes through in its catch/success branches.
 */
class SectionErrorTest {

    private fun error(where: String = "GET /x") = RuntimeException("boom").toUiError(where)

    @Test
    fun withSectionErrorDoesNotTouchTheGlobalBanner() {
        val state = DashboardState().withSectionError(DataSection.Funnels, error())
        assertNull(state.error)
        assertEquals(1, state.sectionErrors.size)
    }

    @Test
    fun withSectionErrorOnlyAffectsItsOwnSection() {
        val state = DashboardState()
            .withSectionError(DataSection.Funnels, error("GET /projects/{id}/funnels"))
            .withSectionError(DataSection.Aggregates, error("GET /projects/{id}/aggregates"))
        assertEquals(2, state.sectionErrors.size)
        assertEquals("GET /projects/{id}/funnels", state.sectionErrors[DataSection.Funnels]?.where)
        assertEquals("GET /projects/{id}/aggregates", state.sectionErrors[DataSection.Aggregates]?.where)
    }

    @Test
    fun clearingSectionErrorRemovesOnlyThatSection() {
        val state = DashboardState()
            .withSectionError(DataSection.Funnels, error())
            .withSectionError(DataSection.Sessions, error())
            .clearingSectionError(DataSection.Funnels)
        assertTrue(DataSection.Funnels !in state.sectionErrors)
        assertTrue(DataSection.Sessions in state.sectionErrors)
    }

    @Test
    fun clearingSectionErrorLeavesTheGlobalBannerUntouched() {
        val globalError = error("POST /projects")
        val state = DashboardState(error = globalError)
            .withSectionError(DataSection.Funnels, error())
            .clearingSectionError(DataSection.Funnels)
        assertEquals(globalError, state.error)
    }

    @Test
    fun settingASectionErrorLeavesAPreExistingGlobalErrorUntouched() {
        val globalError = error("POST /projects")
        val state = DashboardState(error = globalError).withSectionError(DataSection.Aggregates, error())
        assertEquals(globalError, state.error)
    }
}
