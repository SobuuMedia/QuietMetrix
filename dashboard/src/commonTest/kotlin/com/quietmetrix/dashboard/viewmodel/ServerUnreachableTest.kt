package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.ApiException
import com.quietmetrix.dashboard.api.toUiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DashboardState.serverUnreachableError] drives the global, header-adjacent "We can't reach
 * your QuietMetrix server" banner (Task 17) — it must fire for a connectivity failure recorded
 * by any background loader, but stay null for section failures that aren't connectivity (e.g. a
 * 404/409 from one bad panel), and never derive from [DashboardState.error] (reserved for
 * user-initiated actions, which already render their own [com.quietmetrix.dashboard.ui.components.ErrorBanner]).
 */
class ServerUnreachableTest {

    private fun networkError(where: String = "GET /x") =
        RuntimeException("Connection refused").toUiError(where) // non-ApiException -> ErrorKind.Network

    private fun notFoundError(where: String = "GET /y") =
        ApiException(status = 404, code = null, serverMessage = null).toUiError(where)

    @Test
    fun nullWhenNoSectionHasFailed() {
        assertNull(DashboardState().serverUnreachableError)
    }

    @Test
    fun firesWhenAnySectionFailedWithNetworkKind() {
        val state = DashboardState().withSectionError(DataSection.Aggregates, networkError("GET /projects/{id}/aggregates"))
        assertEquals("GET /projects/{id}/aggregates", state.serverUnreachableError?.where)
    }

    @Test
    fun staysNullForANonNetworkSectionFailure() {
        val state = DashboardState().withSectionError(DataSection.Funnels, notFoundError())
        assertNull(state.serverUnreachableError)
    }

    @Test
    fun ignoresTheGlobalUserActionErrorField() {
        val state = DashboardState(error = networkError("POST /projects"))
        assertNull(state.serverUnreachableError)
    }

    @Test
    fun clearsOnceThatSectionRecoversEvenIfOthersStillFail() {
        val state = DashboardState()
            .withSectionError(DataSection.Aggregates, networkError("GET /projects/{id}/aggregates"))
            .withSectionError(DataSection.Sessions, notFoundError())
            .clearingSectionError(DataSection.Aggregates)
        assertNull(state.serverUnreachableError)
    }
}
