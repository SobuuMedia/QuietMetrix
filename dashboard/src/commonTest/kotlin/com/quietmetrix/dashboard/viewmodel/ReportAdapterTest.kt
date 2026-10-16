package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.ProjectReportDto
import com.quietmetrix.dashboard.api.ReportMeasureDto
import com.quietmetrix.dashboard.nav.NavDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportAdapterTest {
    @Test fun previousDurationUsesItsOwnReportValueAndState() {
        val report=ProjectReportDto(overview=mapOf("visit_duration" to ReportMeasureDto(60000,"reported","milliseconds","v2")),
            comparison=mapOf("visit_duration" to ReportMeasureDto(null,"suppressed","milliseconds","v2")))
        assertEquals(60000L,report.toAggregates(AggregatesResponse(),false).v2VisitDurationMs)
        assertNull(report.toAggregates(AggregatesResponse(),true).v2VisitDurationMs)
        assertEquals("suppressed",report.toAggregates(AggregatesResponse(),true).v2VisitDurationState)
    }
    @Test fun aiSelectionCarriesTheExactFilterAndRevision() {
        val state=DashboardState(activeDestination=NavDestination.Funnels,selectedFunnelKey="signup", selectedFunnelRevision=2,
            funnels=listOf(FunnelDto("signup","Signup",revision=3)),askContext=mapOf("step" to "finish"))
        val context=aiSelectionContext(state)
        assertEquals("signup",context["funnel"]); assertEquals("2",context["revision"]); assertEquals("finish",context["step"])
        assertEquals(OverviewFilter("country","DE"),DashboardState(overviewFilter=OverviewFilter("country","DE")).reportFilter())
    }
}
