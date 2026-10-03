package com.quietmetrix.server.ask

import com.quietmetrix.server.counters.OverviewCounterAnalyzer
import com.quietmetrix.server.counters.SearchCounterAnalyzer
import com.quietmetrix.server.persistence.CounterRepository.CounterCell
import com.quietmetrix.server.retention.RetentionCounterAnalyzer
import com.quietmetrix.server.retention.RetentionCounterCell

/** A computed answer and the aggregate source used to derive it. */
data class AskAnalyticsAnswer(val text: String, val source: String)

/** Computes answers for the small set of questions backed by existing privacy-gated aggregates. */
object AskAnalyticsAnalyzer {
    /** Answers a supported intent from already k-anonymity-filtered cells, or explains the supported scope. */
    fun answer(
        question: String,
        sessions: List<CounterCell>,
        transitions: List<CounterCell>,
        searches: List<CounterCell>,
        zeroResults: List<CounterCell>,
        retention: List<RetentionCounterCell>,
        rangeLabel: String,
    ): AskAnalyticsAnswer {
        val q = question.lowercase()
        val source = "Selected range: $rangeLabel"
        if (Regex("\\bwhy\\b|por qué|por que").containsMatchIn(q)) {
            return AskAnalyticsAnswer("I can't establish causes from aggregate counters. Ask about sessions, search results, Day 1 retention, or the most reached screen to see the observed values.", "Supported aggregate questions · $source")
        }
        return when {
            "session" in q || "visit" in q || "sesion" in q || "sesión" in q -> {
                val total = sessions.sumOf { it.n }
                AskAnalyticsAnswer("There were $total sessions in the selected range.", "Sessions · $source")
            }
            ("search" in q || "búsqueda" in q || "busqueda" in q) -> {
                val worst = SearchCounterAnalyzer.analyze(searches, zeroResults).firstOrNull { it.rate != null }
                if (worst == null) AskAnalyticsAnswer("There isn't enough privacy-safe search data to answer that yet.", "Search aggregates · $source")
                else AskAnalyticsAnswer("${(worst.rate!! * 100).toInt()}% of searches on ${worst.screen} returned no results (${worst.zeroResult} of ${worst.total}).", "Search aggregates · $source")
            }
            "retention" in q || "return" in q || "came back" in q || "retención" in q || "retencion" in q || "volvieron" in q -> {
                val cohorts = RetentionCounterAnalyzer.analyze(retention)
                val known = cohorts.filter { it.day1 != null && it.size > 0 }
                val size = known.sumOf { it.size }
                val returning = known.sumOf { kotlin.math.round(it.day1!! * it.size) }
                if (size == 0) AskAnalyticsAnswer("There isn't enough privacy-safe Day 1 retention data to answer that yet.", "Retention aggregates · $source")
                else AskAnalyticsAnswer("${(returning * 100.0 / size).toInt()}% of people returned on Day 1 across cohorts with visible counts.", "Retention aggregates · $source")
            }
            "screen" in q || "page" in q || "pantalla" in q || "página" in q -> {
                val top = OverviewCounterAnalyzer.topScreens(transitions, 1).firstOrNull()
                if (top == null) AskAnalyticsAnswer("There isn't enough privacy-safe screen data to answer that yet.", "Screen transitions · $source")
                else AskAnalyticsAnswer("${top.first} was the most reached screen, with ${top.second} transitions in the selected range.", "Screen transitions · $source")
            }
            else -> AskAnalyticsAnswer("I can answer questions about sessions, search results, Day 1 retention, or the most reached screen. I only report patterns in the selected range; I can't establish causes.", "Supported aggregate questions · $source")
        }
    }
}
