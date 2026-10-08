package com.quietmetrix.dashboard.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.ErrorKind
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.UiError
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.nav.classifyWindow
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_got_it
import com.quietmetrix.dashboard.resources.action_refresh
import com.quietmetrix.dashboard.resources.action_signout
import com.quietmetrix.dashboard.resources.action_try_again
import com.quietmetrix.dashboard.resources.ai_allowance_reached
import com.quietmetrix.dashboard.resources.analytics_period_label
import com.quietmetrix.dashboard.resources.analytics_resolution_help
import com.quietmetrix.dashboard.resources.analytics_resolution_title
import com.quietmetrix.dashboard.resources.brand
import com.quietmetrix.dashboard.resources.comparison_scope
import com.quietmetrix.dashboard.resources.drill_question
import com.quietmetrix.dashboard.resources.drill_scope
import com.quietmetrix.dashboard.resources.header_ask_button
import com.quietmetrix.dashboard.resources.header_avatar_cd
import com.quietmetrix.dashboard.resources.header_progress_bar_cd
import com.quietmetrix.dashboard.resources.header_server_unreachable_message
import com.quietmetrix.dashboard.resources.header_theme_toggle_cd
import com.quietmetrix.dashboard.resources.header_toggle_compare
import com.quietmetrix.dashboard.resources.header_toggle_expert
import com.quietmetrix.dashboard.resources.ic_brand
import com.quietmetrix.dashboard.resources.ic_calendar
import com.quietmetrix.dashboard.resources.ic_chevron_down
import com.quietmetrix.dashboard.resources.ic_compare
import com.quietmetrix.dashboard.resources.ic_dark_mode
import com.quietmetrix.dashboard.resources.ic_info
import com.quietmetrix.dashboard.resources.ic_light_mode
import com.quietmetrix.dashboard.resources.ic_refresh
import com.quietmetrix.dashboard.resources.ic_signout
import com.quietmetrix.dashboard.resources.ic_twemoji_1f4e6
import com.quietmetrix.dashboard.resources.ic_twemoji_1f913
import com.quietmetrix.dashboard.resources.ic_twemoji_2728
import com.quietmetrix.dashboard.resources.nav_badge_new
import com.quietmetrix.dashboard.resources.picker_no_projects
import com.quietmetrix.dashboard.resources.picker_select_project
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.LocalThemeMode
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.theme.resolveSystemThemeMode
import com.quietmetrix.dashboard.ui.components.AskDrawer
import com.quietmetrix.dashboard.ui.components.CopyErrorButton
import com.quietmetrix.dashboard.ui.components.DrillDownDrawer
import com.quietmetrix.dashboard.ui.components.ErrorBanner
import com.quietmetrix.dashboard.ui.components.InlineErrorNotice
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.analyticsSections
import com.quietmetrix.dashboard.viewmodel.selectDrillDown
import com.quietmetrix.dashboard.viewmodel.serverUnreachableError
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Text as MaterialText
import com.quietmetrix.dashboard.ui.components.SelectableText as Text

/** Height of [HeaderProgressBar]'s sliding fill — design_handoff_dashboard_redesign/README.md > Header: "A 3px progress bar". */
private val PROGRESS_BAR_HEIGHT = 3.dp

/**
 * Top-level shell every dashboard screen renders inside: a [Scaffold] whose
 * top bar is the dashboard-redesign header (logo/wordmark, project + date-range
 * dropdowns, toggle row, nav pill row) and whose body is [content].
 *
 * The header's `NavPillRow` (Task 16) is the sole navigation surface at every
 * window width — see this task's cleanup report
 * (`.superpowers/sdd/proud-dazzling-pancake/cleanup-task16-duplicate-nav-report.md`)
 * for why the pre-redesign `SideRail`/`SideDrawerHost` split (`nav/SideMenu.kt`)
 * was removed from here: neither the README nor the HTML prototype describe a
 * sidebar or drawer at any viewport, only the header pill row, which already
 * wraps onto additional lines at narrow widths via `FlowRow` (matching the
 * prototype's `<nav style="...flex-wrap:wrap">`). [BoxWithConstraints] and
 * [classifyWindow] are kept here (and the [WindowSizeClass] still threaded
 * into [content]) because several screens use them for non-nav layout
 * decisions — e.g. [ResponsiveRowOrColumn]'s stacked-vs-row flow, [DataTable]
 * hiding columns, [DonutChart] sizing, and `ProjectsTab`'s grid column count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardShell(
    viewModel: DashboardViewModel,
    state: DashboardState,
    snackbarState: SnackbarHostState,
    content: @Composable (WindowSizeClass) -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(com.quietmetrix.dashboard.ui.components.LocalAiAccess provides state.ai) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val sizeClass = classifyWindow(maxWidth)

        Scaffold(
            topBar = { DashboardTopBar(viewModel, state) },
            snackbarHost = { SnackbarHost(hostState = snackbarState) },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                HeaderProgressBar(visible = state.loading || state.refreshing || state.loadingSections.isNotEmpty())
                // Mutually exclusive, not stacked — see this block's KDoc note below for why.
                val serverUnreachable = state.serverUnreachableError
                if (serverUnreachable != null) {
                    ServerUnreachableBanner(serverUnreachable, onRetry = { viewModel.refresh() })
                } else {
                    state.error?.let { ErrorBanner(it, onDismiss = { viewModel.clearError() }) }
                }
                if (state.ai.reason == "ai_allowance_exhausted") {
                    Text(stringResource(Res.string.ai_allowance_reached, state.ai.reset_date), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                if (state.compare && state.activeDestination in analyticsDestinations) {
                    val dates = analyticsPeriodDates(state.range, state.analyticsAnchor)
                    Text(stringResource(Res.string.analytics_period_label, dates[0], dates[1], dates[2], dates[3]),
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(Res.string.comparison_scope), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall, color = LocalExtendedColors.current.lavender)
                    state.comparisonErrors.filterKeys { it in analyticsSections(state.activeDestination) }.values.forEach { error ->
                        InlineErrorNotice(error, onRetry = { viewModel.refresh() })
                    }
                }
                Box(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp).alpha(if (state.refreshing) .45f else 1f)) {
                    content(sizeClass)
                }
            }
        }
        AskDrawer(viewModel, state)
        state.drillDown?.let { selection ->
            val selected = selectDrillDown(state, selection.kind, selection.key)
            val title = selected.titleRes?.let { stringResource(it) } ?: selected.title ?: selected.key
            val project = state.projects.firstOrNull { it.id == state.currentProjectId }?.name.orEmpty()
            val scope = stringResource(Res.string.drill_scope, project, stringResource(state.range.labelRes))
            val question = stringResource(Res.string.drill_question, title)
            DrillDownDrawer(selected, scope, state.compare, state.expertMode, onDismiss = { viewModel.closeDrillDown() }) {
                viewModel.closeDrillDown()
                viewModel.openAskDrawer(question)
            }
        }
    }
    }
}

/**
 * 3px sliding lime loading bar shown directly under the header while [visible]
 * (design_handoff_dashboard_redesign/README.md > Header: "A 3px progress bar appears under the
 * header during Loading state or while refreshing (animated sliding lime fill)."). Renders
 * nothing at all when [visible] is false, so it never reserves layout space on a quiet screen.
 *
 * The animation itself ([rememberInfiniteTransition]) is pulled out from
 * [HeaderProgressBarTrack] — the actual fill-fraction drawing — so screenshot tests can render a
 * single deterministic frame of the sweep instead of fighting a live animation clock.
 */
@Composable
internal fun HeaderProgressBar(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val transition = rememberInfiniteTransition(label = "header-progress-bar")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "header-progress-bar-fraction",
    )
    HeaderProgressBarTrack(fraction = fraction, modifier = modifier)
}

/**
 * Pure, non-animated rendering of [HeaderProgressBar]'s track + lime fill at a given [fraction]
 * (0f-1f) of the full width — factored out so a screenshot test can pin one exact frame. Visually
 * cross-checked against `_source/02-overview-loading.png`'s visible progress-bar row (see
 * `goldens/README.md`'s log entry for this golden): same 3px height and left-aligned sliding fill
 * against a subtle track, at a fraction (~0.34) matching that captured frame. The fill is the
 * solid [com.quietmetrix.dashboard.theme.ExtendedColors.lime] token rather than the exact
 * (JPEG-compressed, anti-aliased) pixel color sampled from the source — the same token-over-pixel
 * judgment call this file's other screenshot tests document for e.g. the Experiments "NEW" badge.
 */
@Composable
internal fun HeaderProgressBarTrack(fraction: Float, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val progressBarCd = stringResource(Res.string.header_progress_bar_cd)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PROGRESS_BAR_HEIGHT)
            .background(extended.chipFill)
            .semantics { contentDescription = progressBarCd },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(extended.lime),
        )
    }
}

/**
 * Coral "can't reach your QuietMetrix server" banner rendered directly under the header on every
 * screen while [DashboardState.serverUnreachableError] is non-null
 * (design_handoff_dashboard_redesign/README.md > Header: "An error banner (coral background, 🔌
 * icon, 'We can't reach your QuietMetrix server', 'Try again' + 'Copy error details' buttons)
 * renders under the header in Errors state, on every screen."). Unlike [ErrorBanner] (dismissible,
 * per-[UiError.kind] friendly sentence, for a user-initiated action), this banner's message is
 * fixed — connectivity, not a specific action, is what it reports — and it has no dismiss button:
 * [onRetry] (wired to [DashboardViewModel.refresh] by the caller) is the only way past it, since
 * the underlying data really is unreachable until the next successful fetch.
 *
 * **Mutually exclusive with [ErrorBanner], never stacked.** Both [DashboardState.sectionErrors]
 * (which [DashboardState.serverUnreachableError] reads) and [DashboardState.error] can
 * independently end up holding an [ErrorKind.Network] failure at the same time — e.g. a
 * background loader on the active screen fails to reach the server (recorded in
 * `sectionErrors`, surfacing this banner) while the user separately retries a user-initiated
 * action (e.g. "New project") that *also* fails with a connection error (recorded in `error`,
 * which [ErrorBanner] would otherwise render). Showing both at once would stack two banners
 * under the header describing the same underlying outage with inconsistent, confusing
 * affordances (one dismissible with no retry, one non-dismissible with retry). [DashboardShell]
 * resolves this by giving this banner priority: whenever [DashboardState.serverUnreachableError]
 * is non-null, [ErrorBanner] is not rendered at all, regardless of [DashboardState.error] or its
 * kind — the connectivity-level banner is the more actionable, more encompassing signal (a
 * dismissed per-action error would wrongly imply the problem is resolved when the server is
 * still unreachable), and it clears on its own once the next loader succeeds.
 */
@Composable
internal fun ServerUnreachableBanner(error: UiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val message = stringResource(Res.string.header_server_unreachable_message)
    Surface(color = extended.coral, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                color = extended.onLime,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, modifier = Modifier.handCursor()) {
                MaterialText(stringResource(Res.string.action_try_again), color = extended.onLime, fontWeight = FontWeight.Bold)
            }
            CopyErrorButton(error, tint = extended.onLime)
        }
    }
}

/**
 * The persistent dashboard header uses the handoff's two-row desktop composition:
 * brand/project/date and trailing controls share the first row, with navigation below.
 * At narrower widths, controls wrap into a separate row before navigation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DashboardTopBar(
    viewModel: DashboardViewModel,
    state: DashboardState,
) {
    val savedThemeMode = LocalThemeMode.current
    val themeMode = if (savedThemeMode == ThemeMode.System) resolveSystemThemeMode() else savedThemeMode
    val setThemeMode = LocalThemeModeSetter.current
    val compareOn = state.compare

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.widthIn(max = 1360.dp).fillMaxWidth()) {
              val containerWidth = maxWidth
              val gutter = when {
                  maxWidth < 600.dp -> 16.dp
                  maxWidth < 1024.dp -> 24.dp
                  else -> 32.dp
              }
              Column(
                modifier = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 16.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (containerWidth >= 1200.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HeaderLogoAndWordmark()
                        ProjectDropdown(
                            projects = state.projects,
                            currentProjectId = state.currentProjectId,
                            onSelect = { viewModel.selectProject(it) },
                            modifier = Modifier.widthIn(max = 190.dp),
                        )
                        DateRangeDropdown(range = state.range, onSelect = { viewModel.selectRange(it) })
                        Spacer(Modifier.weight(1f))
                        HeaderUtilityControls(
                            compareVisible = state.activeDestination in analyticsDestinations,
                            compareOn = compareOn,
                            onCompareToggle = { viewModel.toggleCompare() },
                            expertOn = state.expertMode,
                            onExpertToggle = { viewModel.toggleExpertMode() },
                            refreshing = state.refreshing,
                            onRefresh = { viewModel.refresh() },
                            themeMode = themeMode,
                            onToggleTheme = { setThemeMode(if (themeMode == ThemeMode.Light) ThemeMode.Dark else ThemeMode.Light) },
                            onAsk = { viewModel.openAskDrawer() },
                        )
                        HeaderAvatar(state.user?.email) { viewModel.logout() }
                    }
                } else {
                    if (containerWidth >= 1024.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HeaderLogoAndWordmark()
                            ProjectDropdown(
                                projects = state.projects,
                                currentProjectId = state.currentProjectId,
                                onSelect = { viewModel.selectProject(it) },
                                modifier = Modifier.widthIn(max = 190.dp),
                            )
                            DateRangeDropdown(range = state.range, onSelect = { viewModel.selectRange(it) })
                            Spacer(Modifier.weight(1f))
                            HeaderAvatar(state.user?.email) { viewModel.logout() }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HeaderLogoAndWordmark()
                            Spacer(Modifier.weight(1f))
                            HeaderAvatar(state.user?.email) { viewModel.logout() }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProjectDropdown(
                                projects = state.projects,
                                currentProjectId = state.currentProjectId,
                                onSelect = { viewModel.selectProject(it) },
                                modifier = Modifier.weight(1f),
                            )
                            DateRangeDropdown(
                                range = state.range,
                                onSelect = { viewModel.selectRange(it) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HeaderUtilityControls(
                            compareVisible = state.activeDestination in analyticsDestinations,
                            compareOn = compareOn,
                            onCompareToggle = { viewModel.toggleCompare() },
                            expertOn = state.expertMode,
                            onExpertToggle = { viewModel.toggleExpertMode() },
                            refreshing = state.refreshing,
                            onRefresh = { viewModel.refresh() },
                            themeMode = themeMode,
                            onToggleTheme = { setThemeMode(if (themeMode == ThemeMode.Light) ThemeMode.Dark else ThemeMode.Light) },
                            onAsk = { viewModel.openAskDrawer() },
                        )
                    }
                }

                NavPillRow(
                    destinations = NavDestination.visibleFor(state.user?.role),
                    current = state.activeDestination,
                    onSelect = { viewModel.navigateTo(it) },
                )
              }
            }
        }
    }
}

/** Existing browser favicon rendered beside the wordmark. */
@Composable
internal fun HeaderLogoAndWordmark(modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(Res.drawable.ic_brand), contentDescription = null, tint = Color.Unspecified,
            modifier = Modifier.size(38.dp))
        Text(
            stringResource(Res.string.brand),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Project-switcher dropdown trigger (design_handoff_dashboard_redesign/README.md
 * > Header: "Project switcher dropdown (shows emoji + name, e.g. '📚 Sobuu ▾');
 * menu lists all projects with a check on the active one"). Functionally
 * identical to the pre-existing `ProjectPicker` in `DashboardScreen.kt`
 * (same [ApiProject] list, same [onSelect] callback into
 * `DashboardViewModel.selectProject`) — this is a new, header-styled
 * trigger (pill-shaped chip instead of an `OutlinedButton`) rather than a
 * re-skin of that shared widget, so the per-tab pickers elsewhere keep
 * their existing appearance until their own screen tasks restyle them.
 */
@Composable
internal fun ProjectDropdown(
    projects: List<ApiProject>,
    currentProjectId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = projects.firstOrNull { it.id == currentProjectId }
    val noProjects = stringResource(Res.string.picker_no_projects)
    val selectProjectLabel = stringResource(Res.string.picker_select_project)
    val triggerText = current?.name ?: if (projects.isEmpty()) noProjects else selectProjectLabel

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            enabled = projects.isNotEmpty(),
            color = LocalExtendedColors.current.chipFill,
            shape = QmShapeTokens.pill,
            modifier = Modifier.handCursor(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Icon(painterResource(Res.drawable.ic_twemoji_1f4e6), contentDescription = null,
                    tint = Color.Unspecified, modifier = Modifier.size(18.dp))
                Text(triggerText, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(painterResource(Res.drawable.ic_chevron_down), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { MaterialText(p.name) },
                    onClick = { onSelect(p.id); expanded = false },
                    modifier = Modifier.handCursor(),
                )
            }
        }
    }
}

/**
 * Date-range dropdown trigger (design_handoff_dashboard_redesign/README.md
 * > Header: "Date-range dropdown ('🗓️ Last 7 days ▾'): Today / Last 7 days /
 * Last 30 days / Last 90 days"). Functionally identical to the pre-existing
 * `WindowPicker` in `DashboardScreen.kt` (same [TimeRange] entries, same
 * [onSelect] callback into `DashboardViewModel.selectRange`) — see
 * [ProjectDropdown]'s KDoc for why this is a new, header-styled trigger
 * rather than a re-skin of that shared widget.
 */
@Composable
internal fun DateRangeDropdown(
    range: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var resolutionHelp by remember { mutableStateOf(false) }
    val triggerText = stringResource(range.labelRes)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
      Box {
        Surface(
            onClick = { expanded = true },
            color = LocalExtendedColors.current.chipFill,
            shape = QmShapeTokens.pill,
            modifier = Modifier.handCursor(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Icon(painterResource(Res.drawable.ic_calendar), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(triggerText, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Icon(painterResource(Res.drawable.ic_chevron_down), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeRange.entries.filter { it != TimeRange.Hour1 }.forEach { r ->
                DropdownMenuItem(
                    text = { MaterialText(stringResource(r.labelRes)) },
                    onClick = { onSelect(r); expanded = false },
                    modifier = Modifier.handCursor(),
                )
            }
        }
      }
      IconButton(onClick = { resolutionHelp = true }, modifier = Modifier.size(32.dp).handCursor()) {
          Icon(painterResource(Res.drawable.ic_info), contentDescription = stringResource(Res.string.analytics_resolution_title),
              tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
      }
    }
    if (resolutionHelp) AlertDialog(
        onDismissRequest = { resolutionHelp = false },
        title = { Text(stringResource(Res.string.analytics_resolution_title)) },
        text = { Text(stringResource(Res.string.analytics_resolution_help)) },
        confirmButton = { TextButton(onClick = { resolutionHelp = false }, modifier = Modifier.handCursor()) { MaterialText(stringResource(Res.string.action_got_it)) } },
    )
}

/**
 * The header's second row (design_handoff_dashboard_redesign/README.md >
 * Header: "⇄ Compare toggle ... 🤓 Expert toggle ... 🔄 Refresh button ...
 * ☀️/🌙 theme toggle ... ✨ Ask button (lavender, opens right-hand drawer)
 * ... user avatar circle"). [onRefresh] and the theme toggle retain their existing
 * behavior; [onExpertToggle] updates the shared dashboard preference. Compare updates the shared
 * range query, and Ask opens its drawer even when the server has no AI entitlement so the drawer
 * can explain that state.
 * [onSignOut] is the pre-existing `DashboardViewModel.logout`, now reached
 * through the avatar's dropdown menu rather than a standalone button.
 */
@Composable
internal fun HeaderToggleRow(
    compareOn: Boolean,
    compareVisible: Boolean = true,
    onCompareToggle: () -> Unit,
    expertOn: Boolean,
    onExpertToggle: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onAsk: () -> Unit,
    userEmail: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = LocalExtendedColors.current
    var avatarMenuExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 560.dp
        if (compact) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderUtilityControls(compareVisible, compareOn, onCompareToggle, expertOn, onExpertToggle,
                        refreshing, onRefresh, themeMode, onToggleTheme, onAsk)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    HeaderAvatar(userEmail, onSignOut)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderUtilityControls(compareVisible, compareOn, onCompareToggle, expertOn, onExpertToggle,
                    refreshing, onRefresh, themeMode, onToggleTheme, onAsk)
                Spacer(Modifier.weight(1f))
                HeaderAvatar(userEmail, onSignOut)
            }
        }
    }
}

@Composable
private fun HeaderUtilityControls(
    compareVisible: Boolean, compareOn: Boolean, onCompareToggle: () -> Unit,
    expertOn: Boolean, onExpertToggle: () -> Unit, refreshing: Boolean, onRefresh: () -> Unit,
    themeMode: ThemeMode, onToggleTheme: () -> Unit, onAsk: () -> Unit,
) {
    BoxWithConstraints {
        val compact = maxWidth < 560.dp
        val controls: @Composable () -> Unit = {
            if (compareVisible) ToggleChip(icon = Res.drawable.ic_compare, label = stringResource(Res.string.header_toggle_compare), on = compareOn, onClick = onCompareToggle)
            ToggleChip(icon = Res.drawable.ic_twemoji_1f913, label = stringResource(Res.string.header_toggle_expert), on = expertOn, onClick = onExpertToggle, colorful = true)
            IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.handCursor()) {
                Icon(painter = painterResource(Res.drawable.ic_refresh), contentDescription = stringResource(Res.string.action_refresh), modifier = Modifier.size(20.dp))
            }
            val themeToggleCd = stringResource(Res.string.header_theme_toggle_cd)
            IconButton(onClick = onToggleTheme, modifier = Modifier.handCursor().semantics { contentDescription = themeToggleCd }) {
                Icon(painterResource(if (themeMode == ThemeMode.Light) Res.drawable.ic_dark_mode else Res.drawable.ic_light_mode),
                    contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Pill(text = stringResource(Res.string.header_ask_button), variant = PillVariant.Lavender,
                onClick = onAsk, leadingIcon = Res.drawable.ic_twemoji_2728, colorfulIcon = true)
        }
        if (compact) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { controls() }
        }
    }
}

@Composable
private fun HeaderAvatar(userEmail: String?, onSignOut: () -> Unit) {
    val extended = LocalExtendedColors.current
    var avatarMenuExpanded by remember { mutableStateOf(false) }
    val avatarCd = stringResource(Res.string.header_avatar_cd)
    Box {
        Surface(onClick = { avatarMenuExpanded = true }, color = extended.coral, shape = CircleShape,
            modifier = Modifier.size(40.dp).handCursor().semantics { contentDescription = avatarCd }) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = (userEmail?.firstOrNull()?.uppercaseChar() ?: '?').toString(), color = extended.onLime,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(expanded = avatarMenuExpanded, onDismissRequest = { avatarMenuExpanded = false }) {
            userEmail?.let { DropdownMenuItem(text = { MaterialText(it) }, onClick = {}, enabled = false) }
            DropdownMenuItem(text = { MaterialText(stringResource(Res.string.action_signout)) },
                leadingIcon = { Icon(painter = painterResource(Res.drawable.ic_signout), contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { onSignOut(); avatarMenuExpanded = false }, modifier = Modifier.handCursor())
        }
    }
}

/**
 * One Compare/Expert toggle pill: an emoji + label whose fill switches
 * between [PillVariant.Lime] (on) and [PillVariant.Neutral] (off) so the
 * active state reads clearly without a separate indicator glyph.
 */
@Composable
private fun ToggleChip(icon: DrawableResource, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, colorful: Boolean = false) {
    Pill(text = label, variant = if (on) PillVariant.Lime else PillVariant.Neutral,
        onClick = onClick, modifier = modifier, leadingIcon = icon, colorfulIcon = colorful)
}

/**
 * The header's third row — primary nav pills
 * (design_handoff_dashboard_redesign/README.md > Header: "primary nav pills
 * — Overview, Flow, Retention, Funnels, Experiments (has a lime 'NEW'
 * badge), Projects, Team, Settings. Active pill is filled solid (`--ink` bg
 * / `--bg` text)"). The active destination renders as a filled
 * [PillVariant.NavActive] [Pill] (`goldens/pill/variants.png`'s existing
 * reference); every inactive destination renders as plain emoji+label text
 * with no fill at all — `_source/01-overview-normal.png`'s visible
 * Flow/Retention/Funnels/Team/Settings pills show no background surface
 * behind their text, only the active Overview pill does (see
 * `goldens/README.md`'s log entry for this golden).
 */
@Composable
internal fun NavPillRow(
    destinations: List<NavDestination>,
    current: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = when {
            maxWidth < 600.dp -> 2
            maxWidth < 1024.dp -> 5
            else -> destinations.size.coerceAtLeast(1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            destinations.chunked(perRow).forEach { rowDestinations ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowDestinations.forEach { dest ->
                        NavPillItem(destination = dest, selected = dest == current, onClick = { onSelect(dest) })
                    }
                }
            }
        }
    }
}

/**
 * A single nav-row entry. Its vector icon is rendered by Compose Resources,
 * avoiding platform-dependent emoji glyphs in the shared dashboard.
 */
@Composable
private fun NavPillItem(destination: NavDestination, selected: Boolean, onClick: () -> Unit) {
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) bringIntoViewRequester.bringIntoView()
    }
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = foreground,
        shape = QmShapeTokens.pill,
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester).handCursor(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                painter = painterResource(destination.icon),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(16.dp),
            )
            Text(stringResource(destination.labelRes), style = MaterialTheme.typography.labelLarge)
            if (destination == NavDestination.Experiments) NewBadge()
        }
    }
}

/**
 * Small lime "NEW" corner badge for the Experiments nav pill
 * (design_handoff_dashboard_redesign/README.md > Header: "Experiments (has a
 * lime 'NEW' badge)"). Covered/cut by the Ask-drawer overlay in
 * `_source/01-overview-normal.png`, so this is token-derived (fixed
 * `extended.lime`/`onLime` per `theme/Color.kt`) rather than a JPEG crop —
 * see `goldens/README.md`'s log entry for [NavPillRow]'s golden.
 */
@Composable
private fun NewBadge(modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    Surface(color = extended.lime, shape = QmShapeTokens.chip, modifier = modifier) {
        Text(
            stringResource(Res.string.nav_badge_new),
            style = MaterialTheme.typography.labelSmall,
            color = extended.onLime,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** Screens whose metrics have a selected analytics period; management cards are not time series. */
private val analyticsDestinations = setOf(NavDestination.Overview, NavDestination.Flow, NavDestination.Retention,
    NavDestination.Funnels, NavDestination.Experiments)
