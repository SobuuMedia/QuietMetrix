package com.quietmetrix.server.ai

import com.quietmetrix.server.domain.ReportMeasure
import com.quietmetrix.server.domain.ReportRetentionCohort
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Match the dashboard headline: weight only mature, reportable cohort cells. */
internal fun visibleRetentionSummary(cohorts: List<ReportRetentionCohort>): Map<String, ReportMeasure> =
    listOf(1,3,7,14,30).associate { age ->
        val rows=cohorts.filter { c ->
            (c.size.value ?: 0)>0 && c.size.state in setOf("reported","zero") &&
                c.days[age.toString()]?.let { it.state in setOf("reported","zero") && it.rate != null } == true
        }
        val size=rows.sumOf { it.size.value ?: 0 }
        val measure=if (size==0L) ReportMeasure(state="insufficient_data",unit="installations",source="v2")
        else ReportMeasure(state="reported",unit="installations",source="v2",contributors=size,
            value=rows.sumOf { it.days.getValue(age.toString()).value ?: 0 },
            rate=rows.sumOf { it.size.value!! * it.days.getValue(age.toString()).rate!! } / size)
        "day_$age" to measure
    }

internal fun visibleRetentionJson(cohorts: List<ReportRetentionCohort>) =
    Json.encodeToJsonElement(MapSerializer(String.serializer(),ReportMeasure.serializer()),visibleRetentionSummary(cohorts))
