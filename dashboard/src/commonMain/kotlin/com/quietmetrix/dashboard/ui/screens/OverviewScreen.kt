package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
import com.quietmetrix.dashboard.resources.ask_overview_question
import com.quietmetrix.dashboard.resources.comparison_current
import com.quietmetrix.dashboard.resources.comparison_previous
import com.quietmetrix.dashboard.resources.ic_ask
import com.quietmetrix.dashboard.resources.ic_close
import com.quietmetrix.dashboard.resources.ic_copy
import com.quietmetrix.dashboard.resources.ic_events
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_lock
import com.quietmetrix.dashboard.resources.ic_twemoji_1f465
import com.quietmetrix.dashboard.resources.ic_twemoji_1f501
import com.quietmetrix.dashboard.resources.ic_twemoji_26a0
import com.quietmetrix.dashboard.resources.overview_breakdown_filter_cd
import com.quietmetrix.dashboard.resources.overview_card_countries_title
import com.quietmetrix.dashboard.resources.overview_card_platforms_title
import com.quietmetrix.dashboard.resources.overview_card_screens_title
import com.quietmetrix.dashboard.resources.overview_chart_metric_actions
import com.quietmetrix.dashboard.resources.overview_chart_metric_people
import com.quietmetrix.dashboard.resources.overview_chart_metric_visits
import com.quietmetrix.dashboard.resources.overview_chart_record_day
import com.quietmetrix.dashboard.resources.overview_chart_title_daily
import com.quietmetrix.dashboard.resources.overview_chart_title_hourly
import com.quietmetrix.dashboard.resources.overview_chart_title_weekly
import com.quietmetrix.dashboard.resources.overview_empty_greeting
import com.quietmetrix.dashboard.resources.overview_empty_headline_highlight
import com.quietmetrix.dashboard.resources.overview_empty_headline_lead
import com.quietmetrix.dashboard.resources.overview_empty_headline_tail
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
import com.quietmetrix.dashboard.resources.overview_error_ai_nap_body
import com.quietmetrix.dashboard.resources.overview_error_ai_nap_title
import com.quietmetrix.dashboard.resources.overview_error_headline
import com.quietmetrix.dashboard.resources.overview_filter_coverage
import com.quietmetrix.dashboard.resources.overview_hero_ai_chip
import com.quietmetrix.dashboard.resources.overview_hero_change_less
import com.quietmetrix.dashboard.resources.overview_hero_change_more
import com.quietmetrix.dashboard.resources.overview_hero_change_same
import com.quietmetrix.dashboard.resources.overview_hero_copied
import com.quietmetrix.dashboard.resources.overview_hero_copy
import com.quietmetrix.dashboard.resources.overview_hero_current_period
import com.quietmetrix.dashboard.resources.overview_hero_greeting
import com.quietmetrix.dashboard.resources.overview_hero_loading
import com.quietmetrix.dashboard.resources.overview_hero_opened
import com.quietmetrix.dashboard.resources.overview_hero_people
import com.quietmetrix.dashboard.resources.overview_hero_tail_standout
import com.quietmetrix.dashboard.resources.overview_hero_tell_me_more
import com.quietmetrix.dashboard.resources.overview_hero_unavailable
import com.quietmetrix.dashboard.resources.overview_key_card_manage
import com.quietmetrix.dashboard.resources.overview_key_card_status_receiving
import com.quietmetrix.dashboard.resources.overview_key_card_status_unknown
import com.quietmetrix.dashboard.resources.overview_key_card_status_waiting
import com.quietmetrix.dashboard.resources.overview_key_card_title
import com.quietmetrix.dashboard.resources.overview_privacy_body
import com.quietmetrix.dashboard.resources.overview_privacy_gdpr_chip
import com.quietmetrix.dashboard.resources.overview_privacy_info_cd
import com.quietmetrix.dashboard.resources.overview_privacy_no_banner_chip
import com.quietmetrix.dashboard.resources.overview_privacy_title
import com.quietmetrix.dashboard.resources.overview_screen_row_visits
import com.quietmetrix.dashboard.resources.overview_vital_crashes_label
import com.quietmetrix.dashboard.resources.overview_vital_crashes_reported_caption
import com.quietmetrix.dashboard.resources.overview_vital_crashes_suppressed_caption
import com.quietmetrix.dashboard.resources.overview_vital_crashes_unavailable
import com.quietmetrix.dashboard.resources.overview_vital_duration_approximate_caption
import com.quietmetrix.dashboard.resources.overview_vital_duration_label
import com.quietmetrix.dashboard.resources.overview_vital_duration_reported_caption
import com.quietmetrix.dashboard.resources.overview_vital_duration_suppressed_caption
import com.quietmetrix.dashboard.resources.overview_vital_filter_chip
import com.quietmetrix.dashboard.resources.overview_vital_people_caption
import com.quietmetrix.dashboard.resources.overview_vital_people_label
import com.quietmetrix.dashboard.resources.overview_vital_people_suppressed
import com.quietmetrix.dashboard.resources.overview_vital_people_unavailable
import com.quietmetrix.dashboard.resources.overview_vital_retention_caption
import com.quietmetrix.dashboard.resources.overview_vital_retention_label
import com.quietmetrix.dashboard.resources.overview_vital_retention_not_tracked
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
import com.quietmetrix.dashboard.resources.state_no_data
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.ui.components.ComparisonNote
import com.quietmetrix.dashboard.ui.components.ContentState
import com.quietmetrix.dashboard.ui.components.DashboardCard
import com.quietmetrix.dashboard.ui.components.EmojiVector
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
import com.quietmetrix.dashboard.ui.components.comparisonNoteText
import com.quietmetrix.dashboard.ui.components.contentStateFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.layoutFlowFor
import com.quietmetrix.dashboard.ui.components.responsiveDisplayStyle
import com.quietmetrix.dashboard.ui.components.screenGlyphFor
import com.quietmetrix.dashboard.viewmodel.ComparisonData
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.isLoading
import com.quietmetrix.dashboard.viewmodel.serverUnreachableError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import com.quietmetrix.dashboard.nav.NavDestination as AppTab
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

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
    MaxWidthContainer(sizeClass = sizeClass, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.demoMode) DemoBadge()

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Exclude detected bots", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.aggregates?.trafficClassAvailable == true) "Unclassified traffic stays included." else "Bot classification appears as new SDK data arrives.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.aggregates?.trafficClassSupported == true) {
                        val counts = state.aggregates?.trafficCounts.orEmpty()
                        fun countLabel(key: String): String {
                            val measure = counts[key]
                            return measure?.value?.let(::formatCount) ?: if (measure?.state == "suppressed") "suppressed" else "—"
                        }
                        Text(
                            "Detected bots: ${countLabel("detected_bot")} · Likely human: ${countLabel("likely_human")} · Unclassified: ${countLabel("unclassified")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = state.excludeDetectedBots,
                    onCheckedChange = viewModel::setExcludeDetectedBots,
                    enabled = state.aggregates?.trafficClassSupported == true && state.aggregates?.trafficClassAvailable == true,
                )
            }

            state.sectionErrors[DataSection.Aggregates]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }
            state.sectionErrors[DataSection.Sessions]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }

            val agg = state.aggregates
            val sess = state.sessions.takeIf { state.overviewFilter == null }
            state.aiInsight?.takeIf { it.mode == "ai" && !state.excludeDetectedBots }?.let { insight -> Text(insight.text, style = MaterialTheme.typography.bodyLarge) }
            val overviewQuestion = stringResource(Res.string.ask_overview_question)
    val projectName = state.projects.firstOrNull { it.id == state.currentProjectId }
                ?.name?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.value_none)

            // Hero + privacy card: a flexible hero column next to a fixed-width privacy card on
            // wide layouts (README > Overview: "minmax(0,1fr) minmax(280px,360px)" grid), stacked
            // on compact widths — the same breakpoint ResponsiveRowOrColumn uses elsewhere, but a
            // plain Row/Column here since the two children are NOT evenly weighted like a KPI row.
            if (layoutFlowFor(sizeClass) == LayoutFlow.Row) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp), modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp)) {
                    OverviewHero(
                        projectName = projectName,
                        range = state.range,
                        agg = agg,
                        sess = sess,
                        previous = state.comparison.aggregates.takeIf { state.compare },
                        loading = state.isLoading(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention),
                        serverUnreachable = state.serverUnreachableError != null,
                        onTellMeMore = { viewModel.openAskDrawer(overviewQuestion) },
                        modifier = Modifier.weight(1f),
                    )
                    PrivacyCard(modifier = Modifier.width(320.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp)) {
                    OverviewHero(
                        projectName = projectName,
                        range = state.range,
                        agg = agg,
                        sess = sess,
                        previous = state.comparison.aggregates.takeIf { state.compare },
                        loading = state.isLoading(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention),
                        serverUnreachable = state.serverUnreachableError != null,
                        onTellMeMore = { viewModel.openAskDrawer(overviewQuestion) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrivacyCard(modifier = Modifier.fillMaxWidth())
                }
            }

            // Task 19: the redesigned 4-vital grid (People/Time per visit/Came back next
            // day/Crashes — see VitalsSection's KDoc) replaces the pre-redesign 5-card Events/
            // Offline-captured/Errors/Sessions/Avg-duration rows above. Events and
            // Offline-captured are no longer shown as standalone vitals cards per the new
            // design (they aren't among its 4 vitals); events remain visible via the daily
            // volume chart below.
            val resolvedEmpty = state.overviewFilter == null && (agg != null || sess != null) && isOverviewEmpty(agg, sess) &&
                !state.isLoading(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention) &&
                state.sectionErrors.keys.none { it in setOf(DataSection.Aggregates, DataSection.Sessions) }
            if (resolvedEmpty) {
                SetupInstructionsSection(Modifier.fillMaxWidth())
            } else {
            val activeVitalsFilter = state.overviewFilter?.value
            VitalsSection(
                agg = agg,
                sess = sess,
                retention = state.retention,
                loading = state.isLoading(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention),
                sizeClass = sizeClass,
                activeFilter = activeVitalsFilter,
                onClearFilter = { viewModel.setOverviewFilter() },
                onDrillDown = { kind, key -> viewModel.openDrillDown(kind, key) },
                compare = state.compare,
                previous = state.comparison,
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
                sizeClass = sizeClass,
                compare = state.compare,
                previous = state.comparison,
                anchor = state.analyticsAnchor,
                onDayClick = { kind, date -> viewModel.openDrillDown(kind, date) },
                loading = state.isLoading(DataSection.Aggregates, DataSection.Sessions),
                modifier = Modifier.fillMaxWidth(),
            )

                ScreensPlatformsCountriesSection(
                    agg = state.overviewBaseAggregates ?: agg,
                    loading = state.isLoading(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention),
                    sizeClass = sizeClass,
                    trafficFiltered = state.excludeDetectedBots,
                    activeFilter = activeVitalsFilter,
                    onSelectFilter = { kind, value -> viewModel.setOverviewFilter(kind, if (kind == "platform") value.lowercase() else if (kind == "country") (if (value.lowercase() == "unknown") "unknown" else value.uppercase()) else value) },
                compare = state.compare,
                previous = state.comparison.aggregates,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.excludeDetectedBots) {
                    Text("Additional insights are unavailable for the bot-filtered view yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (layoutFlowFor(sizeClass) == LayoutFlow.Row) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        WorthALookSection(
                            agg = agg,
                            dismissedIds = state.dismissedWorthALookIds,
                            loading = state.isLoading(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention),
                            onDismiss = { viewModel.hideWorthALook(it, snooze = false) },
                            onSnooze = { viewModel.hideWorthALook(it, snooze = true) },
                            modifier = Modifier.weight(2f),
                        )
                        KeyCard(
                            project = state.projects.firstOrNull { it.id == state.currentProjectId },
                            isReceivingData = (agg?.totals?.events ?: 0L) > 0L,
                            serverUnreachable = state.serverUnreachableError != null,
                            onManageKey = { viewModel.navigateTo(AppTab.Projects) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        WorthALookSection(
                            agg = agg,
                            dismissedIds = state.dismissedWorthALookIds,
                            loading = state.isLoading(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention),
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
private fun TopEventRow(item: TopEvent, compare: Boolean = false, previous: Long? = null) {
    Column {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.eventName, modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodyMedium)
        Text(item.count.toString(), style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (compare) ComparisonNote(previous?.let(::formatCount))
    }
}

@Composable
private fun TopValueRow(item: TopValue, compare: Boolean = false, previous: Long? = null) {
    Column {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.name, modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodyMedium)
        Text(formatMinorUnits(item.totalMinorUnits), style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (compare) ComparisonNote(previous?.let(::formatMinorUnits))
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
 * The Overview hero: a state-dependent greeting + large headline. Normal shows a computed
 * computed plain-language summary with lime-highlighted numbers ([OverviewHeroNormal]); Loading shows a
 * contextual loading line ([OverviewHeroLoading]); Empty shows a first-run setup prompt
 * ([OverviewHeroEmpty]); and while the server is unreachable, a plain numbers-may-be-stale
 * headline plus a summary-unavailable notice ([OverviewHeroError]) — the header-level
 * `ServerUnreachableBanner` (`DashboardShell.kt`, Task 17) already communicates the connection
 * problem itself, so this branch only avoids claiming a fresh AI summary it can't produce right
 * now, rather than duplicating that message.
 *
 * [agg]/[sess] feed [computeHeroNumbers] for the Normal headline's numbers. People and its
 * previous-period comparison come from exact backend measures; unavailable measures use neutral
 * copy rather than proxying sessions or daily event totals.
 */
@Composable
internal fun OverviewHero(
    projectName: String,
    range: TimeRange,
    agg: AggregatesResponse?,
    sess: SessionsResponse?,
    previous: AggregatesResponse? = null,
    loading: Boolean,
    serverUnreachable: Boolean,
    onTellMeMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hero = computeHeroNumbers(agg, sess, previous)
    val isEmpty = hero == null || !hero.hasActivity

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            serverUnreachable -> OverviewHeroError(projectName, range)
            loading && sess == null -> OverviewHeroLoading()
            sess == null && agg != null && hero == null -> OverviewHeroError(projectName, range)
            else -> when (contentStateFor(loading, isEmpty)) {
                ContentState.Loading -> OverviewHeroLoading()
                ContentState.Empty -> OverviewHeroEmpty(projectName)
                ContentState.Data -> OverviewHeroNormal(hero!!, projectName, range, onTellMeMore)
            }
        }
    }
}

/** Normal-state hero content: greeting, computed headline and source attribution. */
@Composable
private fun OverviewGreeting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        EmojiVector("👋", Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverviewHeroNormal(hero: HeroNumbers, projectName: String, range: TimeRange, onTellMeMore: () -> Unit) {
    val ai = com.quietmetrix.dashboard.ui.components.LocalAiAccess.current
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val extended = LocalExtendedColors.current
    val lime = SpanStyle(background = extended.lime, color = extended.onLime)

    OverviewGreeting(stringResource(Res.string.overview_hero_greeting, projectName, stringResource(range.labelRes)))

    val openedPhrase = stringResource(Res.string.overview_hero_opened, projectName)
    val peopleCount = hero.peopleCount
    val peopleText = peopleCount?.let { stringResource(Res.string.overview_hero_people, formatCount(it)) }
    val currentPeriod = stringResource(Res.string.overview_hero_current_period)
    val comparison = hero.changePercent?.let { change ->
        when {
            change > 0 -> stringResource(Res.string.overview_hero_change_more, "${change}%")
            change < 0 -> stringResource(Res.string.overview_hero_change_less, "${-change}%")
            else -> stringResource(Res.string.overview_hero_change_same)
        }
    }
    val eventTail = hero.standoutEvent?.let { stringResource(Res.string.overview_hero_tail_standout, it) }
    val sentence = if (peopleCount == null) {
        androidx.compose.ui.text.AnnotatedString(stringResource(Res.string.overview_hero_unavailable))
    } else buildAnnotatedString {
        withStyle(lime) { append(peopleText.orEmpty()) }
        append(" ")
        append(openedPhrase)
        append(if (comparison == null) " $currentPeriod" else ", ")
        if (comparison != null) withStyle(lime) { append(comparison) }
        if (eventTail != null) {
            append(" ")
            append(eventTail)
        }
    }
    BoxWithConstraints {
        val headline = responsiveDisplayStyle(maxWidth)
        Text(sentence, style = headline, color = MaterialTheme.colorScheme.onBackground)
    }

    val aiChip = stringResource(Res.string.overview_hero_ai_chip)
    val tellMeMore = stringResource(Res.string.overview_hero_tell_me_more)
    val copyLabel = stringResource(if (copied) Res.string.overview_hero_copied else Res.string.overview_hero_copy)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = extended.chipFill, shape = QmShapeTokens.pill) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(painterResource(Res.drawable.ic_ask), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Text(aiChip, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (ai.enabled) Text(
            tellMeMore,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.handCursor().clickable(enabled = ai.available, onClick = onTellMeMore),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier
            .handCursor()
            .clickable {
                clipboard.setText(AnnotatedString(sentence.toString()))
                copied = true
                scope.launch { delay(2_000); copied = false }
            }
            .semantics { contentDescription = copyLabel }) {
            Icon(painterResource(Res.drawable.ic_copy), contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
            Text(copyLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** Loading-state hero content: just the contextual loading line (`_source/02-overview-loading.png`). */
@Composable
private fun OverviewHeroLoading() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(Res.drawable.ic_events), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(stringResource(Res.string.overview_hero_loading), style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** First-run hero content: a welcome line + a headline prompting SDK setup (the numbered
 *  setup instructions themselves are Task 21's "Worth a look"/key-card scope, not this task's). */
@Composable
private fun OverviewHeroEmpty(projectName: String) {
    val extended = LocalExtendedColors.current
    OverviewGreeting(stringResource(Res.string.overview_empty_greeting, projectName))
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
    BoxWithConstraints {
        val headline = responsiveDisplayStyle(maxWidth)
        Text(sentence, style = headline, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Errors-state hero content: the usual greeting + a plain (non-AI, no lime highlights) headline,
 *  plus an "AI summary unavailable" notice — the connection problem itself is the header banner's
 *  job (`ServerUnreachableBanner`, Task 17), not repeated here. */
@Composable
private fun OverviewHeroError(projectName: String, range: TimeRange) {
    val extended = LocalExtendedColors.current
    val rangeLabel = stringResource(range.labelRes)
    OverviewGreeting(stringResource(Res.string.overview_hero_greeting, projectName, rangeLabel))
    BoxWithConstraints {
        val headline = responsiveDisplayStyle(maxWidth)
        Text(
            stringResource(Res.string.overview_error_headline, rangeLabel),
            style = headline,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    Surface(
        color = extended.coral.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, extended.coral.copy(alpha = 0.4f)),
        shape = QmShapeTokens.chip,
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EmojiVector("💤", Modifier.size(28.dp))
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
                Icon(painterResource(Res.drawable.ic_lock), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                val infoCd = stringResource(Res.string.overview_privacy_info_cd)
                Surface(color = extended.chipFill, shape = QmShapeTokens.pill) {
                    Icon(painterResource(Res.drawable.ic_info), contentDescription = infoCd,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp).size(14.dp).semantics { contentDescription = infoCd })
                }
            }
            Text(stringResource(Res.string.overview_privacy_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.overview_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(stringResource(Res.string.overview_privacy_gdpr_chip), PillVariant.Neutral,
                    leadingIcon = Res.drawable.ic_lock)
                Pill(stringResource(Res.string.overview_privacy_no_banner_chip), PillVariant.Neutral)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Vitals grid (Task 19)
// ---------------------------------------------------------------------------

/**
 * The redesigned Overview vitals grid: 4 [KpiCard]s — People, Time per visit, Came back next
 * day, Crashes — laid out via [ResponsiveRowOrColumn], preceded by an optional lavender "Showing
 * only {X} ✕" filter chip (design_handoff_dashboard_redesign/README.md > Overview: "Clicking any
 * of these three [screens/platforms/countries, a later task's 3-card row] shows a lavender
 * 'Showing only {X} ✕' filter chip above the vitals grid"). People is read only from the exact
 * fixed-window API field; session duration is explicitly marked approximate; crash-only data is
 * unavailable until the backend reports it separately. Compare notes use separately loaded
 * previous UTC windows and disappear when those values are unavailable.
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
    onDrillDown: (String, String) -> Unit = { _, _ -> },
    compare: Boolean = false,
    previous: ComparisonData = ComparisonData(),
) {
    val vitals = computeVitalsNumbers(retention)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (activeFilter != null) {
            Pill(
                text = stringResource(Res.string.overview_vital_filter_chip, activeFilter),
                variant = PillVariant.Lavender,
                onClick = onClearFilter,
                trailingIcon = Res.drawable.ic_close,
            )
            Text(stringResource(Res.string.overview_filter_coverage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val reportedPeople = agg?.people
                    val resolvedDuration = resolveOverviewDuration(agg?.v2VisitDurationMs, agg?.v2VisitDurationState, sess?.avgDurationSec?.takeIf { (sess?.totalSessions ?: 0) > 0 })
                    val durationLabel = if (agg?.v2VisitDurationMs?.let { it in 1L..999L } == true && agg?.v2VisitDurationState?.let { it in setOf("reported", "zero") } == true) "<1s"
                        else resolvedDuration.seconds?.let(::formatDuration) ?: stringResource(Res.string.value_none)
                    val peopleMetricAvailable = reportedPeople != null
                    val peopleCaption = when {
                        agg?.peopleState == "suppressed" -> Res.string.overview_vital_people_suppressed
                        peopleMetricAvailable -> Res.string.overview_vital_people_caption
                        else -> Res.string.overview_vital_people_unavailable
                    }
                    KpiCard(
                        label = stringResource(Res.string.overview_vital_people_label),
                        value = reportedPeople?.let(::formatCount) ?: stringResource(Res.string.value_none),
                        sub = stringResource(peopleCaption),
                        leadingIcon = Res.drawable.ic_twemoji_1f465,
                        onClick = { onDrillDown("people", "people") },
                        compareNote = if (!compare) null else if (agg?.peopleSource == "v2") {
                            comparisonNoteText(agg.peopleComparison?.let(::formatCount))
                        } else comparisonNoteText(previous.aggregates?.people?.let(::formatCount)),
                        modifier = Modifier.fillSlot(),
                    )
                    KpiCard(
                        label = stringResource(Res.string.overview_vital_duration_label),
                        value = durationLabel,
                        sub = stringResource(when (resolvedDuration.source) {
                            OverviewMeasureSource.ReportedV2 -> Res.string.overview_vital_duration_reported_caption
                            OverviewMeasureSource.Suppressed -> Res.string.overview_vital_duration_suppressed_caption
                            OverviewMeasureSource.LegacyApproximate -> Res.string.overview_vital_duration_approximate_caption
                            else -> Res.string.overview_vital_people_unavailable
                        }),
                        leadingIcon = Res.drawable.ic_events,
                        onClick = { onDrillDown("duration", "duration") },
                        compareNote = if (compare) comparisonNoteText(comparableDuration(
                            resolveOverviewDuration(agg?.v2VisitDurationMs, agg?.v2VisitDurationState, sess?.avgDurationSec?.takeIf { (sess?.totalSessions ?: 0) > 0 }),
                            resolveOverviewDuration(previous.aggregates?.v2VisitDurationMs, previous.aggregates?.v2VisitDurationState, previous.sessions?.avgDurationSec?.takeIf { (previous.sessions?.totalSessions ?: 0) > 0 }),
                        )?.let(::formatDuration)) else null,
                        modifier = Modifier.fillSlot(),
                    )
                    val retentionDay1 = vitals?.retentionDay1
                    val retentionValue = retentionDay1?.let { formatPercent(it) } ?: stringResource(Res.string.value_none)
                    val retentionCaption = if (retentionDay1 != null) {
                        stringResource(Res.string.overview_vital_retention_caption)
                    } else {
                        stringResource(Res.string.overview_vital_retention_not_tracked)
                    }
                    KpiCard(
                        label = stringResource(Res.string.overview_vital_retention_label),
                        value = retentionValue,
                        sub = retentionCaption,
                        leadingIcon = Res.drawable.ic_twemoji_1f501,
                        onClick = { onDrillDown("retention", "day1") },
                        compareNote = if (compare) comparisonNoteText(computeRetentionVitals(previous.retention).day1?.let(::formatPercent)) else null,
                        modifier = Modifier.fillSlot(),
                    )
                    KpiCard(
                        label = stringResource(Res.string.overview_vital_crashes_label),
                        value = resolveOverviewCrashes(agg?.v2Crashes, agg?.v2CrashesState).count
                            ?.let(::formatCount) ?: stringResource(Res.string.value_none),
                        sub = stringResource(when (resolveOverviewCrashes(agg?.v2Crashes, agg?.v2CrashesState).source) {
                            OverviewMeasureSource.ReportedV2 -> Res.string.overview_vital_crashes_reported_caption
                            OverviewMeasureSource.Suppressed -> Res.string.overview_vital_crashes_suppressed_caption
                            OverviewMeasureSource.LegacyApproximate -> Res.string.overview_vital_crashes_unavailable
                            OverviewMeasureSource.Unavailable -> Res.string.overview_vital_crashes_unavailable
                        }),
                        leadingIcon = Res.drawable.ic_twemoji_26a0,
                        onClick = { onDrillDown("crashes", "crashes") },
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
 * [TimeRange]'s bucket granularity, a People/Visits/Actions segmented control, and a [BarChartCanvas] of
 * the selected series with the real "record day" highlighted in lime. Replaces the two
 * pre-redesign cards ("Daily volume" and "Daily sessions") that stood side by side in
 * `OverviewScreen` — the design handoff describes one card with a metric toggle, not two
 * permanently-stacked charts (design_handoff_dashboard_redesign/README.md > Overview).
 *
 * The three-way segmented control matches the handoff. People uses the report endpoint's
 * privacy-gated distinct-installation bins; the other tabs use session and event totals. On
 * compact phones the title and tabs stack so all three choices remain visible without clipping.
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
 * Bars open their stable date key in the shared drawer. Compare renders separately fetched,
 * aligned previous-period values as dashed lavender bars and a legend.
 *
 * Empty state (no buckets for the selected metric) shows the existing [EmptyState] placeholder,
 * while pending requests show a chart skeleton.
 */
@Composable
internal fun DailyVolumeSection(
    agg: AggregatesResponse?,
    sess: SessionsResponse?,
    range: TimeRange,
    sizeClass: WindowSizeClass = WindowSizeClass.Expanded,
    modifier: Modifier = Modifier,
    compare: Boolean = false,
    previous: ComparisonData = ComparisonData(),
    anchor: String = com.quietmetrix.dashboard.viewmodel.analyticsAnchorToday(),
    onDayClick: (String, String) -> Unit = { _, _ -> },
    loading: Boolean = false,
) {
    var metric by remember { mutableStateOf(DailyVolumeMetric.People) }

    DashboardCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            val chartTitle: @Composable () -> Unit = {
                Text(
                    text = stringResource(when {
                        range == TimeRange.Day90 -> Res.string.overview_chart_title_weekly
                        range.hourly -> Res.string.overview_chart_title_hourly
                        else -> Res.string.overview_chart_title_daily
                    }),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sizeClass == WindowSizeClass.Compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    chartTitle()
                    MetricSegmentedControl(selected = metric, onSelect = { metric = it }, modifier = Modifier.align(Alignment.End))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { chartTitle() }
                    MetricSegmentedControl(selected = metric, onSelect = { metric = it })
                }
            }
            Spacer(Modifier.height(8.dp))

            val hasReport = agg?.v2VisitDurationState != null
            val currentPoints = when (metric) {
                DailyVolumeMetric.People -> agg?.dailyPeople.orEmpty()
                DailyVolumeMetric.Visits -> if (hasReport) agg?.dailyVisitsV2.orEmpty() else sess?.dailySessions.orEmpty()
                DailyVolumeMetric.Actions -> if (hasReport) agg?.dailyActionsV2.orEmpty() else agg?.daily.orEmpty()
            }
            val previousPoints = when (metric) {
                DailyVolumeMetric.People -> agg?.dailyPeoplePrevious.orEmpty()
                DailyVolumeMetric.Visits -> if (hasReport) agg?.dailyVisitsV2Previous.orEmpty() else previous.sessions?.dailySessions.orEmpty()
                DailyVolumeMetric.Actions -> if (hasReport) agg?.dailyActionsV2Previous.orEmpty() else previous.aggregates?.daily.orEmpty()
            }
            val points = analyticsChartPoints(currentPoints, range, anchor)
            val priorPoints = analyticsChartPoints(previousPoints, range, anchor, previous = true)
            val bars = points.map { DailyVolumeBar(formatBucketLabel(it.day, false), it.total) }
            val metricName = when (metric) {
                DailyVolumeMetric.People -> stringResource(Res.string.overview_chart_metric_people)
                DailyVolumeMetric.Visits -> stringResource(Res.string.overview_chart_metric_visits)
                DailyVolumeMetric.Actions -> stringResource(Res.string.overview_chart_metric_actions)
            }
            val metricCount = when (metric) {
                DailyVolumeMetric.People -> agg?.people
                DailyVolumeMetric.Visits -> if (hasReport) agg?.v2Visits else sess?.totalSessions
                DailyVolumeMetric.Actions -> if (hasReport) agg?.v2Actions else agg?.totals?.events
            }
            metricCount?.let { count ->
                Text("$metricName · ${formatCount(count)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (compare) {
                Text(stringResource(Res.string.comparison_current) + " / " + stringResource(Res.string.comparison_previous),
                    style = MaterialTheme.typography.labelSmall, color = LocalExtendedColors.current.lavender)
                ComparisonNote(if (previousPoints.isEmpty()) null else formatCount(previousPoints.sumOf { it.total }))
            }
            if (loading && bars.isEmpty()) {
                SkeletonBox(Modifier.fillMaxWidth().height(220.dp))
            } else if (bars.isEmpty()) {
                EmptyState(Res.drawable.ic_events, Res.string.state_no_data)
            } else {
                val recordIndex = recordDayIndex(bars)
                BarChartCanvas(
                    bars = bars.map { BarData(it.label, it.value.toFloat()) },
                    highlightIndex = recordIndex,
                    previousValues = if (compare) comparisonDailyValues(points, priorPoints, ((range.seconds + 86399) / 86400).toInt()) else emptyList(),
                    onBarClick = { index ->
                        val kind = when (metric) {
                            DailyVolumeMetric.People -> "day-people"
                            DailyVolumeMetric.Visits -> "day-visits"
                            DailyVolumeMetric.Actions -> "day-actions"
                        }
                        onDayClick(kind, points[index].day)
                    },
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
                DailyVolumeMetric.People -> stringResource(Res.string.overview_chart_metric_people)
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
    trafficFiltered: Boolean = false,
    modifier: Modifier = Modifier,
    activeFilter: String? = null,
    onSelectFilter: (String, String) -> Unit = { _, _ -> },
    compare: Boolean = false,
    previous: AggregatesResponse? = null,
) {
    if (trafficFiltered) {
        Text("Breakdowns are unavailable for the bot-filtered view yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    } else ResponsiveRowOrColumn(sizeClass, modifier = modifier) {
        if (loading) {
            TopListSkeletonCard(Modifier.fillSlot())
            TopListSkeletonCard(Modifier.fillSlot())
            TopListSkeletonCard(Modifier.fillSlot())
        } else {
            DashboardCard(modifier = Modifier.fillSlot()) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmojiVector("📍", Modifier.size(22.dp))
                        Text(stringResource(Res.string.overview_card_screens_title), style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    val rows = computeScreenUsage(agg)
                    if (rows.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmojiVector("📱", Modifier.size(24.dp))
                            Text(stringResource(Res.string.state_no_data), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        rows.forEach { row ->
                            ScreenUsageRowItem(row, selected = row.screen == activeFilter, onClick = { onSelectFilter("screen", row.screen) })
                            if (compare) ComparisonNote(previous?.topScreens?.firstOrNull { it.screen == row.screen }?.count?.let(::formatCount))
                        }
                    }
                }
            }
            DashboardCard(modifier = Modifier.fillSlot()) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmojiVector("📱", Modifier.size(22.dp))
                        Text(stringResource(Res.string.overview_card_platforms_title), style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    val rows = computeBreakdownRows(agg?.platforms.orEmpty())
                    if (rows.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmojiVector("📱", Modifier.size(24.dp))
                            Text(stringResource(Res.string.state_no_data), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        rows.forEach { row ->
                            BreakdownRowItem(row, selected = row.name == activeFilter, icon = breakdownGlyph(row.name), onClick = { onSelectFilter("platform", row.name) })
                            if (compare) ComparisonNote(previous?.platforms?.firstOrNull { it.displayName == row.name }?.count?.let(::formatCount))
                        }
                    }
                }
            }
            DashboardCard(modifier = Modifier.fillSlot()) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmojiVector("🌍", Modifier.size(22.dp))
                        Text(stringResource(Res.string.overview_card_countries_title), style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    val rows = computeBreakdownRows(agg?.countries.orEmpty())
                    if (rows.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmojiVector("🌍", Modifier.size(24.dp))
                            Text(stringResource(Res.string.state_no_data), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        rows.forEach { row ->
                            BreakdownRowItem(row, selected = row.name == activeFilter, icon = "🌍", onClick = { onSelectFilter("country", row.name) })
                            if (compare) ComparisonNote(previous?.countries?.firstOrNull { it.displayName == row.name }?.count?.let(::formatCount))
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmojiVector(screenGlyphFor(row.screen), Modifier.size(20.dp))
                Text(row.screen, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else null)
            }
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
private fun breakdownGlyph(name: String): String = when (name.lowercase()) {
    "android" -> "🤖"
    "ios" -> "🍎"
    "web" -> "🌐"
    "desktop", "computer" -> "🖥️"
    else -> "🌍"
}

@Composable
private fun BreakdownRowItem(row: BreakdownRow, selected: Boolean, icon: String, onClick: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmojiVector(icon, Modifier.size(20.dp))
                Text(row.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else null)
            }
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
    DashboardCard(modifier = modifier) {
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
    DashboardCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmojiVector("👀", Modifier.size(22.dp))
                Text(stringResource(Res.string.overview_worth_a_look_title), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            if (loading) {
                SkeletonBox(Modifier.fillMaxWidth().height(40.dp))
            } else {
                val observations = computeWorthALookObservations(agg).filter { it.id !in dismissedIds }
                if (observations.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        EmojiVector("🎈", Modifier.size(24.dp))
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
        EmojiVector(emoji, Modifier.size(26.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EmojiVector(
            "💤",
            modifier = Modifier
                .clickable(onClick = onSnooze)
                .handCursor()
                .padding(6.dp)
                .size(28.dp)
                .semantics { contentDescription = snoozeCd },
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.handCursor().semantics { contentDescription = dismissCd },
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
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
        color = extended.lime,
        contentColor = extended.onLime,
        shape = QmShapeTokens.card,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (project == null) {
                SkeletonBox(Modifier.width(140.dp).height(20.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(32.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmojiVector("🔑", Modifier.size(22.dp))
                    Text(stringResource(Res.string.overview_key_card_title, project.name), style = MaterialTheme.typography.titleSmall)
                }
                Surface(color = extended.onLime.copy(alpha = 0.08f), contentColor = extended.onLime, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "•".repeat(16) + (project.apiKeyLast4 ?: stringResource(Res.string.value_none)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmojiVector(when { serverUnreachable -> "⚠"; isReceivingData -> "🟢"; else -> "⚪" }, Modifier.size(16.dp))
                    Text(
                        text = when {
                            serverUnreachable -> stringResource(Res.string.overview_key_card_status_unknown)
                            isReceivingData -> stringResource(Res.string.overview_key_card_status_receiving)
                            else -> stringResource(Res.string.overview_key_card_status_waiting)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.onLime.copy(alpha = 0.78f),
                    )
                }
                Surface(
                    color = extended.onLime,
                    contentColor = extended.lime,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.clickable(onClick = onManageKey).handCursor(),
                ) {
                    Text(
                        stringResource(Res.string.overview_key_card_manage),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty-state setup instructions (Task 21)
// ---------------------------------------------------------------------------

/** Which language's install/init snippets [SetupInstructionsSection] shows. */
private enum class SetupLanguage { Kotlin, Swift, JavaScript }

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

    DashboardCard(modifier = modifier) {
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

/** Monospace code block for [SetupInstructionsSection], using a tonal surface and padded code text. */
@Composable
private fun SetupCodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
