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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.ErrorKind
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.UiError
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.nav.classifyWindow
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.action_refresh
import com.quietmetrix.dashboard.resources.action_signout
import com.quietmetrix.dashboard.resources.action_try_again
import com.quietmetrix.dashboard.resources.brand
import com.quietmetrix.dashboard.resources.header_ask_button
import com.quietmetrix.dashboard.resources.header_avatar_cd
import com.quietmetrix.dashboard.resources.header_progress_bar_cd
import com.quietmetrix.dashboard.resources.header_server_unreachable_message
import com.quietmetrix.dashboard.resources.header_theme_toggle_cd
import com.quietmetrix.dashboard.resources.header_toggle_compare
import com.quietmetrix.dashboard.resources.header_toggle_expert
import com.quietmetrix.dashboard.resources.ic_refresh
import com.quietmetrix.dashboard.resources.ic_signout
import com.quietmetrix.dashboard.resources.nav_badge_new
import com.quietmetrix.dashboard.resources.picker_no_projects
import com.quietmetrix.dashboard.resources.picker_select_project
import com.quietmetrix.dashboard.theme.LocalExtendedColors
import com.quietmetrix.dashboard.theme.LocalThemeMode
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.QmShapeTokens
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.ui.components.CopyErrorButton
import com.quietmetrix.dashboard.ui.components.ErrorBanner
import com.quietmetrix.dashboard.ui.components.Pill
import com.quietmetrix.dashboard.ui.components.PillVariant
import com.quietmetrix.dashboard.ui.components.handCursor
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.serverUnreachableError
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Lime-badged "shh" logo glyph (design_handoff_dashboard_redesign/README.md >
 * Header: emoji-badge logo, lime rounded square, shushing-face emoji). Kept
 * as a named constant — not inlined directly into the `Text(...)` call that
 * renders it — matching `NavDestination.emoji`'s convention of naming glyph
 * constants rather than embedding them as bare literals.
 */
private const val LOGO_EMOJI = "🤫"

/** Project-switcher dropdown glyph (no per-project emoji exists in [ApiProject], so this is a fixed stand-in matching the "📦 Projects" nav glyph). */
private const val PROJECT_EMOJI = "📦"

/** Date-range dropdown glyph (design_handoff_dashboard_redesign/README.md > Header: "🗓️ Last 7 days ▾"). */
private const val DATE_EMOJI = "🗓️"

/** Dropdown-trigger chevron glyph, shared by the project and date-range dropdowns. */
private const val CHEVRON_DOWN = "▾"

/** Compare-toggle glyph (design_handoff_dashboard_redesign/README.md > Header: "⇄ Compare toggle"). */
private const val COMPARE_EMOJI = "⇄"

/** Expert-toggle glyph (design_handoff_dashboard_redesign/README.md > Header: "🤓 Expert toggle"). */
private const val EXPERT_EMOJI = "🤓"

/** Light-theme glyph for the theme toggle (design_handoff_dashboard_redesign/README.md > Header: "☀️/🌙 theme toggle"). */
private const val THEME_LIGHT_EMOJI = "☀️"

/** Dark-theme glyph for the theme toggle. */
private const val THEME_DARK_EMOJI = "🌙"

/** Ask-the-data button glyph (design_handoff_dashboard_redesign/README.md > Header: "✨ Ask button (lavender, opens right-hand drawer)"). */
private const val ASK_EMOJI = "✨"

/** Server-unreachable banner glyph (design_handoff_dashboard_redesign/README.md > Header: "An error banner (coral background, 🔌 icon, ...)"). */
private const val PLUG_EMOJI = "🔌"

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
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val sizeClass = classifyWindow(maxWidth)

        Scaffold(
            topBar = { DashboardTopBar(viewModel, state) },
            snackbarHost = { SnackbarHost(hostState = snackbarState) },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                HeaderProgressBar(visible = state.loading || state.refreshing)
                // Mutually exclusive, not stacked — see this block's KDoc note below for why.
                val serverUnreachable = state.serverUnreachableError
                if (serverUnreachable != null) {
                    ServerUnreachableBanner(serverUnreachable, onRetry = { viewModel.refresh() })
                } else {
                    state.error?.let { ErrorBanner(it, onDismiss = { viewModel.clearError() }) }
                }
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (state.refreshing) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        content(sizeClass)
                    }
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
    val message = "$PLUG_EMOJI " + stringResource(Res.string.header_server_unreachable_message)
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
                Text(stringResource(Res.string.action_try_again), color = extended.onLime, fontWeight = FontWeight.Bold)
            }
            CopyErrorButton(error, tint = extended.onLime)
        }
    }
}

/**
 * The persistent dashboard-redesign header (design_handoff_dashboard_redesign/README.md
 * > Screens / Views > "1. Header (persistent across all screens)"): a
 * three-row [Column] — brand + project/date dropdowns, the Compare/Expert/
 * Refresh/Theme/Ask/avatar toggle row, and the nav pill row — each wrapped
 * in a [FlowRow] so it reflows onto a second line on narrow widths rather
 * than overflowing (the spec's "wraps on narrow widths, never wraps text
 * within a single control"). The nav pill row is now the only navigation
 * surface at every width (see [DashboardShell]'s KDoc) — there is no
 * hamburger/drawer trigger here any more.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DashboardTopBar(
    viewModel: DashboardViewModel,
    state: DashboardState,
) {
    val themeMode = LocalThemeMode.current
    val setThemeMode = LocalThemeModeSetter.current
    var compareOn by remember { mutableStateOf(false) }
    var expertOn by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderLogoAndWordmark()
                Spacer(Modifier.weight(1f))
                ProjectDropdown(
                    projects = state.projects,
                    currentProjectId = state.currentProjectId,
                    onSelect = { viewModel.selectProject(it) },
                )
                DateRangeDropdown(range = state.range, onSelect = { viewModel.selectRange(it) })
            }

            HeaderToggleRow(
                compareOn = compareOn,
                onCompareToggle = { compareOn = !compareOn },
                expertOn = expertOn,
                onExpertToggle = { expertOn = !expertOn },
                refreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                themeMode = themeMode,
                onToggleTheme = { setThemeMode(if (themeMode == ThemeMode.Light) ThemeMode.Dark else ThemeMode.Light) },
                onAsk = { /* Ask-your-data drawer is a separate, not-yet-built feature (README's own "AI Ask your data drawer" section); stub per this task's scope. */ },
                userEmail = state.user?.email,
                onSignOut = { viewModel.logout() },
            )

            NavPillRow(
                destinations = NavDestination.visibleFor(state.user?.role),
                current = state.activeDestination,
                onSelect = { viewModel.navigateTo(it) },
            )
        }
    }
}

/**
 * The header's left-most element: the lime-badged [LOGO_EMOJI] glyph next
 * to the "quietmetrix" wordmark (design_handoff_dashboard_redesign/README.md
 * > Header: "emoji-badge logo (lime rounded square, 🤫) + wordmark").
 */
@Composable
internal fun HeaderLogoAndWordmark(modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = extended.lime, shape = QmShapeTokens.chip) {
            Text(LOGO_EMOJI, modifier = Modifier.padding(6.dp))
        }
        Text(
            stringResource(Res.string.brand),
            style = MaterialTheme.typography.titleLarge,
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
    val triggerText = "$PROJECT_EMOJI " + (current?.name ?: if (projects.isEmpty()) noProjects else selectProjectLabel) + " $CHEVRON_DOWN"

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            enabled = projects.isNotEmpty(),
            color = LocalExtendedColors.current.chipFill,
            shape = QmShapeTokens.pill,
            modifier = Modifier.handCursor(),
        ) {
            Text(
                triggerText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name) },
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
    val triggerText = "$DATE_EMOJI " + stringResource(range.labelRes) + " $CHEVRON_DOWN"

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            color = LocalExtendedColors.current.chipFill,
            shape = QmShapeTokens.pill,
            modifier = Modifier.handCursor(),
        ) {
            Text(
                triggerText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeRange.entries.forEach { r ->
                DropdownMenuItem(
                    text = { Text(stringResource(r.labelRes)) },
                    onClick = { onSelect(r); expanded = false },
                    modifier = Modifier.handCursor(),
                )
            }
        }
    }
}

/**
 * The header's second row (design_handoff_dashboard_redesign/README.md >
 * Header: "⇄ Compare toggle ... 🤓 Expert toggle ... 🔄 Refresh button ...
 * ☀️/🌙 theme toggle ... ✨ Ask button (lavender, opens right-hand drawer)
 * ... user avatar circle"). Only [onRefresh] (pre-existing
 * `DashboardViewModel.refresh`) and the theme toggle (pre-existing
 * `LocalThemeMode`/`LocalThemeModeSetter`) are wired to real behavior;
 * [onCompareToggle]/[onExpertToggle]/[onAsk] are local, no-backend stub
 * toggles per this task's scope (no Compare/Expert-mode state exists on
 * [DashboardState] yet, and the "Ask your data" drawer is a separate,
 * not-yet-built feature) — see the Task 16 report for this call-out.
 * [onSignOut] is the pre-existing `DashboardViewModel.logout`, now reached
 * through the avatar's dropdown menu rather than a standalone button.
 */
@Composable
internal fun HeaderToggleRow(
    compareOn: Boolean,
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

    FlowRow(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToggleChip(emoji = COMPARE_EMOJI, label = stringResource(Res.string.header_toggle_compare), on = compareOn, onClick = onCompareToggle)
        ToggleChip(emoji = EXPERT_EMOJI, label = stringResource(Res.string.header_toggle_expert), on = expertOn, onClick = onExpertToggle)

        IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.handCursor()) {
            Icon(
                painter = painterResource(Res.drawable.ic_refresh),
                contentDescription = stringResource(Res.string.action_refresh),
                modifier = Modifier.size(20.dp),
            )
        }

        val themeToggleCd = stringResource(Res.string.header_theme_toggle_cd)
        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier.handCursor().semantics { contentDescription = themeToggleCd },
        ) {
            Text(if (themeMode == ThemeMode.Light) THEME_DARK_EMOJI else THEME_LIGHT_EMOJI, style = MaterialTheme.typography.titleMedium)
        }

        val askLabel = "$ASK_EMOJI " + stringResource(Res.string.header_ask_button)
        Pill(text = askLabel, variant = PillVariant.Lavender, onClick = onAsk)

        val avatarCd = stringResource(Res.string.header_avatar_cd)
        Box {
            Surface(
                onClick = { avatarMenuExpanded = true },
                color = extended.lavender,
                shape = CircleShape,
                modifier = Modifier.size(32.dp).handCursor().semantics { contentDescription = avatarCd },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (userEmail?.firstOrNull()?.uppercaseChar() ?: '?').toString(),
                        color = extended.onLime,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            DropdownMenu(expanded = avatarMenuExpanded, onDismissRequest = { avatarMenuExpanded = false }) {
                userEmail?.let {
                    DropdownMenuItem(text = { Text(it) }, onClick = {}, enabled = false)
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_signout)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_signout),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = { onSignOut(); avatarMenuExpanded = false },
                    modifier = Modifier.handCursor(),
                )
            }
        }
    }
}

/**
 * One Compare/Expert toggle pill: an emoji + label whose fill switches
 * between [PillVariant.Lime] (on) and [PillVariant.Neutral] (off) so the
 * active state reads clearly without a separate indicator glyph.
 */
@Composable
private fun ToggleChip(emoji: String, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = "$emoji $label"
    Pill(text = text, variant = if (on) PillVariant.Lime else PillVariant.Neutral, onClick = onClick, modifier = modifier)
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NavPillRow(
    destinations: List<NavDestination>,
    current: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        destinations.forEach { dest ->
            Box {
                NavPillItem(destination = dest, selected = dest == current, onClick = { onSelect(dest) })
                if (dest == NavDestination.Experiments) {
                    NewBadge(modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
}

/**
 * A single nav-row entry: [PillVariant.NavActive] when selected, or plain
 * (unfilled) emoji+label text otherwise — see [NavPillRow]'s KDoc for why
 * the inactive style has no background.
 */
@Composable
private fun NavPillItem(destination: NavDestination, selected: Boolean, onClick: () -> Unit) {
    val label = destination.emoji + " " + stringResource(destination.labelRes)
    if (selected) {
        Pill(text = label, variant = PillVariant.NavActive, onClick = onClick)
    } else {
        Surface(onClick = onClick, color = Color.Transparent, shape = QmShapeTokens.pill, modifier = Modifier.handCursor()) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
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
