package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.TopEvent
import com.quietmetrix.dashboard.api.TopValue
import com.quietmetrix.dashboard.format.formatBucketLabel
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatDuration
import com.quietmetrix.dashboard.format.formatMinorUnits
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_dismiss
import com.quietmetrix.dashboard.resources.card_top_events
import com.quietmetrix.dashboard.resources.card_top_values
import com.quietmetrix.dashboard.resources.overview_breakdown_filter_cd
import com.quietmetrix.dashboard.resources.overview_card_countries_title
import com.quietmetrix.dashboard.resources.overview_card_platforms_title
import com.quietmetrix.dashboard.resources.overview_card_screens_title
import com.quietmetrix.dashboard.resources.overview_screen_row_visits
import com.quietmetrix.dashboard.resources.overview_worth_a_look_dominant_platform_body
import com.quietmetrix.dashboard.resources.overview_worth_a_look_dominant_platform_title
import com.quietmetrix.dashboard.resources.overview_worth_a_look_empty_body
import com.quietmetrix.dashboard.resources.overview_worth_a_look_empty_title
import com.quietmetrix.dashboard.resources.overview_worth_a_look_offline_body
import com.quietmetrix.dashboard.resources.overview_worth_a_look_offline_title
import com.quietmetrix.dashboard.resources.overview_worth_a_look_snooze_cd
import com.quietmetrix.dashboard.resources.overview_worth_a_look_title
import com.quietmetrix.dashboard.resources.overview_worth_a_look_top_screen_body
import com.quietmetrix.dashboard.resources.overview_worth_a_look_top_screen_title
import com.quietmetrix.dashboard.resources.overview_key_card_manage
import com.quietmetrix.dashboard.resources.overview_key_card_status_receiving
import com.quietmetrix.dashboard.resources.overview_key_card_status_unknown
import com.quietmetrix.dashboard.resources.overview_key_card_status_waiting
import com.quietmetrix.dashboard.resources.overview_key_card_title
import com.quietmetrix.dashboard.resources.overview_empty_setup_code_init_js
import com.quietmetrix.dashboard.resources.overview_empty_setup_code_init_kotlin
import com.quietmetrix.dashboard.resources.overview_empty_setup_code_init_swift
import com.quietmetrix.dashboard.resources.overview_empty_setup_code_install_js
import com.quietmetrix.dashboard.resources.overview_empty_setup_code_install_kotlin
import com.quietmetrix.dashboard.resources.overview_empty_setup_code_install_swift
import com.quietmetrix.dashboard.resources.overview_empty_setup_lang_js
import com.quietmetrix.dashboard.resources.overview_empty_setup_lang_kotlin
import com.quietmetrix.dashboard.resources.overview_empty_setup_lang_swift
import com.quietmetrix.dashboard.resources.overview_empty_setup_step1_title
import com.quietmetrix.dashboard.resources.overview_empty_setup_step2_title
import com.quietmetrix.dashboard.resources.overview_empty_setup_step3_body
import com.quietmetrix.dashboard.resources.overview_empty_setup_step3_title
import com.quietmetrix.dashboard.resources.overview_empty_setup_title
import com.quietmetrix.dashboard.resources.overview_empty_setup_waiting
import com.quietmetrix.dashboard.resources.ic_events
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_overview
import com.quietmetrix.dashboard.resources.kpi_errors
import com.quietmetrix.dashboard.resources.overview_empty_greeting
import com.quietmetrix.dashboard.resources.overview_empty_headline_highlight
import com.quietmetrix.dashboard.resources.overview_empty_headline_lead
import com.quietmetrix.dashboard.resources.overview_empty_headline_tail
import com.quietmetrix.dashboard.resources.overview_error_ai_nap_body
import com.quietmetrix.dashboard.resources.overview_error_ai_nap_emoji
import com.quietmetrix.dashboard.resources.overview_error_ai_nap_title
import com.quietmetrix.dashboard.resources.overview_chart_metric_actions
import com.quietmetrix.dashboard.resources.overview_chart_metric_visits
import com.quietmetrix.dashboard.resources.overview_chart_record_day
import com.quietmetrix.dashboard.resources.overview_chart_title_daily
import com.quietmetrix.dashboard.resources.overview_chart_title_hourly
import com.quietmetrix.dashboard.resources.overview_error_headline
import com.quietmetrix.dashboard.resources.overview_hero_ai_chip
import com.quietmetrix.dashboard.resources.overview_hero_copy
import com.quietmetrix.dashboard.resources.overview_hero_greeting
import com.quietmetrix.dashboard.resources.overview_hero_loading
import com.quietmetrix.dashboard.resources.overview_hero_opened
import com.quietmetrix.dashboard.resources.overview_hero_sessions
import com.quietmetrix.dashboard.resources.overview_hero_tail_generic
import com.quietmetrix.dashboard.resources.overview_hero_tail_standout
import com.quietmetrix.dashboard.resources.overview_hero_tell_me_more
import com.quietmetrix.dashboard.resources.overview_hero_trend_down
import com.quietmetrix.dashboard.resources.overview_hero_trend_up
import com.quietmetrix.dashboard.resources.overview_privacy_body
import com.quietmetrix.dashboard.resources.overview_privacy_gdpr_chip
import com.quietmetrix.dashboard.resources.overview_privacy_info_cd
import com.quietmetrix.dashboard.resources.overview_privacy_info_glyph
import com.quietmetrix.dashboard.resources.overview_privacy_lock_emoji
import com.quietmetrix.dashboard.resources.overview_privacy_no_banner_chip
import com.quietmetrix.dashboard.resources.overview_privacy_title
import com.quietmetrix.dashboard.resources.overview_vital_duration_caption
import com.quietmetrix.dashboard.resources.overview_vital_duration_label
import com.quietmetrix.dashboard.resources.overview_vital_errors_caption
import com.quietmetrix.dashboard.resources.overview_vital_filter_chip
import com.quietmetrix.dashboard.resources.overview_vital_retention_caption
import com.quietmetrix.dashboard.resources.overview_vital_retention_label
import com.quietmetrix.dashboard.resources.overview_vital_retention_not_tracked
import com.quietmetrix.dashboard.resources.overview_vital_sessions_caption
import com.quietmetrix.dashboard.resources.overview_vital_sessions_label
import com.quietmetrix.dashboard.resources.overview_vital_trend_down
import com.quietmetrix.dashboard.resources.overview_vital_trend_up
import com.quietmetrix.dashboard.resources.state_no_data
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.KpiCard
import com.quietmetrix.dashboard.ui.components.KpiSkeleton
import com.quietmetrix.dashboard.ui.components.LayoutFlow
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.SkeletonBox
import com.quietmetrix.dashboard.ui.components.charts.BarChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.BarData
import com.quietmetrix.dashboard.ui.components.contentStateFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.layoutFlowFor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.serverUnreachableError
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import com.quietmetrix.dashboard.nav.NavDestination as AppTab

// ---------------------------------------------------------------------------
// Overview screen (Task 18: hero + privacy card; Tasks 19-21 restyle the
// vitals/chart/top-lists content below, carried over as-is from the
// pre-redesign `OverviewTab` for now).
// ---------------------------------------------------------------------------

/**
 * The Overview screen: a hero ("AI-written" headline) + privacy card row, followed by the
 * vitals/chart/top-lists content (not yet restyled — see Tasks 19-21).
 */
@Composable
internal fun OverviewScreen(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
    MaxWidthContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProjectPicker(state.projects, state.currentProjectId) { viewModel.selectProject(it) }
                WindowPicker(state.range) { viewModel.selectRange(it) }
                if (state.demoMode) DemoBadge()
                IconButton(
                    onClick = { viewModel.showTabInfo(AppTab.Overview) },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = stringResource(Res.string.tab_info_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            state.sectionErrors[DataSection.Aggregates]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }
            state.sectionErrors[DataSection.Sessions]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }

            val agg = state.aggregates
            val sess = state.sessions
            val projectName = state.projects.firstOrNull { it.id == state.currentProjectId }
                ?.name?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.value_none)

            // Hero + privacy card: a flexible hero column next to a fixed-width privacy card on
            // wide layouts (README > Overview: "minmax(0,1fr) minmax(280px,360px)" grid), stacked
            // on compact widths — the same breakpoint ResponsiveRowOrColumn uses elsewhere, but a
            // plain Row/Column here since the two children are NOT evenly weighted like a KPI row.
            if (layoutFlowFor(sizeClass) == LayoutFlow.Row) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
                    OverviewHero(
                        projectName = projectName,
                        range = state.range,
                        agg = agg,
                        sess = sess,
                        loading = state.loading,
                        serverUnreachable = state.serverUnreachableError != null,
                        modifier = Modifier.weight(1f),
                    )
                    PrivacyCard(modifier = Modifier.width(320.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    OverviewHero(
                        projectName = projectName,
                        range = state.range,
                        agg = agg,
                        sess = sess,
                        loading = state.loading,
                        serverUnreachable = state.serverUnreachableError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrivacyCard(modifier = Modifier.fillMaxWidth())
                }
            }

            // Task 19: the redesigned 4-vital grid (Sessions/Time per visit/Came back next
            // day/Errors — see VitalsSection's KDoc) replaces the pre-redesign 5-card Events/
            // Offline-captured/Errors/Sessions/Avg-duration rows above. Events and
            // Offline-captured are no longer shown as standalone vitals cards per the new
            // design (they aren't among its 4 vitals); events remain visible via the daily
            // volume chart below.
            var activeVitalsFilter by remember { mutableStateOf<String?>(null) }
            VitalsSection(
                agg = agg,
                sess = sess,
                retention = state.retention,
                loading = state.loading,
                sizeClass = sizeClass,
                activeFilter = activeVitalsFilter,
                onClearFilter = { activeVitalsFilter = null },
                modifier = Modifier.fillMaxWidth(),
            )

            // Task 20: one unified "Daily volume chart card" (design_handoff_dashboard_redesign/
            // README.md > Overview) replaces the two pre-redesign cards above ("Daily volume" /
            // events-only bar chart, and "Daily sessions" / sessions-only line chart) — the
            // README describes a single card with a Visits/Actions toggle, not two permanently
            // stacked charts. See DailyVolumeSection's KDoc for the segmented-control and
            // record-day data-honesty decisions.
            DailyVolumeSection(
                agg = agg,
                sess = sess,
                range = state.range,
                modifier = Modifier.fillMaxWidth(),
            )

            ResponsiveRowOrColumn(sizeClass) {
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_top_events),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val top = agg?.topEvents ?: emptyList()
                        if (top.isEmpty()) {
                            EmptyState(Res.drawable.ic_events, Res.string.state_no_data)
                        } else {
                            top.forEach { TopEventRow(it) }
                        }
                    }
                }
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_top_values),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val topValues = agg?.topValues ?: emptyList()
                        if (topValues.isEmpty()) {
                            EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
                        } else {
                            topValues.forEach { TopValueRow(it) }
                        }
                    }
                }
            }

            // Task 21: whole-screen Empty state (OverviewTopListsLogic.kt's isOverviewEmpty)
            // swaps the 3-card row/"Worth a look"/key card region for numbered setup
            // instructions (design_handoff_dashboard_redesign/README.md > Interactions &
            // Behavior > "Empty state") — see SetupInstructionsSection's KDoc. The vitals grid
            // and daily-volume chart above keep their own Task 19/20 all-zero/EmptyState
            // treatments; only this region (never built until now) gets the setup-instructions
            // swap, matching OverviewHeroEmpty's own note that this content was deferred here.
            if (isOverviewEmpty(agg, sess) && !state.loading) {
                SetupInstructionsSection(modifier = Modifier.fillMaxWidth())
            } else {
                ScreensPlatformsCountriesSection(
                    agg = agg,
                    loading = state.loading,
                    sizeClass = sizeClass,
                    activeFilter = activeVitalsFilter,
                    onSelectFilter = { activeVitalsFilter = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (layoutFlowFor(sizeClass) == LayoutFlow.Row) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
                        WorthALookSection(
                            agg = agg,
                            dismissedIds = state.dismissedWorthALookIds,
                            loading = state.loading,
                            onDismiss = { viewModel.hideWorthALook(it, snooze = false) },
                            onSnooze = { viewModel.hideWorthALook(it, snooze = true) },
                            modifier = Modifier.weight(1f),
                        )
                        KeyCard(
                            project = state.projects.firstOrNull { it.id == state.currentProjectId },
                            isReceivingData = (agg?.totals?.events ?: 0L) > 0L,
                            serverUnreachable = state.serverUnreachableError != null,
                            onManageKey = { viewModel.navigateTo(AppTab.Projects) },
                            modifier = Modifier.width(320.dp),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        WorthALookSection(
                            agg = agg,
                            dismissedIds = state.dismissedWorthALookIds,
                            loading = state.loading,
                            onDismiss = { viewModel.hideWorthALook(it, snooze = false) },
                            onSnooze = { viewModel.hideWorthALook(it, snooze = true) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        KeyCard(
                            project = state.projects.firstOrNull { it.id == state.currentProjectId },
                            isReceivingData = (agg?.totals?.events ?: 0L) > 0L,
                            serverUnreachable = state.serverUnreachableError != null,
                            onManageKey = { viewModel.navigateTo(AppTab.Projects) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopEventRow(item: TopEvent) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.eventName, modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodyMedium)
        Text(item.count.toString(), style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopValueRow(item: TopValue) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.name, modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodyMedium)
        Text(formatMinorUnits(item.totalMinorUnits), style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Human-readable dwell time: "8s", "1m 38s", "2m" — reused by [ScreenUsageRowItem] (Task 21). */
private fun formatDwell(ms: Long): String {
    val totalSec = (ms / 1000.0).roundToInt()
    if (totalSec < 60) return "${totalSec}s"
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return if (seconds == 0) "${minutes}m" else "${minutes}m ${seconds}s"
}

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

/**
 * The Overview hero: a state-dependent greeting + large headline. Normal shows an "AI-written"
 * plain-language summary with lime-highlighted numbers ([OverviewHeroNormal]); Loading shows a
 * contextual loading line ([OverviewHeroLoading]); Empty shows a first-run setup prompt
 * ([OverviewHeroEmpty]); and while the server is unreachable, a plain numbers-may-be-stale
 * headline plus an "AI summary unavailable" notice ([OverviewHeroError]) — the header-level
 * `ServerUnreachableBanner` (`DashboardShell.kt`, Task 17) already communicates the connection
 * problem itself, so this branch only avoids claiming a fresh AI summary it can't produce right
 * now, rather than duplicating that message.
 *
 * [agg]/[sess] feed [computeHeroNumbers] for the Normal headline's numbers; see that function's
 * KDoc for the honest proxy-data caveat (no real distinct-people/previous-period metric exists
 * server-side yet).
 */
@Composable
internal fun OverviewHero(
    projectName: String,
    range: TimeRange,
    agg: AggregatesResponse?,
    sess: SessionsResponse?,
    loading: Boolean,
    serverUnreachable: Boolean,
    modifier: Modifier = Modifier,
) {
    val hero = computeHeroNumbers(agg, sess)
    val isEmpty = hero == null || (hero.sessionCount == 0L && (agg?.totals?.events ?: 0L) == 0L)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            serverUnreachable -> OverviewHeroError(projectName, range)
            else -> when (contentStateFor(loading, isEmpty)) {
                ContentState.Loading -> OverviewHeroLoading()
                ContentState.Empty -> OverviewHeroEmpty(projectName)
                ContentState.Data -> OverviewHeroNormal(hero!!, projectName, range)
            }
        }
    }
}

/** Normal-state hero content: greeting line + the AI-written headline + its attribution chip. */
@Composable
private fun OverviewHeroNormal(hero: HeroNumbers, projectName: String, range: TimeRange) {
    val extended = LocalExtendedColors.current
    val lime = SpanStyle(background = extended.lime, color = extended.onLime)

    Text(
        stringResource(Res.string.overview_hero_greeting, projectName, stringResource(range.labelRes)),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val sessionsLabel = stringResource(Res.string.overview_hero_sessions, formatCount(hero.sessionCount))
    val openedPhrase = stringResource(Res.string.overview_hero_opened, projectName)
    // Trend DIRECTION only, never the numeric magnitude — see HeroNumbers.momentumIsIncrease's
    // KDoc: this isn't a real period-over-period comparison, so it must not read like one.
    val trendPhrase = hero.momentumChange?.let { _ ->
        stringResource(if (hero.momentumIsIncrease) Res.string.overview_hero_trend_up else Res.string.overview_hero_trend_down)
    }
    val tail = hero.standoutEvent?.let { stringResource(Res.string.overview_hero_tail_standout, it) }
        ?: stringResource(Res.string.overview_hero_tail_generic)

    val sentence = buildAnnotatedString {
        withStyle(lime) { append(sessionsLabel) }
        append(" ")
        append(openedPhrase)
        if (trendPhrase != null) {
            append(", ")
            withStyle(lime) { append(trendPhrase) }
            append(".")
        } else {
            append(".")
        }
        append(" ")
        append(tail)
    }
    Text(sentence, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)

    val aiChip = stringResource(Res.string.overview_hero_ai_chip)
    val tellMeMore = stringResource(Res.string.overview_hero_tell_me_more)
    val copyLabel = stringResource(Res.string.overview_hero_copy)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = extended.chipFill, shape = QmShapeTokens.pill) {
            Text(
                aiChip,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        // Stubs for now — the Ask-drawer ("Tell me more") and headline clipboard copy
        // ("Copy") land when the Ask drawer itself is built (Task 40 per the plan's
        // standing "stub now, wire for real later" convention, see progress.md).
        Text(
            tellMeMore,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.handCursor(),
        )
        Text(
            copyLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.handCursor(),
        )
    }
}

/** Loading-state hero content: just the contextual loading line (`_source/02-overview-loading.png`). */
@Composable
private fun OverviewHeroLoading() {
    Text(
        stringResource(Res.string.overview_hero_loading),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** First-run hero content: a welcome line + a headline prompting SDK setup (the numbered
 *  setup instructions themselves are Task 21's "Worth a look"/key-card scope, not this task's). */
@Composable
private fun OverviewHeroEmpty(projectName: String) {
    val extended = LocalExtendedColors.current
    Text(
        stringResource(Res.string.overview_empty_greeting, projectName),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val lead = stringResource(Res.string.overview_empty_headline_lead)
    val highlight = stringResource(Res.string.overview_empty_headline_highlight)
    val tail = stringResource(Res.string.overview_empty_headline_tail)
    val sentence = buildAnnotatedString {
        append(lead)
        append(" ")
        withStyle(SpanStyle(background = extended.lime, color = extended.onLime)) { append(highlight) }
        append(" ")
        append(tail)
    }
    Text(sentence, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
}

/** Errors-state hero content: the usual greeting + a plain (non-AI, no lime highlights) headline,
 *  plus an "AI summary unavailable" notice — the connection problem itself is the header banner's
 *  job (`ServerUnreachableBanner`, Task 17), not repeated here. */
@Composable
private fun OverviewHeroError(projectName: String, range: TimeRange) {
    val extended = LocalExtendedColors.current
    val rangeLabel = stringResource(range.labelRes)
    Text(
        stringResource(Res.string.overview_hero_greeting, projectName, rangeLabel),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(Res.string.overview_error_headline, rangeLabel),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Surface(
        color = extended.coral.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, extended.coral.copy(alpha = 0.4f)),
        shape = QmShapeTokens.chip,
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(Res.string.overview_error_ai_nap_emoji),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.overview_error_ai_nap_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(Res.string.overview_error_ai_nap_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Privacy card
// ---------------------------------------------------------------------------

/**
 * Static "Privacy check: all clear" card — the hero's right-column companion
 * (design_handoff_dashboard_redesign/README.md > Overview: "lime-bordered privacy card"). Pure
 * copy, no dynamic data, so it renders identically across all 4 Overview states.
 */
@Composable
internal fun PrivacyCard(modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = QmShapeTokens.card,
        border = BorderStroke(1.dp, extended.lime),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.overview_privacy_lock_emoji),
                    style = MaterialTheme.typography.headlineMedium,
                )
                val infoCd = stringResource(Res.string.overview_privacy_info_cd)
                Surface(color = extended.chipFill, shape = QmShapeTokens.pill) {
                    Text(
                        stringResource(Res.string.overview_privacy_info_glyph),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .semantics { contentDescription = infoCd },
                    )
                }
            }
            Text(stringResource(Res.string.overview_privacy_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.overview_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(stringResource(Res.string.overview_privacy_gdpr_chip), PillVariant.Neutral)
                Pill(stringResource(Res.string.overview_privacy_no_banner_chip), PillVariant.Neutral)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Vitals grid (Task 19)
// ---------------------------------------------------------------------------

/**
 * The redesigned Overview vitals grid: 4 [KpiCard]s — Sessions, Time per visit, Came back next
 * day, Errors — laid out via [ResponsiveRowOrColumn], preceded by an optional lavender "Showing
 * only {X} ✕" filter chip (design_handoff_dashboard_redesign/README.md > Overview: "Clicking any
 * of these three [screens/platforms/countries, a later task's 3-card row] shows a lavender
 * 'Showing only {X} ✕' filter chip above the vitals grid"). See [VitalsNumbers]'s KDoc for why 2
 * of the 4 cards are labeled "Sessions"/"Errors" rather than the design handoff's "People"/
 * "Crashes" — the same data-honesty standard Task 18 established for the hero.
 *
 * Only Sessions gets a delta pill: it's the only one of the 4 with a daily historical series
 * ([SessionsResponse.dailySessions]) to honestly compute a trend from (see [VitalsNumbers]'s
 * KDoc). The design handoff's "was N (last week)" Compare-mode note
 * ([KpiCard]'s `compareNote` parameter) is deliberately never populated with real data here for
 * the same reason: there is no previous-period total anywhere in
 * [AggregatesResponse]/[SessionsResponse]/[RetentionResponse] to honestly show as "last week"
 * (the exact gap Task 18's review already flagged and fixed for the hero's percent-change) — a
 * "Compare" toggle also doesn't reach this screen at all yet (`DashboardShell.kt`'s `compareOn` is
 * local header state, never threaded down to screen content), so this is a double gap: no
 * plumbing AND no honest number to plumb. `KpiCard.compareNote`'s capability is proven separately
 * by `KpiCardScreenshotTest`'s own fixture.
 *
 * [activeFilter]/[onClearFilter] are driven by [OverviewScreen]'s own local `remember` state for
 * now — there is no filter SOURCE yet (the 3-card row that would set it, per the README excerpt
 * above, is a later task), so in the running app [activeFilter] is always null and this chip never
 * actually appears yet. It's built and screenshot-tested here (see
 * `OverviewVitalsScreenshotTest.withActiveFilterChipMatchesGolden`) so that later task only has to
 * call `onClearFilter`'s sibling setter, not build the chip itself — same "stub now, wire for real
 * later" convention as the hero's "Tell me more"/"Copy" links (Task 18).
 *
 * Loading shows 4 [KpiSkeleton]s; every other case (real data, including all-zero "first run"
 * data, and stale data rendered while the server is unreachable — this composable has no
 * server-reachability awareness of its own, matching the pre-redesign KPI rows it replaces) shows
 * the 4 real cards.
 */
@Composable
internal fun VitalsSection(
    agg: AggregatesResponse?,
    sess: SessionsResponse?,
    retention: RetentionResponse?,
    loading: Boolean,
    sizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    activeFilter: String? = null,
    onClearFilter: () -> Unit = {},
) {
    val vitals = computeVitalsNumbers(agg, sess, retention)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (activeFilter != null) {
            Pill(
                text = stringResource(Res.string.overview_vital_filter_chip, activeFilter),
                variant = PillVariant.Lavender,
                onClick = onClearFilter,
            )
        }
        ResponsiveRowOrColumn(sizeClass) {
            when (contentStateFor(loading, vitals == null)) {
                ContentState.Loading -> {
                    KpiSkeleton(Modifier.fillSlot())
                    KpiSkeleton(Modifier.fillSlot())
                    KpiSkeleton(Modifier.fillSlot())
                    KpiSkeleton(Modifier.fillSlot())
                }
                else -> {
                    val v = vitals ?: VitalsNumbers(
                        sessionCount = 0L,
                        sessionTrendChange = null,
                        sessionTrendIsIncrease = true,
                        avgDurationSec = 0,
                        retentionDay1 = null,
                        errorsCount = 0L,
                    )
                    val sessionDelta = v.sessionTrendChange?.let { change ->
                        stringResource(
                            if (v.sessionTrendIsIncrease) Res.string.overview_vital_trend_up else Res.string.overview_vital_trend_down,
                            "$change%",
                        )
                    }
                    KpiCard(
                        label = stringResource(Res.string.overview_vital_sessions_label),
                        value = formatCount(v.sessionCount),
                        sub = stringResource(Res.string.overview_vital_sessions_caption),
                        delta = sessionDelta,
                        deltaPositive = v.sessionTrendIsIncrease,
                        modifier = Modifier.fillSlot(),
                    )
                    KpiCard(
                        label = stringResource(Res.string.overview_vital_duration_label),
                        value = formatDuration(v.avgDurationSec),
                        sub = stringResource(Res.string.overview_vital_duration_caption),
                        modifier = Modifier.fillSlot(),
                    )
                    val retentionValue = v.retentionDay1?.let { formatPercent(it) } ?: stringResource(Res.string.value_none)
                    val retentionCaption = if (v.retentionDay1 != null) {
                        stringResource(Res.string.overview_vital_retention_caption)
                    } else {
                        stringResource(Res.string.overview_vital_retention_not_tracked)
                    }
                    KpiCard(
                        label = stringResource(Res.string.overview_vital_retention_label),
                        value = retentionValue,
                        sub = retentionCaption,
                        modifier = Modifier.fillSlot(),
                    )
                    KpiCard(
                        label = stringResource(Res.string.kpi_errors),
                        value = formatCount(v.errorsCount),
                        sub = stringResource(Res.string.overview_vital_errors_caption),
                        modifier = Modifier.fillSlot(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Daily volume chart card (Task 20)
// ---------------------------------------------------------------------------

/**
 * The redesigned Overview daily-volume chart card: a title that reflects the active
 * [TimeRange]'s bucket granularity, a Visits/Actions segmented control, and a [BarChartCanvas] of
 * the selected series with the real "record day" highlighted in lime. Replaces the two
 * pre-redesign cards ("Daily volume" and "Daily sessions") that stood side by side in
 * `OverviewScreen` — the design handoff describes one card with a metric toggle, not two
 * permanently-stacked charts (design_handoff_dashboard_redesign/README.md > Overview).
 *
 * **Segmented control — "Visits"/"Actions" only, not "People/Visits/Actions":** see
 * [DailyVolumeMetric]'s KDoc. "People" (a distinct-device/WAU count) is not tracked anywhere in
 * this backend — the same gap Tasks 18/19 already found for the hero and vitals grid — and unlike
 * those two tasks there is no honest relabel available here (this control's "Visits" option
 * already *is* the sessions proxy those tasks used for "People"), so the segment is omitted
 * entirely rather than duplicated under a second name.
 *
 * **Title — 2 variants, not 3:** the design handoff's title changes "('Who showed up each day' /
 * '…each hour' / '…each week')" for 3 bucket granularities. [TimeRange] only distinguishes
 * `hourly` vs. not (`TimeRange.kt`) — there is no weekly bucketing anywhere in the backend (every
 * non-hourly range, including [TimeRange.Day90], buckets by day) — so a literal 3rd "each week"
 * title would describe a granularity that never actually renders. Only 2 variants are shown. The
 * wording itself is also changed from "who showed up" (a people/attendance framing) to "what
 * happened", since neither Visits nor Actions is a distinct-person count.
 *
 * **Record-day callout — real highlight, no fabricated caption:** the design handoff calls out the
 * record day with "a floating '🚀 v2.3 shipped' label and a lime bar"
 * (design_handoff_dashboard_redesign/README.md > Overview). The lime bar is real and honestly
 * derivable — [recordDayIndex] finds the actual highest-value bucket in whichever series is
 * selected, reusing [BarChartCanvas]'s new `highlightIndex`/`highlightColor` parameters (this
 * task's one genuine `BarChartCanvas` extension). The literal text "v2.3 shipped" is NOT rendered
 * anywhere: it is placeholder flavor text from the design mockup (grepped across this repo — no
 * changelog, release-note, or deploy-marker concept exists in either backend
 * (`servers/ktor/`/`php-hosting/`) that could honestly back a specific version string), and
 * showing it verbatim to a real user would fabricate a release event that may not have happened on
 * their actual record day. Instead, a plain, true caption — "🔥 Busiest day: {label}" — is shown
 * under the chart, derived from the same real [recordDayIndex] the bar highlight uses.
 *
 * **Deliberately out of scope (documented, not silently dropped):**
 * - **Hover tooltip / click-to-drill-down**: the design handoff asks for both ("Hover a bar →
 *   tooltip with exact number; click → opens that day's drill-down"). The drill-down drawer itself
 *   is not wired anywhere in this codebase yet — every existing `KpiCard.onClick` callsite leaves
 *   it unset, and Tasks 10/14's reports already name this a project-wide, explicitly-deferred gap
 *   for later tasks (26/40/41) to wire up. Building a one-off hover/click affordance here that
 *   opens nothing would be a dead-end interaction, so both are left for whichever task wires the
 *   drill-down drawer for real.
 * - **Compare-mode ghost bars + legend**: the design handoff's "When Compare is on, each bar gets
 *   a dashed lavender ghost bar behind it for the previous period, plus a legend." Checked
 *   `ChartLogic.kt`/`BarChartCanvas.kt` first per this task's brief: **neither has any ghost-bar or
 *   previous-period support today.** Not adding it here either, for the same two-part reason
 *   Task 19's report already documented for the vitals grid's unpopulated `compareNote`: (a) there
 *   is no previous-period data anywhere in [AggregatesResponse]/[SessionsResponse] to honestly draw
 *   a ghost bar from, and (b) `DashboardShell.kt`'s `compareOn` Compare toggle is local header state
 *   that still isn't threaded down to any screen content. Rendering a dashed ghost bar from
 *   invented numbers would be exactly the fabrication this codebase's Task 18 precedent rules out;
 *   building the pure layout math for a feature with no real data to feed it would be speculative,
 *   untested-by-real-usage code. This is a scope *omission*, not a stub — unlike `KpiCard.onClick`,
 *   there is no Compare-aware parameter on this composable at all.
 *
 * Empty state (no buckets for the selected metric) shows the existing [EmptyState] placeholder,
 * matching the pre-redesign cards' own behavior — no new skeleton/loading treatment was added here
 * since neither pre-redesign card had one either.
 */
@Composable
internal fun DailyVolumeSection(
    agg: AggregatesResponse?,
    sess: SessionsResponse?,
    range: TimeRange,
    modifier: Modifier = Modifier,
) {
    var metric by remember { mutableStateOf(DailyVolumeMetric.Actions) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        if (range.hourly) Res.string.overview_chart_title_hourly else Res.string.overview_chart_title_daily,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                MetricSegmentedControl(selected = metric, onSelect = { metric = it })
            }
            Spacer(Modifier.height(8.dp))

            val bars = dailyVolumeBars(agg, sess, metric, range.hourly)
            if (bars.isEmpty()) {
                EmptyState(Res.drawable.ic_events, Res.string.state_no_data)
            } else {
                val recordIndex = recordDayIndex(bars)
                BarChartCanvas(
                    bars = bars.map { BarData(it.label, it.value.toFloat()) },
                    highlightIndex = recordIndex,
                )
                if (recordIndex != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.overview_chart_record_day, bars[recordIndex].label),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalExtendedColors.current.lime,
                    )
                }
            }
        }
    }
}

/**
 * The Visits/Actions toggle for [DailyVolumeSection] — two [Pill]s reusing the same
 * Lavender-when-selected/Neutral-when-not vocabulary [VitalsSection]'s filter chip and `Pill`'s own
 * variants already establish elsewhere on this screen (no distinct "segmented control" component
 * exists yet in `ui/components/`, and this task's scope is `OverviewScreen.kt` only — see
 * [DailyVolumeSection]'s KDoc for why "People" has no third option here).
 */
@Composable
private fun MetricSegmentedControl(
    selected: DailyVolumeMetric,
    onSelect: (DailyVolumeMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DailyVolumeMetric.entries.forEach { option ->
            val label = when (option) {
                DailyVolumeMetric.Visits -> stringResource(Res.string.overview_chart_metric_visits)
                DailyVolumeMetric.Actions -> stringResource(Res.string.overview_chart_metric_actions)
            }
            Pill(
                text = label,
                variant = if (option == selected) PillVariant.Lavender else PillVariant.Neutral,
                onClick = { onSelect(option) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3-card row: screens / platforms / countries (Task 21)
// ---------------------------------------------------------------------------

/**
 * The "📍 Where people hang out" / "📱 What they use" / "🌍 Where they are" 3-card row
 * (design_handoff_dashboard_redesign/README.md > Overview), built from real breakdown data already
 * fetched on [AggregatesResponse] — [AggregatesResponse.topScreens]+[AggregatesResponse.screenDurations]
 * (joined by [computeScreenUsage]), [AggregatesResponse.platforms], and
 * [AggregatesResponse.countries] (both ranked by [computeBreakdownRows]). No fabricated data: every
 * row is a real count from a field this dashboard already loads for the Overview screen.
 *
 * Clicking any row calls [onSelectFilter] with that row's real name — [OverviewScreen] wires this
 * straight into the SAME `activeVitalsFilter`/[VitalsSection] filter chip Task 19 already built
 * (design_handoff_dashboard_redesign/README.md > Overview: "Clicking any of these three shows a
 * lavender 'Showing only {X} ✕' filter chip above the vitals grid"); [activeFilter] highlights
 * whichever row (if any) matches the current filter's label, mirroring the prototype's own
 * `sel`-highlighted row background.
 *
 * Loading shows 3 skeleton cards (matching [KpiSkeleton]'s flat-fill idiom); each card
 * independently falls back to [EmptyState] when ITS OWN list is empty (e.g. real traffic exists
 * but no country data was ever recorded) — this is distinct from the whole-screen first-run Empty
 * state ([isOverviewEmpty]), which swaps this entire section out for [SetupInstructionsSection]
 * before this composable is even reached. No server-reachability awareness of its own (same
 * convention as [VitalsSection]/[DailyVolumeSection] — see their KDoc).
 */
@Composable
internal fun ScreensPlatformsCountriesSection(
    agg: AggregatesResponse?,
    loading: Boolean,
    sizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    activeFilter: String? = null,
    onSelectFilter: (String) -> Unit = {},
) {
    ResponsiveRowOrColumn(sizeClass, modifier = modifier) {
        if (loading) {
            TopListSkeletonCard(Modifier.fillSlot())
            TopListSkeletonCard(Modifier.fillSlot())
            TopListSkeletonCard(Modifier.fillSlot())
        } else {
            Card(modifier = Modifier.fillSlot()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.overview_card_screens_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    val rows = computeScreenUsage(agg)
                    if (rows.isEmpty()) {
                        EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
                    } else {
                        rows.forEach { row ->
                            ScreenUsageRowItem(row, selected = row.screen == activeFilter, onClick = { onSelectFilter(row.screen) })
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillSlot()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.overview_card_platforms_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    val rows = computeBreakdownRows(agg?.platforms.orEmpty())
                    if (rows.isEmpty()) {
                        EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
                    } else {
                        rows.forEach { row ->
                            BreakdownRowItem(row, selected = row.name == activeFilter, onClick = { onSelectFilter(row.name) })
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillSlot()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.overview_card_countries_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    val rows = computeBreakdownRows(agg?.countries.orEmpty())
                    if (rows.isEmpty()) {
                        EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
                    } else {
                        rows.forEach { row ->
                            BreakdownRowItem(row, selected = row.name == activeFilter, onClick = { onSelectFilter(row.name) })
                        }
                    }
                }
            }
        }
    }
}

/** One clickable row in the "📍 Where people hang out" card: screen name, visit count, dwell
 *  time, and a [UsageBar] sized by [ScreenUsageRow.barFraction]. [selected] highlights the row
 *  with the same lavender tint [VitalsSection]'s filter chip uses, mirroring the design
 *  prototype's own selected-row background. */
@Composable
private fun ScreenUsageRowItem(row: ScreenUsageRow, selected: Boolean, onClick: () -> Unit) {
    val extended = LocalExtendedColors.current
    val filterCd = stringResource(Res.string.overview_breakdown_filter_cd, row.screen)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) extended.lavender.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .handCursor()
            .padding(vertical = 6.dp, horizontal = 6.dp)
            .semantics { contentDescription = filterCd },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.screen, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else null)
            Text(formatDwell(row.avgDwellMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        UsageBar(fraction = row.barFraction.toFloat())
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(Res.string.overview_screen_row_visits, formatCount(row.visits)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One clickable row in the "📱 What they use"/"🌍 Where they are" cards: name, share-of-total
 *  percent, and a [UsageBar] sized by [BreakdownRow.barFraction]. See [ScreenUsageRowItem]'s KDoc
 *  for the [selected] highlight. */
@Composable
private fun BreakdownRowItem(row: BreakdownRow, selected: Boolean, onClick: () -> Unit) {
    val extended = LocalExtendedColors.current
    val filterCd = stringResource(Res.string.overview_breakdown_filter_cd, row.name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) extended.lavender.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .handCursor()
            .padding(vertical = 6.dp, horizontal = 6.dp)
            .semantics { contentDescription = filterCd },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else null)
            Text(formatPercent(row.shareOfTotal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        UsageBar(fraction = row.barFraction.toFloat())
    }
}

/** Thin horizontal bar: a subtle track ([ExtendedColors.chipFill]) + a lime fill sized to
 *  [fraction] (0f-1f) — the same "track + solid fill" idiom [HeaderProgressBarTrack]
 *  (`DashboardShell.kt`) already establishes, reused here at a shorter height for inline row
 *  bars. */
@Composable
private fun UsageBar(fraction: Float, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    Box(modifier = modifier.fillMaxWidth().height(4.dp).background(extended.chipFill, RoundedCornerShape(2.dp))) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(extended.lime, RoundedCornerShape(2.dp)),
        )
    }
}

/** Flat skeleton placeholder for one 3-card-row card while [AggregatesResponse] is loading —
 *  same flat tonal-fill idiom as [KpiSkeleton], sized taller to approximate a short ranked list. */
@Composable
private fun TopListSkeletonCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.width(100.dp).height(16.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(40.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(40.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// "Worth a look" (Task 21)
// ---------------------------------------------------------------------------

/**
 * The "👀 Worth a look" card. See `OverviewTopListsLogic.kt`'s [WorthALookObservation] KDoc for
 * the full data-honesty investigation: unlike the design handoff's literal "3 auto-generated
 * observations" (demonstrated in the prototype's own hardcoded demo data — an offline-sync note, a
 * fabricated A/B-experiment "winning" claim, and a fabricated Search drop-off claim), this card
 * renders only [computeWorthALookObservations]'s plain, mechanically-derived facts, and shows
 * however many (0-3) of them actually hold — never padded to 3.
 *
 * [dismissedIds] filters out cards the user has hidden this session
 * ([DashboardState.dismissedWorthALookIds]); [onDismiss]/[onSnooze] are each invoked with the
 * observation's [WorthALookObservation.id] and are expected to be wired straight to
 * [DashboardViewModel.hideWorthALook] (`false`/`true` for snooze) — the SAME toast queue every
 * other toast on this screen uses (design_handoff_dashboard_redesign/README.md > Interactions &
 * Behavior > "Dismiss/snooze": "both remove a 'Worth a look' card immediately and show an undo
 * toast for ~5s").
 *
 * Shows the honest "🎈 All caught up!" placeholder when no observation holds (or all have been
 * dismissed) — design_handoff_dashboard_redesign/README.md > Overview: "when the list is empty it
 * shows a '🎈 All caught up!' placeholder". Loading shows a flat skeleton; no server-reachability
 * awareness of its own (same convention as [VitalsSection]/[DailyVolumeSection]/
 * [ScreensPlatformsCountriesSection]).
 */
@Composable
internal fun WorthALookSection(
    agg: AggregatesResponse?,
    dismissedIds: Set<String>,
    loading: Boolean,
    onDismiss: (String) -> Unit,
    onSnooze: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.overview_worth_a_look_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (loading) {
                SkeletonBox(Modifier.fillMaxWidth().height(40.dp))
            } else {
                val observations = computeWorthALookObservations(agg).filter { it.id !in dismissedIds }
                if (observations.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        Text(stringResource(Res.string.overview_worth_a_look_empty_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.overview_worth_a_look_empty_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    observations.forEach { obs ->
                        WorthALookRow(obs, onDismiss = { onDismiss(obs.id) }, onSnooze = { onSnooze(obs.id) })
                    }
                }
            }
        }
    }
}

/** One "Worth a look" row: emoji + title + body (per-variant copy below) + a 💤 snooze icon + an
 *  ✕ dismiss icon — design_handoff_dashboard_redesign/README.md > Overview: "each: emoji, title,
 *  body, a text CTA, a 💤 snooze icon, and an ✕ dismiss icon". The text CTA is deliberately
 *  omitted: every real CTA target the design mockup's own observations use (the Ask drawer, the
 *  Experiments/Flow screens) belongs to a DIFFERENT, fabricated observation this task's
 *  investigation already rejected — none of the 3 honest observations here has an analogous real
 *  drill-down target of its own, and a stub link that opens nothing would be a dead-end
 *  affordance (the same reasoning `DailyVolumeSection`'s KDoc already gives for omitting its own
 *  hover-tooltip/click-to-drill-down). */
@Composable
private fun WorthALookRow(observation: WorthALookObservation, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val (emoji, title, body) = when (observation) {
        is OfflineSyncObservation -> Triple(
            "✈️",
            stringResource(Res.string.overview_worth_a_look_offline_title, formatCount(observation.count)),
            stringResource(Res.string.overview_worth_a_look_offline_body),
        )
        is TopScreenObservation -> Triple(
            "📍",
            stringResource(Res.string.overview_worth_a_look_top_screen_title, observation.screen),
            stringResource(Res.string.overview_worth_a_look_top_screen_body, formatCount(observation.visits)),
        )
        is DominantPlatformObservation -> Triple(
            "📱",
            stringResource(Res.string.overview_worth_a_look_dominant_platform_title, observation.platform),
            stringResource(Res.string.overview_worth_a_look_dominant_platform_body, formatPercent(observation.share)),
        )
    }
    val snoozeCd = stringResource(Res.string.overview_worth_a_look_snooze_cd)
    val dismissCd = stringResource(Res.string.action_dismiss)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "💤",
            modifier = Modifier
                .clickable(onClick = onSnooze)
                .handCursor()
                .padding(6.dp)
                .semantics { contentDescription = snoozeCd },
        )
        Text(
            "✕",
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .handCursor()
                .padding(6.dp)
                .semantics { contentDescription = dismissCd },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Key card (Task 21)
// ---------------------------------------------------------------------------

/**
 * The sticky-style "🔑 Your {project} key" card (design_handoff_dashboard_redesign/README.md >
 * Overview: "Sticky-style lime '🔑 Your {project} key' card with masked key + Copy button and a
 * live 'last received' status line").
 *
 * **Data-honesty finding — "Copy" button replaced with "Manage key →":** this dashboard never
 * holds a project's full plaintext API key during a normal Overview visit.
 * `DashboardScreen.kt`'s `ProjectCard` already documents the reason: "the full key is never
 * recoverable — only a hash is stored server-side." The only 2 places a real plaintext key briefly
 * exists in [DashboardState] ([DashboardState.newProjectKeys]/[DashboardState.regeneratedKey]) are
 * transient, create/regenerate-modal-only state that is cleared well before a user would be
 * looking at Overview. [onManageKey] instead navigates to the Projects screen, where Copy/
 * Regenerate genuinely have a real key to act on — a smaller, second data-honesty finding in the
 * same spirit as `OverviewTopListsLogic.kt`'s "Worth a look" investigation, rather than a
 * functional-looking Copy button with nothing real to copy.
 *
 * **Data-honesty finding — status line:** no per-event "last received" timestamp exists anywhere
 * in this API (checked `AggregatesResponse`/`servers/ktor`/`php-hosting` — same kind of gap
 * `OverviewTopListsLogic.kt`'s investigation found for "Worth a look"'s rejected observations), so
 * [isReceivingData] drives a real binary signal instead of a fabricated recency claim: "🟢
 * Receiving data" when the current window has any real events, "⚪ No events yet this period"
 * otherwise, or "⚠️ Status unknown" while [serverUnreachable] (the header's
 * `ServerUnreachableBanner`, Task 17, already owns the connectivity problem itself; this just
 * avoids claiming a status it can't currently verify).
 *
 * [project] is null while projects haven't loaded yet (shows a skeleton); the masked key reuses
 * `ProjectCard`'s own "last 4 digits" convention ([ApiProject.apiKeyLast4]).
 */
@Composable
internal fun KeyCard(
    project: ApiProject?,
    isReceivingData: Boolean,
    serverUnreachable: Boolean,
    onManageKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = LocalExtendedColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = QmShapeTokens.card,
        border = BorderStroke(1.dp, extended.lime),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (project == null) {
                SkeletonBox(Modifier.width(140.dp).height(20.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(32.dp))
            } else {
                Text(
                    stringResource(Res.string.overview_key_card_title, project.name),
                    style = MaterialTheme.typography.titleSmall,
                )
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "•".repeat(16) + (project.apiKeyLast4 ?: stringResource(Res.string.value_none)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
                Text(
                    text = when {
                        serverUnreachable -> stringResource(Res.string.overview_key_card_status_unknown)
                        isReceivingData -> stringResource(Res.string.overview_key_card_status_receiving)
                        else -> stringResource(Res.string.overview_key_card_status_waiting)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(Res.string.overview_key_card_manage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(onClick = onManageKey).handCursor(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty-state setup instructions (Task 21)
// ---------------------------------------------------------------------------

/** Which language's install/init snippets [SetupInstructionsSection] shows — the design handoff's
 *  "shared Kotlin(KMP)/Swift/JavaScript language switch" (design_handoff_dashboard_redesign/
 *  README.md > Interactions & Behavior > "Empty state"). `internal` (not `private`) so Task 28's
 *  "＋ New funnel" instructional panel (`FunnelsScreen.kt`'s `NewFunnelGuideModal`) can reuse the
 *  exact same language switch rather than declaring a parallel enum. */
internal enum class SetupLanguage { Kotlin, Swift, JavaScript }

/**
 * Numbered first-run setup instructions shown instead of the 3-card row/"Worth a look"/key card
 * region when [isOverviewEmpty] is true (design_handoff_dashboard_redesign/README.md >
 * Interactions & Behavior > "Empty state": "numbered setup instructions (SDK install snippet,
 * init snippet, 'open your app' step) and a live 'waiting for first event' indicator; a shared
 * Kotlin(KMP)/Swift/JavaScript language switch controls every code snippet").
 *
 * Code snippets are shortened, real excerpts from `docs/sdk/android.md`/`docs/sdk/ios.md`/
 * `docs/sdk/web.md` — using those docs' own `qm_ak_your_api_key`/`https://your-server.com/api/v1`
 * placeholders, never a real API key (see [KeyCard]'s KDoc for why this dashboard doesn't hold one
 * to show here anyway). The "waiting for first event" indicator is a plain static line, not an
 * actual live-polling widget of its own — this screen already re-fetches [AggregatesResponse] on
 * every navigation/refresh, and once it returns any real event this whole section stops rendering
 * (replaced by [ScreensPlatformsCountriesSection]/[WorthALookSection]/[KeyCard]), so the "waiting"
 * framing stays true for as long as it's shown without this composable needing its own polling
 * loop.
 */
@Composable
internal fun SetupInstructionsSection(modifier: Modifier = Modifier) {
    var language by remember { mutableStateOf(SetupLanguage.Kotlin) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(Res.string.overview_empty_setup_title), style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SetupLanguage.entries.forEach { lang ->
                    val label = when (lang) {
                        SetupLanguage.Kotlin -> stringResource(Res.string.overview_empty_setup_lang_kotlin)
                        SetupLanguage.Swift -> stringResource(Res.string.overview_empty_setup_lang_swift)
                        SetupLanguage.JavaScript -> stringResource(Res.string.overview_empty_setup_lang_js)
                    }
                    Pill(
                        text = label,
                        variant = if (lang == language) PillVariant.Lavender else PillVariant.Neutral,
                        onClick = { language = lang },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.overview_empty_setup_step1_title), style = MaterialTheme.typography.titleSmall)
                SetupCodeBlock(
                    when (language) {
                        SetupLanguage.Kotlin -> stringResource(Res.string.overview_empty_setup_code_install_kotlin)
                        SetupLanguage.Swift -> stringResource(Res.string.overview_empty_setup_code_install_swift)
                        SetupLanguage.JavaScript -> stringResource(Res.string.overview_empty_setup_code_install_js)
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.overview_empty_setup_step2_title), style = MaterialTheme.typography.titleSmall)
                SetupCodeBlock(
                    when (language) {
                        SetupLanguage.Kotlin -> stringResource(Res.string.overview_empty_setup_code_init_kotlin)
                        SetupLanguage.Swift -> stringResource(Res.string.overview_empty_setup_code_init_swift)
                        SetupLanguage.JavaScript -> stringResource(Res.string.overview_empty_setup_code_init_js)
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.overview_empty_setup_step3_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.overview_empty_setup_step3_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                stringResource(Res.string.overview_empty_setup_waiting),
                style = MaterialTheme.typography.labelMedium,
                color = LocalExtendedColors.current.lime,
            )
        }
    }
}

/** Monospace code block for [SetupInstructionsSection] — same "tonal surface + padded monospace
 *  text" idiom `ExperimentsGuide.kt`'s private `CodeBlock` already establishes. `internal` (not
 *  `private`) so Task 28's `NewFunnelGuideModal` (`FunnelsScreen.kt`) genuinely reuses this
 *  exact composable for its own code snippets instead of a third, parallel duplicate. */
@Composable
internal fun SetupCodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
