package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.FrictionScreenItem
import com.quietmetrix.dashboard.api.SearchScreenItem
import com.quietmetrix.dashboard.api.Transition
import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatPercent
import com.quietmetrix.dashboard.format.formatWilsonRange
import com.quietmetrix.dashboard.format.wilsonInterval
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.card_countries
import com.quietmetrix.dashboard.resources.card_device_classes
import com.quietmetrix.dashboard.resources.card_friction
import com.quietmetrix.dashboard.resources.card_platforms
import com.quietmetrix.dashboard.resources.card_search_zero_result
import com.quietmetrix.dashboard.resources.col_count
import com.quietmetrix.dashboard.resources.col_from
import com.quietmetrix.dashboard.resources.col_rage_taps
import com.quietmetrix.dashboard.resources.col_screen
import com.quietmetrix.dashboard.resources.col_to
import com.quietmetrix.dashboard.resources.col_zero_result_rate
import com.quietmetrix.dashboard.resources.flow_after_screen
import com.quietmetrix.dashboard.resources.flow_caption
import com.quietmetrix.dashboard.resources.flow_exit_label
import com.quietmetrix.dashboard.resources.flow_h1_highlight
import com.quietmetrix.dashboard.resources.flow_h1_lead
import com.quietmetrix.dashboard.resources.flow_h1_tail
import com.quietmetrix.dashboard.resources.flow_path_arrow
import com.quietmetrix.dashboard.resources.flow_path_label
import com.quietmetrix.dashboard.resources.flow_platform_all
import com.quietmetrix.dashboard.resources.flow_platform_android
import com.quietmetrix.dashboard.resources.flow_platform_ios
import com.quietmetrix.dashboard.resources.flow_platform_label
import com.quietmetrix.dashboard.resources.flow_platform_not_tracked_cd
import com.quietmetrix.dashboard.resources.flow_platform_web
import com.quietmetrix.dashboard.resources.flow_start_from_label
import com.quietmetrix.dashboard.resources.flow_state_empty_path
import com.quietmetrix.dashboard.resources.ic_flow
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.nav_flow
import com.quietmetrix.dashboard.resources.state_no_transitions
import com.quietmetrix.dashboard.resources.tab_info_content_description
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.charts.DonutChart
import com.quietmetrix.dashboard.ui.components.charts.DonutData
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.table.Column
import com.quietmetrix.dashboard.ui.components.table.DataTable
import com.quietmetrix.dashboard.ui.components.table.SortColumn
import com.quietmetrix.dashboard.ui.components.table.screenLabel
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.quietmetrix.dashboard.nav.NavDestination as AppTab

// ---------------------------------------------------------------------------
// Task 22: Flow screen. Extracted from `DashboardScreen.kt`'s `FlowTab` (see that file's git
// history). Adds the design handoff's breadcrumb/"Start from"/platform-filter header
// (design_handoff_dashboard_redesign/README.md > Flow) on top of the existing transitions/search/
// friction tables and countries/platforms/device-class donut charts, which are kept unchanged
// below — restyling that lower "3-card row" to the literal bar-list/stat-tile/progress-bar spec
// is left to a later Flow task, matching how the Overview phase split hero/vitals/chart/top-lists
// across Tasks 18-21. See `FlowPathLogic.kt`'s top-of-file KDoc for the data-honesty investigation
// behind the breadcrumb's scope (what's real vs. what the design asks for that the backend can't
// back).
// ---------------------------------------------------------------------------

/** The Flow screen itself — see this file's top-of-file KDoc for scope. */
@Composable
internal fun FlowScreen(viewModel: DashboardViewModel, state: DashboardState, sizeClass: WindowSizeClass) {
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
                IconButton(
                    onClick = { viewModel.showTabInfo(AppTab.Flow) },
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

            state.sectionErrors[DataSection.Transitions]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }

            FlowHero()
            PlatformFilterRow()

            val transitions = state.transitions?.transitions ?: emptyList()
            val startScreens = remember(transitions) { topStartScreens(transitions) }
            var flowPath by remember { mutableStateOf<List<String>>(emptyList()) }

            StartFromRow(
                screens = startScreens,
                selected = flowPath.firstOrNull(),
                onPick = { flowPath = listOf(it) },
            )
            FlowBreadcrumbSection(
                path = flowPath,
                transitions = transitions,
                onPick = { option -> extendFlowPath(flowPath, option)?.let { flowPath = it } },
            )

            if (transitions.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(Res.drawable.ic_flow, Res.string.state_no_transitions, Modifier.padding(16.dp))
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val transitionNone = stringResource(Res.string.value_none)
                        DataTable(
                            columns = listOf(
                                Column(
                                    title = stringResource(Res.string.col_from),
                                    weight = 1f,
                                    cell = { t: Transition -> Text(screenLabel(t.fromScreen, transitionNone), style = MaterialTheme.typography.bodyMedium) },
                                    sortSelector = { it.fromScreen },
                                ),
                                Column(
                                    title = stringResource(Res.string.col_to),
                                    weight = 1f,
                                    cell = { t: Transition -> Text(screenLabel(t.toScreen, transitionNone), style = MaterialTheme.typography.bodyMedium) },
                                    sortSelector = { it.toScreen },
                                ),
                                Column(
                                    title = stringResource(Res.string.col_count),
                                    weight = 0.6f,
                                    cell = { t: Transition ->
                                        Text(
                                            formatCount(t.count),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    sortSelector = { it.count },
                                ),
                            ),
                            rows = transitions,
                            sizeClass = sizeClass,
                            initialSort = SortColumn(2, ascending = false),
                            rowKey = { it.fromScreen to it.toScreen },
                            modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                        )
                    }
                }
            }

            val searchScreens = state.search?.screens ?: emptyList()
            if (searchScreens.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(Res.string.card_search_zero_result),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                        )
                        DataTable(
                            columns = listOf(
                                Column(
                                    title = stringResource(Res.string.col_screen),
                                    weight = 1f,
                                    cell = { s: SearchScreenItem -> Text(s.screen, style = MaterialTheme.typography.bodyMedium) },
                                    sortSelector = { it.screen },
                                ),
                                Column(
                                    title = stringResource(Res.string.col_count),
                                    weight = 0.6f,
                                    cell = { s: SearchScreenItem -> Text(formatCount(s.total), style = MaterialTheme.typography.bodyMedium) },
                                    sortSelector = { it.total },
                                ),
                                Column<SearchScreenItem>(
                                    title = stringResource(Res.string.col_zero_result_rate),
                                    weight = 0.9f,
                                    cell = { s ->
                                        val rate = s.rate
                                        if (rate == null) {
                                            Text(stringResource(Res.string.value_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            val interval = wilsonInterval(s.zeroResult, s.total)
                                            Column {
                                                Text(formatPercent(rate), style = MaterialTheme.typography.bodyMedium)
                                                if (interval != null) {
                                                    Text(
                                                        formatWilsonRange(interval),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    sortSelector = { it.rate ?: -1.0 },
                                ),
                            ),
                            rows = searchScreens,
                            sizeClass = sizeClass,
                            initialSort = SortColumn(2, ascending = false),
                            rowKey = { it.screen },
                            modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                        )
                    }
                }
            }

            val frictionScreens = state.friction?.screens ?: emptyList()
            if (frictionScreens.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(Res.string.card_friction),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                        )
                        DataTable(
                            columns = listOf(
                                Column(
                                    title = stringResource(Res.string.col_screen),
                                    weight = 1f,
                                    cell = { f: FrictionScreenItem -> Text(f.screen, style = MaterialTheme.typography.bodyMedium) },
                                    sortSelector = { it.screen },
                                ),
                                Column(
                                    title = stringResource(Res.string.col_rage_taps),
                                    weight = 0.6f,
                                    cell = { f: FrictionScreenItem ->
                                        Text(
                                            formatCount(f.rageTaps),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    sortSelector = { it.rageTaps },
                                ),
                            ),
                            rows = frictionScreens,
                            sizeClass = sizeClass,
                            initialSort = SortColumn(1, ascending = false),
                            rowKey = { it.screen },
                            modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                        )
                    }
                }
            }

            val agg2 = state.aggregates
            ResponsiveRowOrColumn(sizeClass) {
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_countries),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        DonutChart(
                            (agg2?.countries ?: emptyList()).map { DonutData(it.displayName, it.count.toFloat()) },
                            sizeClass,
                        )
                    }
                }
                Card(modifier = Modifier.fillSlot()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.card_platforms),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        DonutChart(
                            (agg2?.platforms ?: emptyList()).map { DonutData(it.displayName, it.count.toFloat()) },
                            sizeClass,
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.card_device_classes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    DonutChart(
                        (agg2?.deviceClasses ?: emptyList()).map { DonutData(it.displayName, it.count.toFloat()) },
                        sizeClass,
                    )
                }
            }
        }
    }
}

/**
 * The "🧭 Flow" eyebrow label + H1 with a lime-highlighted span on "where they go next"
 * (design_handoff_dashboard_redesign/README.md > Flow; `QuietMetrix Prototype.dc.html`'s Flow H1
 * markup: `...see <span style="background:#c8f25a...">where they go next</span>.`).
 */
@Composable
internal fun FlowHero(modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val lead = stringResource(Res.string.flow_h1_lead)
    val highlight = stringResource(Res.string.flow_h1_highlight)
    val tail = stringResource(Res.string.flow_h1_tail)
    Column(modifier = modifier) {
        Text(
            "🧭 " + stringResource(Res.string.nav_flow),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        val sentence = buildAnnotatedString {
            append(lead)
            withStyle(SpanStyle(background = extended.lime, color = extended.onLime)) { append(highlight) }
            append(tail)
        }
        Text(sentence, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * Platform filter row (🌈 All / 🤖 Android / 🍎 iOS / 🌐 Web — design handoff README > Flow).
 * **Data-honesty decision:** only [FlowPlatform.All] is selectable/clickable. The other 3 pills
 * render (matching the design's screenshot) but carry no `onClick` and are dimmed, with a
 * content description explaining why — see [filterTransitionsByPlatform]'s KDoc (`FlowPathLogic.kt`)
 * for the backend-side finding (`Transition`/`TransitionItem` have no platform field) behind this.
 */
@Composable
internal fun PlatformFilterRow(modifier: Modifier = Modifier) {
    val notTracked = stringResource(Res.string.flow_platform_not_tracked_cd)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(Res.string.flow_platform_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Pill(text = stringResource(Res.string.flow_platform_all), variant = PillVariant.NavActive)
        Pill(
            text = stringResource(Res.string.flow_platform_android),
            variant = PillVariant.Neutral,
            modifier = Modifier.semantics { contentDescription = notTracked },
        )
        Pill(
            text = stringResource(Res.string.flow_platform_ios),
            variant = PillVariant.Neutral,
            modifier = Modifier.semantics { contentDescription = notTracked },
        )
        Pill(
            text = stringResource(Res.string.flow_platform_web),
            variant = PillVariant.Neutral,
            modifier = Modifier.semantics { contentDescription = notTracked },
        )
    }
}

/**
 * "Start from" pill row: the real top screens by outgoing transition volume ([topStartScreens]).
 * Clicking a screen (re)starts the breadcrumb path there. Renders nothing when there are no
 * screens to start from (no transitions loaded yet).
 */
@Composable
internal fun StartFromRow(
    screens: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (screens.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(Res.string.flow_start_from_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        screens.forEach { screen ->
            Pill(
                text = screen,
                variant = if (screen == selected) PillVariant.Lime else PillVariant.Neutral,
                onClick = { onPick(screen) },
            )
        }
    }
}

/**
 * The breadcrumb card: a "Path: …" chip row of the screens picked so far, then one
 * horizontally-scrollable column per step showing its real next-step options as a bar + percent
 * ([FlowBreadcrumbColumn] from [buildFlowBreadcrumb]). An empty [path] shows a plain prompt
 * instead of an empty card. See `FlowPathLogic.kt`'s top-of-file KDoc for what each column
 * honestly claims (a real per-screen outgoing-transition distribution, not a cross-hop "people"
 * simulation).
 */
@Composable
internal fun FlowBreadcrumbSection(
    path: List<String>,
    transitions: List<Transition>,
    onPick: (FlowStepOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (path.isEmpty()) {
                Text(
                    stringResource(Res.string.flow_state_empty_path),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 16.dp),
                ) {
                    Text(
                        stringResource(Res.string.flow_path_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val arrow = stringResource(Res.string.flow_path_arrow)
                    path.forEachIndexed { index, screen ->
                        Pill(text = screen, variant = PillVariant.Neutral)
                        if (index < path.lastIndex) {
                            Text(arrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                val columns = buildFlowBreadcrumb(path, transitions)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    columns.forEachIndexed { index, column ->
                        FlowBreadcrumbColumnView(
                            column = column,
                            selectedNext = path.getOrNull(index + 1),
                            onPick = onPick,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.flow_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One breadcrumb column: "After {screen} · N transitions seen" + its [FlowStepOption] rows. */
@Composable
private fun FlowBreadcrumbColumnView(
    column: FlowBreadcrumbColumn,
    selectedNext: String?,
    onPick: (FlowStepOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(220.dp)) {
        val totalSeen = column.options.sumOf { it.count }
        Text(
            stringResource(Res.string.flow_after_screen, column.screen, formatCount(totalSeen)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            column.options.forEach { option ->
                FlowStepOptionRow(
                    option = option,
                    selected = option.screen != null && option.screen == selectedNext,
                    onClick = { onPick(option) },
                )
            }
        }
    }
}

/**
 * One destination row: an inline bar sized to [FlowStepOption.percent], the screen name (or the
 * 👋 exit label), and the percent. Non-exit rows are clickable (extends the path); the exit row
 * never is (design handoff README > Flow: "👋 = 'left the app' and is not clickable").
 */
@Composable
private fun FlowStepOptionRow(
    option: FlowStepOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = LocalExtendedColors.current
    val label = if (option.isExit) stringResource(Res.string.flow_exit_label) else option.screen!!
    val barColor = if (option.isExit) extended.coral.copy(alpha = 0.22f) else extended.lime.copy(alpha = 0.28f)
    val fraction = (option.percent / 100.0).toFloat().coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(QmShapeTokens.chip)
            .background(extended.chipFill)
            .then(
                if (selected) Modifier.border(BorderStroke(2.dp, extended.lime), QmShapeTokens.chip) else Modifier,
            )
            .then(
                if (!option.isExit) Modifier.clickable(onClick = onClick) else Modifier,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(barColor),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                formatPercent(option.percent / 100.0),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
