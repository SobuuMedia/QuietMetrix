package com.quietmetrix.server.ai

import com.quietmetrix.server.domain.ReportMeasure
import com.quietmetrix.server.domain.ReportRetentionCohort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiReportAdapterTest {
    @Test fun `AI headline uses the same visible cohort weighting as dashboard`() {
        fun cohort(size:Long, rate:Double?, state:String="reported")=ReportRetentionCohort("2026-10-01",
            ReportMeasure(value=size,state="reported",unit="installations",source="v2"),
            mapOf("1" to ReportMeasure(value=rate?.let { (it*size).toLong() },rate=rate,state=state,unit="installations",source="v2")))
        val summary=visibleRetentionSummary(listOf(cohort(100,.8),cohort(900,.2),cohort(1000,null,"suppressed")))
        assertEquals(.26,summary.getValue("day_1").rate)
        assertEquals(1000L,summary.getValue("day_1").contributors)
        assertNull(summary.getValue("day_7").rate)
    }
}
