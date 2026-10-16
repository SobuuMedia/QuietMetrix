package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AggregatesResponse
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
import com.quietmetrix.dashboard.resources.flow_copy_path
import com.quietmetrix.dashboard.resources.flow_copy_scope
import com.quietmetrix.dashboard.resources.flow_exit_label
import com.quietmetrix.dashboard.resources.flow_h1_highlight
import com.quietmetrix.dashboard.resources.flow_h1_lead
import com.quietmetrix.dashboard.resources.flow_h1_tail
import com.quietmetrix.dashboard.resources.flow_os_versions_caption
import com.quietmetrix.dashboard.resources.flow_path_copied
import com.quietmetrix.dashboard.resources.flow_path_label
import com.quietmetrix.dashboard.resources.flow_platform_all
import com.quietmetrix.dashboard.resources.flow_platform_android
import com.quietmetrix.dashboard.resources.flow_platform_ios
import com.quietmetrix.dashboard.resources.flow_platform_label
import com.quietmetrix.dashboard.resources.flow_platform_web
import com.quietmetrix.dashboard.resources.flow_start_from_label
import com.quietmetrix.dashboard.resources.flow_state_empty_path
import com.quietmetrix.dashboard.resources.ic_arrow_right
import com.quietmetrix.dashboard.resources.ic_copy
import com.quietmetrix.dashboard.resources.ic_flow
import com.quietmetrix.dashboard.resources.ic_overview
import com.quietmetrix.dashboard.resources.nav_flow
import com.quietmetrix.dashboard.resources.state_no_data
import com.quietmetrix.dashboard.resources.state_no_transitions
import com.quietmetrix.dashboard.resources.value_none
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.ui.components.ComparisonNote
import com.quietmetrix.dashboard.ui.components.DashboardCard
import com.quietmetrix.dashboard.ui.components.EmojiVector
import com.quietmetrix.dashboard.ui.components.EmptyState
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.MaxWidthContainer
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.ResponsiveRowOrColumn
import com.quietmetrix.dashboard.ui.components.SectionLoading
import com.quietmetrix.dashboard.ui.components.SkeletonBox
import com.quietmetrix.dashboard.ui.components.emojiResourceFor
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.ui.components.responsiveDisplayStyle
import com.quietmetrix.dashboard.ui.components.screenGlyphFor
import com.quietmetrix.dashboard.ui.components.table.Column
import com.quietmetrix.dashboard.ui.components.table.DataTable
import com.quietmetrix.dashboard.ui.components.table.SortColumn
import com.quietmetrix.dashboard.ui.components.table.screenLabel
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.isLoading
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

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
    MaxWidthContainer(sizeClass = sizeClass, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.sectionErrors[DataSection.Transitions]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }
            // Task 23: the 3-card row below now reads `state.aggregates` for real (Countries/
            // Platforms/Device types), so an aggregates-fetch failure needs the same surfaced
            // retry affordance Overview gives it, not just a silently-empty row.
            state.sectionErrors[DataSection.Aggregates]?.let {
                InlineErrorNotice(it, onRetry = { viewModel.navigateTo(state.activeDestination) })
            }

            FlowHero(modifier = Modifier.widthIn(max = 1050.dp))
            PlatformFilterRow(state.flowPlatform, viewModel::setFlowPlatform)

            val transitions = state.transitions?.transitions ?: emptyList()
            val startScreens = remember(transitions) { topStartScreens(transitions) }
            // Start at the highest-volume root. Each following node is explicit user intent.
            var flowPath by remember(transitions) { mutableStateOf(defaultFlowPath(transitions)) }
            LaunchedEffect(flowPath) { viewModel.setFlowNode(flowPath.lastOrNull()) }
            val projectName = state.projects.firstOrNull { it.id == state.currentProjectId }?.name.orEmpty()
            val rangeLabel = stringResource(state.range.labelRes)
            val platformResource = when (state.flowPlatform) {
                "android" -> Res.string.flow_platform_android
                "ios" -> Res.string.flow_platform_ios
                "web" -> Res.string.flow_platform_web
                else -> Res.string.flow_platform_all
            }
            val platformLabel = stringResource(platformResource)
            val copyScope = stringResource(Res.string.flow_copy_scope, projectName, rangeLabel, platformLabel)

            StartFromRow(
                screens = startScreens,
                selected = flowPath.firstOrNull(),
                path = flowPath,
                copyScope = copyScope,
                onPick = { flowPath = listOf(it) },
            )
            FlowBreadcrumbSection(
                path = flowPath,
                transitions = transitions,
                onPick = { option -> extendFlowPath(flowPath, option)?.let { flowPath = it } },
                onInspect = { screen -> viewModel.openDrillDown("flow", screen) },
                previousTransitions = state.comparison.transitions?.transitions.orEmpty(),
                compare = state.compare,
            )

            if (state.isLoading(DataSection.Transitions) && transitions.isEmpty()) {
                SectionLoading()
            } else if (transitions.isEmpty()) {
                DashboardCard(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(Res.drawable.ic_flow, Res.string.state_no_transitions, Modifier.padding(16.dp))
                }
            } else {
                DashboardCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val transitionNone = stringResource(Res.string.value_none)
                        DataTable(
                            columns = listOf(
                                Column(
                                    title = stringResource(Res.string.col_from),
                                    weight = 1f,
                                    cell = { t: Transition -> Text(screenLabel(t.fromScreen, transitionNone), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { viewModel.openDrillDown("flow", t.fromScreen.orEmpty()) }.handCursor()) },
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

            if (transitions.isEmpty() && !state.isLoading(DataSection.Transitions) && state.sectionErrors[DataSection.Transitions] == null) SetupInstructionsSection()
            state.sectionErrors[DataSection.Search]?.let { InlineErrorNotice(it, onRetry = { viewModel.refresh() }) }
            state.sectionErrors[DataSection.Friction]?.let { InlineErrorNotice(it, onRetry = { viewModel.refresh() }) }
            val searchScreens = state.search?.screens ?: emptyList()
            if (searchScreens.isNotEmpty()) {
                DashboardCard(modifier = Modifier.fillMaxWidth()) {
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
                DashboardCard(modifier = Modifier.fillMaxWidth()) {
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

            FlowBreakdownCardsSection(
                agg = state.aggregates,
                previous = state.comparison.aggregates,
                compare = state.compare,
                onInspect = viewModel::openDrillDown,
                loading = state.isLoading(DataSection.Aggregates),
                sizeClass = sizeClass,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Task 23: Flow 3-card row — Countries (bar list), Platforms (3 stat tiles + top OS versions),
// Device types (phone/tablet/desktop progress bars). See `FlowPathLogic.kt`'s Task 23 section KDoc
// for the full data-honesty investigation: platforms is real (`totalsByPlatform`); countries and
// device classes are real-shaped but always empty today (never tracked server-side); OS versions
// have no backing data anywhere in the pipeline and are honestly omitted rather than built.
//
// Replaces the plain `DonutChart` treatment this row used since Task 22's Flow extraction (that
// task's own KDoc deferred the literal bar-list/stat-tile/progress-bar spec to this task, matching
// how Overview's hero/vitals/chart/top-lists were split across Tasks 18-21).
// ---------------------------------------------------------------------------

/**
 * The Flow screen's 3-card row: real [AggregatesResponse.countries]/`.platforms`/`.deviceClasses`
 * rendered with 3 distinct treatments per the design handoff (Countries = bar list, Platforms = 3
 * stat tiles, Device types = progress bars) — see this file's section KDoc above. [loading] shows a
 * skeleton per card, matching Overview's `ScreensPlatformsCountriesSection` convention; each card
 * falls back to the shared [EmptyState] independently when its own list is empty (true for
 * Countries/Device types in every real deployment today).
 */
@Composable
internal fun FlowBreakdownCardsSection(
    agg: AggregatesResponse?,
    loading: Boolean,
    sizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    previous: AggregatesResponse? = null,
    compare: Boolean = false,
    onInspect: (String, String) -> Unit = { _, _ -> },
) {
    ResponsiveRowOrColumn(sizeClass, modifier = modifier) {
        if (loading) {
            FlowCardSkeleton(Modifier.fillSlot())
            FlowCardSkeleton(Modifier.fillSlot())
            FlowCardSkeleton(Modifier.fillSlot())
        } else {
            FlowCountriesCard(agg, Modifier.fillSlot(), previous, compare, onInspect)
            FlowPlatformsCard(agg, Modifier.fillSlot(), previous, compare, onInspect)
            FlowDeviceTypesCard(agg, Modifier.fillSlot(), previous, compare, onInspect)
        }
    }
}

/** Countries card: a ranked bar list, identical row idiom to Overview's "Where they are" card
 *  (same [computeBreakdownRows] data), reused here rather than re-invented — see this file's
 *  section KDoc for why this is empty in every real deployment today. */
@Composable
private fun FlowCountriesCard(agg: AggregatesResponse?, modifier: Modifier = Modifier, previous: AggregatesResponse? = null, compare: Boolean = false, onInspect: (String, String) -> Unit = { _, _ -> }) {
    DashboardCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.card_countries),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (compare) ComparisonNote(previous?.countries?.takeIf { it.isNotEmpty() }?.sumOf { it.count }?.let(::formatCount))
            Spacer(Modifier.height(8.dp))
            val rows = computeBreakdownRows(agg?.countries.orEmpty())
            if (rows.isEmpty()) {
                EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
            } else {
                rows.forEach { FlowBarListRow(it) { onInspect("country", it.name) } }
            }
        }
    }
}

/** One row of the Countries bar list: name + share-of-total percent on one line, a lime
 *  [FlowBar] sized to [BreakdownRow.barFraction] below it. */
@Composable
private fun FlowBarListRow(row: BreakdownRow, onClick: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatPercent(row.shareOfTotal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        FlowBar(fraction = row.barFraction.toFloat(), color = LocalExtendedColors.current.lime)
    }
}

/** Platforms card: up to 3 real stat tiles ([computePlatformStatTiles]) plus an honest caption
 *  disclosing that "top OS versions" (design handoff README > Flow) has no real data anywhere in
 *  the counter pipeline — see `FlowPathLogic.kt`'s Task 23 section KDoc. This is the one card of
 *  the 3 with real content in every real deployment today (platform is a tracked dimension). */
@Composable
private fun FlowPlatformsCard(agg: AggregatesResponse?, modifier: Modifier = Modifier, previous: AggregatesResponse? = null, compare: Boolean = false, onInspect: (String, String) -> Unit = { _, _ -> }) {
    DashboardCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.card_platforms),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (compare) ComparisonNote(previous?.platforms?.takeIf { it.isNotEmpty() }?.sumOf { it.count }?.let(::formatCount))
            Spacer(Modifier.height(8.dp))
            val tiles = computePlatformStatTiles(agg?.platforms.orEmpty())
            if (tiles.isEmpty()) {
                EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    tiles.forEach { tile -> FlowPlatformStatTile(tile, Modifier.weight(1f)) { onInspect("platform", tile.name) } }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.flow_os_versions_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One of the Platforms card's 3 stat tiles: a flat tonal [QmShapeTokens.chip]-shaped surface with
 *  the platform name, its real count, and its share-of-total percent — a visually distinct unit
 *  from the Countries/Device types bar rows, per the design handoff's 3 different treatments. */
@Composable
private fun FlowPlatformStatTile(row: BreakdownRow, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val extended = LocalExtendedColors.current
    Column(
        modifier = modifier.background(extended.chipFill, QmShapeTokens.chip).clickable(onClick = onClick).handCursor().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            row.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(formatCount(row.count), style = MaterialTheme.typography.titleMedium)
        Text(
            formatPercent(row.shareOfTotal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Device types card: progress-bar rows ([computeBreakdownRows] over
 *  [AggregatesResponse.deviceClasses]), each prefixed with [deviceClassEmoji] and laid out
 *  inline (icon + name + bar + percent on one line) — deliberately a different row shape from
 *  the Countries card's two-line bar-list rows, and a lavender fill instead of lime, so the 3
 *  cards read as 3 distinct treatments rather than 3 copies of the same component. Empty in every
 *  real deployment today — see this file's section KDoc. */
@Composable
private fun FlowDeviceTypesCard(agg: AggregatesResponse?, modifier: Modifier = Modifier, previous: AggregatesResponse? = null, compare: Boolean = false, onInspect: (String, String) -> Unit = { _, _ -> }) {
    DashboardCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.card_device_classes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (compare) ComparisonNote(previous?.deviceClasses?.takeIf { it.isNotEmpty() }?.sumOf { it.count }?.let(::formatCount))
            Spacer(Modifier.height(8.dp))
            val rows = computeBreakdownRows(agg?.deviceClasses.orEmpty())
            if (rows.isEmpty()) {
                EmptyState(Res.drawable.ic_overview, Res.string.state_no_data)
            } else {
                rows.forEach { FlowDeviceTypeRow(it) { onInspect("device", it.name) } }
            }
        }
    }
}

/** One Device types progress-bar row: emoji + name, the bar itself taking the remaining width,
 *  then the percent — see [FlowDeviceTypesCard]'s KDoc for why this is a single-line layout
 *  distinct from [FlowBarListRow]'s stacked one. */
@Composable
private fun FlowDeviceTypeRow(row: BreakdownRow, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmojiVector(deviceClassEmoji(row.name), Modifier.size(20.dp))
        Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
        FlowBar(
            fraction = row.barFraction.toFloat(),
            color = LocalExtendedColors.current.lavender,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatPercent(row.shareOfTotal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Thin horizontal bar: a subtle [ExtendedColors.chipFill] track + a solid [color] fill sized to
 *  [fraction] (0f-1f) — the same "track + solid fill" idiom `OverviewScreen.kt`'s own `UsageBar`
 *  uses (file-private there, so re-declared here rather than exposed across files), parameterized
 *  on [color] so [FlowBarListRow] (lime) and [FlowDeviceTypeRow] (lavender) read as visually
 *  distinct bars. */
@Composable
private fun FlowBar(fraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    Box(modifier = modifier.fillMaxWidth().height(4.dp).background(extended.chipFill, RoundedCornerShape(2.dp))) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
    }
}

/** Flat skeleton placeholder for one 3-card-row card while [AggregatesResponse] is loading — same
 *  flat tonal-fill idiom as `OverviewScreen.kt`'s own `TopListSkeletonCard` (file-private there). */
@Composable
private fun FlowCardSkeleton(modifier: Modifier = Modifier) {
    DashboardCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.width(100.dp).height(16.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(14.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(14.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(14.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EmojiVector("🧭", Modifier.size(22.dp))
            Text(stringResource(Res.string.nav_flow), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        val sentence = buildAnnotatedString {
            append(lead)
            withStyle(SpanStyle(background = extended.lime, color = extended.onLime)) { append(highlight) }
            append(tail)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Text(sentence, style = responsiveDisplayStyle(maxWidth), color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/**
 * Platform filter row (design handoff README > Flow: "🌈 All / 🤖 Android / 🍎 iOS / 🌐 Web").
 *
 * **Data-honesty decision, revised after review:** an earlier version of this composable rendered
 * all 4 pills with the 3 non-"All" ones carrying no `onClick` — but a disabled `Pill` renders
 * pixel-identical to a live, clickable one elsewhere on this same screen (`StartFromRow`'s
 * clickable, non-selected pills use that exact same [PillVariant.Neutral] styling), so a sighted
 * user tapping "🤖 Android" would get silent nothing with zero visual signal that it was never
 * going to do anything — exactly the confusion the honest-disable approach was meant to avoid.
 * The only disclosure was an accessibility-only `contentDescription`, which a sighted user never
 * sees.
 *
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PlatformFilterRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.flow_platform_label)
    val platforms = listOf(
        Triple("all", stringResource(Res.string.flow_platform_all), "🌈"),
        Triple("android", stringResource(Res.string.flow_platform_android), "🤖"),
        Triple("ios", stringResource(Res.string.flow_platform_ios), "🍎"),
        Triple("web", stringResource(Res.string.flow_platform_web), "🌐"),
    )
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    platforms.forEach { (key, platformLabel, glyph) ->
                        Pill(text = platformLabel, variant = if (selected == key) PillVariant.NavActive else PillVariant.Neutral,
                            onClick = { onSelect(key) }, leadingIcon = emojiResourceFor(glyph), colorfulIcon = true)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                platforms.forEach { (key, platformLabel, glyph) ->
                    Pill(text = platformLabel, variant = if (selected == key) PillVariant.NavActive else PillVariant.Neutral,
                        onClick = { onSelect(key) }, leadingIcon = emojiResourceFor(glyph), colorfulIcon = true)
                }
            }
        }
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
    path: List<String> = emptyList(),
    copyScope: String = "",
    modifier: Modifier = Modifier,
) {
    if (screens.isEmpty()) return
    val clipboard = LocalClipboardManager.current
    var copiedPath by remember(path) { mutableStateOf(false) }
    val pathLabel = stringResource(Res.string.flow_path_label)
    val copyPath = {
        val pathText = path.joinToString(" → ") { screenLabel(it, it) }
        clipboard.setText(AnnotatedString("$pathLabel $pathText\n$copyScope"))
        copiedPath = true
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 760.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StartFromPills(screens, selected, onPick,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))
                if (path.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CopyFlowPathButton(copiedPath, copyPath)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StartFromPills(screens, selected, onPick,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()))
                if (path.isNotEmpty()) CopyFlowPathButton(copiedPath, copyPath)
            }
        }
    }
}

@Composable
private fun StartFromPills(screens: List<String>, selected: String?, onPick: (String) -> Unit, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(stringResource(Res.string.flow_start_from_label), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        screens.forEach { screen ->
            Pill(text = screenLabel(screen, screen), variant = if (screen == selected) PillVariant.Lime else PillVariant.Neutral,
                onClick = { onPick(screen) }, leadingIcon = emojiResourceFor(screenGlyphFor(screen)), colorfulIcon = true)
        }
    }
}

@Composable
private fun CopyFlowPathButton(copied: Boolean, onCopy: () -> Unit) {
    TextButton(onClick = onCopy, modifier = Modifier.handCursor()) {
        Icon(painterResource(Res.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(16.dp))
        MaterialText(stringResource(if (copied) Res.string.flow_path_copied else Res.string.flow_copy_path))
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
    onInspect: (String) -> Unit = {},
    previousTransitions: List<Transition> = emptyList(),
    compare: Boolean = false,
) {
    DashboardCard(modifier = modifier.fillMaxWidth()) {
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(Res.string.flow_path_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        path.forEachIndexed { index, screen ->
                            Pill(text = screenLabel(screen, screen), variant = PillVariant.Neutral, modifier = Modifier.clickable { onInspect(screen) }.handCursor(),
                                leadingIcon = emojiResourceFor(screenGlyphFor(screen)), colorfulIcon = true)
                            if (index < path.lastIndex) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_arrow_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
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
                            previousTransitions = previousTransitions,
                            compare = compare,
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
    previousTransitions: List<Transition> = emptyList(),
    compare: Boolean = false,
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
                if (compare) ComparisonNote(previousTransitions.firstOrNull {
                    it.fromScreen == column.screen && it.toScreen == option.screen
                }?.count?.let(::formatCount))
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
    val label = if (option.isExit) stringResource(Res.string.flow_exit_label) else screenLabel(option.screen, option.screen!!)
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
                if (!option.isExit) Modifier.clickable(onClick = onClick).handCursor() else Modifier,
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
            EmojiVector(if (option.isExit) "👋" else screenGlyphFor(option.screen!!), Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                formatPercent(option.percent / 100.0),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
