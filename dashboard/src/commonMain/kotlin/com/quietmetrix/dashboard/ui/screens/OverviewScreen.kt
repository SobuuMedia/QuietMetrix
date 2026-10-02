package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ScreenDuration
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.TopEvent
import com.quietmetrix.dashboard.api.TopScreen
import com.quietmetrix.dashboard.api.TopValue
import com.quietmetrix.dashboard.format.formatBucketLabel
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatDuration
import com.quietmetrix.dashboard.format.formatMinorUnits
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.card_daily_volume
import com.quietmetrix.dashboard.resources.card_screen_time
import com.quietmetrix.dashboard.resources.card_sessions_trend
import com.quietmetrix.dashboard.resources.card_top_events
import com.quietmetrix.dashboard.resources.card_top_screens
import com.quietmetrix.dashboard.resources.card_top_values
import com.quietmetrix.dashboard.resources.ic_events
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_live
import com.quietmetrix.dashboard.resources.ic_overview
import com.quietmetrix.dashboard.resources.kpi_avg_duration
import com.quietmetrix.dashboard.resources.kpi_errors
import com.quietmetrix.dashboard.resources.kpi_events
import com.quietmetrix.dashboard.resources.kpi_offline_captured
import com.quietmetrix.dashboard.resources.kpi_sessions
import com.quietmetrix.dashboard.resources.overview_empty_greeting
import com.quietmetrix.dashboard.resources.overview_empty_headline_highlight
import com.quietmetrix.dashboard.resources.overview_empty_headline_lead
import com.quietmetrix.dashboard.resources.overview_empty_headline_tail
import com.quietmetrix.dashboard.resources.overview_error_ai_nap_body
import com.quietmetrix.dashboard.resources.overview_error_ai_nap_emoji
import com.quietmetrix.dashboard.resources.overview_error_ai_nap_title
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
import com.quietmetrix.dashboard.ui.components.charts.BarChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.BarData
import com.quietmetrix.dashboard.ui.components.charts.LineChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.LineData
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

            ResponsiveRowOrColumn(sizeClass) {
                when (contentStateFor(state.loading, agg == null)) {
                    ContentState.Loading -> {
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                    }
                    else -> {
                        KpiCard(stringResource(Res.string.kpi_events), formatCount(agg?.totals?.events ?: 0), Modifier.fillSlot())
                        KpiCard(stringResource(Res.string.kpi_offline_captured), formatCount(agg?.totals?.offline ?: 0), Modifier.fillSlot())
                        KpiCard(stringResource(Res.string.kpi_errors), formatCount(agg?.totals?.errors ?: 0), Modifier.fillSlot())
                    }
                }
            }

            ResponsiveRowOrColumn(sizeClass) {
                when (contentStateFor(state.loading, sess == null)) {
                    ContentState.Loading -> {
                        KpiSkeleton(Modifier.fillSlot())
                        KpiSkeleton(Modifier.fillSlot())
                    }
                    else -> {
                        KpiCard(stringResource(Res.string.kpi_sessions), formatCount((sess?.totalSessions ?: 0).toLong()), Modifier.fillSlot())
                        KpiCard(stringResource(Res.string.kpi_avg_duration), formatDuration(sess?.avgDurationSec ?: 0), Modifier.fillSlot())
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.card_daily_volume),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val daily = agg?.daily ?: emptyList()
                    if (daily.isEmpty()) {
                        EmptyState(Res.drawable.ic_events, Res.string.state_no_data)
                    } else {
                        BarChartCanvas(daily.map { BarData(formatBucketLabel(it.day, state.range.hourly), it.total.toFloat()) })
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.card_sessions_trend),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val sessDaily = sess?.dailySessions ?: emptyList()
                    if (sessDaily.isEmpty()) {
                        EmptyState(Res.drawable.ic_live, Res.string.state_no_data)
                    } else {
                        LineChartCanvas(sessDaily.map { LineData(formatBucketLabel(it.day, state.range.hourly), it.total.toFloat()) })
                    }
                }
            }

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
                            stringResource(Res.string.card_top_screens),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val top = agg?.topScreens ?: emptyList()
                        if (top.isEmpty()) {
                            EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
                        } else {
                            top.forEach { TopScreenRow(it) }
                        }
                    }
                }
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_screen_time),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val durations = agg?.screenDurations ?: emptyList()
                        if (durations.isEmpty()) {
                            EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
                        } else {
                            durations.forEach { ScreenDurationRow(it) }
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

@Composable
private fun TopScreenRow(item: TopScreen) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.screen ?: "(none)", modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodyMedium)
        Text(item.count.toString(), style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScreenDurationRow(item: ScreenDuration) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.screen ?: "(none)", modifier = Modifier.weight(1f),
             style = MaterialTheme.typography.bodyMedium)
        Text(formatDwell(item.avgMs), style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Human-readable dwell time: "8s", "1m 38s", "2m". */
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
