package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AccessTokenDto
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.AiCapabilities
import com.quietmetrix.dashboard.api.AiHistory
import com.quietmetrix.dashboard.api.AiInsight
import com.quietmetrix.dashboard.api.AiRequest
import com.quietmetrix.dashboard.api.ApiClient
import com.quietmetrix.dashboard.api.ApiException
import com.quietmetrix.dashboard.api.ApiManagedUser
import com.quietmetrix.dashboard.api.ApiMember
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.api.CreateExperimentRequest
import com.quietmetrix.dashboard.api.CreateFunnelRequest
import com.quietmetrix.dashboard.api.CreateProjectResponse
import com.quietmetrix.dashboard.api.ErrorKind
import com.quietmetrix.dashboard.api.ExperimentDto
import com.quietmetrix.dashboard.api.ExperimentResultsResponse
import com.quietmetrix.dashboard.api.FrictionResponse
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.ProjectReportDto
import com.quietmetrix.dashboard.api.RegenerateKeyResponse
import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.api.SearchResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.TransitionsResponse
import com.quietmetrix.dashboard.api.UiError
import com.quietmetrix.dashboard.api.UpdateExperimentRequest
import com.quietmetrix.dashboard.api.UserRole
import com.quietmetrix.dashboard.api.decodeUserFromJwt
import com.quietmetrix.dashboard.api.toUiError
import com.quietmetrix.dashboard.nav.NavDestination
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.ask_error_fetch
import com.quietmetrix.dashboard.resources.ask_error_project
import com.quietmetrix.dashboard.resources.login_error_invalid_credentials
import com.quietmetrix.dashboard.resources.login_error_misconfigured
import com.quietmetrix.dashboard.resources.login_error_network
import com.quietmetrix.dashboard.resources.login_error_server
import com.quietmetrix.dashboard.resources.login_error_unknown
import com.quietmetrix.dashboard.storage.ObservationStorage
import com.quietmetrix.dashboard.storage.TokenStorage
import com.quietmetrix.dashboard.ui.screens.EXPERIMENTS_GUIDE_STEP_COUNT
import com.quietmetrix.dashboard.ui.screens.guideStepAfter
import com.quietmetrix.dashboard.ui.screens.guideStepBefore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource
import kotlin.concurrent.Volatile

/** Last observed API connection status; local stdio MCP status is not remotely observable. */
enum class ServerConnection { Unknown, Checking, Online, Offline }

data class DashboardState(
    /** Shared header preference controlling detailed statistics. */
    val expertMode: Boolean = false,
    val token: String? = null,
    val user: ApiUser? = null,
    val projects: List<ApiProject> = emptyList(),
    val currentProjectId: String? = null,
    val aggregates: AggregatesResponse? = null,
    val overviewBaseAggregates: AggregatesResponse? = null,
    val overviewFilter: OverviewFilter? = null,
    val range: TimeRange = TimeRange.Default,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** User-initiated-action failures only (create project, invite, regenerate key, ...). */
    val error: UiError? = null,
    /** Background-loader failures, keyed by section, so one bad panel doesn't blank the rest. */
    val sectionErrors: Map<DataSection, UiError> = emptyMap(),
    val loginErrorRes: StringResource? = null,
    val serverDebug: Boolean = false,
    val serverSelfhost: Boolean = false,
    /** Server base URL used by this dashboard and the MCP client configuration. */
    val serverBaseUrl: String = "",
    /** Last server metadata version, never a hardcoded dashboard version. */
    val serverVersion: String? = null,
    val serverConnection: ServerConnection = ServerConnection.Unknown,
    /** Prevents duplicate invitation requests and keeps the form visible while sending. */
    val invitationPending: Boolean = false,
    val demoMode: Boolean = false,
    val activeDestination: NavDestination = NavDestination.Overview,
    val newProjectKeys: CreateProjectResponse? = null,
    val showInfo: NavDestination? = null,
    val transitions: TransitionsResponse? = null,
    val flowPlatform: String = "all",
    val flowNode: String? = null,
    val askContext: Map<String, String> = emptyMap(),
    val selectedCohorts: String? = null,
    val sessions: SessionsResponse? = null,
    val retention: RetentionResponse? = null,
    val search: SearchResponse? = null,
    val friction: FrictionResponse? = null,
    val funnels: List<FunnelDto> = emptyList(),
    val selectedFunnelKey: String? = null,
    val selectedFunnelRevision: Int? = null,
    val funnelSaving: Boolean = false,
    val funnelSaveError: UiError? = null,
    val funnelCatalog: Map<String, List<String>> = emptyMap(),
    val funnelResults: FunnelResultsResponse? = null,
    // A/B testing experiments
    val experiments: List<ExperimentDto> = emptyList(),
    /** Null when the project's plan has no cap (paid plan, or self-host). */
    val experimentLimit: Int? = null,
    val selectedExperimentKey: String? = null,
    val experimentResults: ExperimentResultsResponse? = null,
    /** Request window associated with the accepted experiment result, never inferred from visible controls. */
    val experimentResultsRange: TimeRange? = null,
    val experimentsGuideOpen: Boolean = false,
    val experimentsGuideStep: Int = 0,
    /** Set when the most recent create-experiment call was rejected with `experiment_limit_reached`. */
    val experimentLimitReached: Boolean = false,
    // User management + API-key UX
    val users: List<ApiManagedUser> = emptyList(),
    val members: Map<String, List<ApiMember>> = emptyMap(),
    val regeneratedKey: RegenerateKeyResponse? = null,
    val lastInviteLink: String? = null,
    // Invitation acceptance flow
    val inviteEmail: String? = null,
    val inviteTokenInvalid: Boolean = false,
    val inviteSubmitting: Boolean = false,
    // Access tokens (agents/CLIs) — see docs/agents/setup.md
    val accessTokens: List<AccessTokenDto> = emptyList(),
    /** The plaintext token from the most recent create, shown once then dismissed. */
    val newAccessToken: String? = null,
    /** The single bottom-center toast currently showing, or null. Set by [DashboardViewModel.showToast],
     *  cleared by [DashboardViewModel.dismissToast] or its own ~5s auto-dismiss timer. */
    val toast: Toast? = null,
    /** Question, messages and in-flight state for the global Ask drawer. */
    val askDrawer: AskDrawerState = AskDrawerState(),
    val ai: AiCapabilities = AiCapabilities(),
    val aiInsight: AiInsight? = null,
    /** Whether the global previous-period comparison is enabled. */
    val compare: Boolean = false,
    /** UTC counter date shared by all requests in the current selection. */
    val analyticsAnchor: String = analyticsAnchorToday(),
    /** Incremented on selection/refresh so a late response cannot overwrite newer data. */
    val analyticsRevision: Long = 0,
    /** Independently loaded previous-period values; never substituted into current results. */
    val comparison: ComparisonData = ComparisonData(),
    /** Active section requests drive first-load skeletons independently of startup. */
    val loadingSections: Set<DataSection> = emptySet(),
    /** Previous-period failures leave current data visible and offer their own retry. */
    val comparisonErrors: Map<DataSection, UiError> = emptyMap(),
    /** The selected analytics drill-down, or null while its drawer is closed. */
    val drillDown: DrillDownSelection? = null,
    /** Project-local hidden observations, with snoozes expiring on the next UTC date. */
    val dismissedWorthALookIds: Set<String> = emptySet(),
) {
    /** Global role of the signed-in user. */
    val role: String? get() = user?.role
    val isAdmin: Boolean get() = role == UserRole.ADMIN
    /** Admins and developers may regenerate API keys; reviewers may not. */
    val canRegenerate: Boolean get() = role == UserRole.ADMIN || role == UserRole.DEVELOPER
    /** Admins and developers may create/edit/delete experiments; reviewers may not — mirrors
     *  the server's role check in ExperimentRoutes.kt. */
    val canWriteExperiments: Boolean get() = role == UserRole.ADMIN || role == UserRole.DEVELOPER
    /** Reviewers see everything read-only. */
    val isReadOnly: Boolean get() = role == UserRole.REVIEWER
}

/** Adapts the typed v2 daily cohorts without inventing counts for suppressed/missing measures. */
private fun ProjectReportDto.toRetentionResponse(): RetentionResponse? {
    return retentionResponseFrom(retentionCohorts)
}

private fun ProjectReportDto.toPreviousRetentionResponse(): RetentionResponse? =
    retentionResponseFrom(comparisonRetentionCohorts)

private fun ProjectReportDto.retentionResponseFrom(rows: List<com.quietmetrix.dashboard.api.ReportRetentionCohortDto>): RetentionResponse? {
    if (rows.isEmpty()) return null
    val cohorts = rows.map { cohort ->
        val states = cohort.days.mapKeys { "day_${it.key}" }.mapValues { it.value.state }
        fun rate(age: String): Double? = cohort.days[age]?.takeIf { it.state == "reported" || it.state == "zero" }?.rate
        RetentionCohort(
            cohortDate = cohort.entryDay,
            size = cohort.size.value?.toInt() ?: 0,
            day1 = rate("1"), day3 = rate("3"), day7 = rate("7"), day14 = rate("14"), day30 = rate("30"),
            dayStates = states,
            sizeState = cohort.size.state,
        )
    }
    return RetentionResponse(cohorts, source = "v2")
}


/** One background data panel that can fail independently without blanking the dashboard. */
enum class DataSection { Projects, Users, Tokens, Aggregates, Transitions, Sessions, Retention, Search, Friction, Funnels, FunnelResults, Experiments, ExperimentResults }

/**
 * A single bottom-center toast notification (design_handoff_dashboard_redesign/README.md >
 * Interactions & Behavior > "Toasts": "bottom-center, auto-dismiss ~5s, optional action button,
 * close (✕) button. Used for every completed action ... and for refresh/error feedback."; shape
 * per the README's State Management section: `toast: { emoji, title, body, err, actionLabel,
 * action } | null`).
 *
 * @param emoji large glyph shown at the start of the toast.
 * @param title short summary of what happened (e.g. "App created").
 * @param body optional one-line detail shown under [title].
 * @param err true renders the toast with error styling (used for refresh/error feedback);
 * false (default) renders the normal completed-action styling.
 * @param actionLabel optional label for an inline action button (e.g. "Copy key", "Undo"); null
 * means no action button is shown.
 * @param action invoked when the [actionLabel] button is pressed. The two are expected to be
 * set together — a non-null [actionLabel] with no [action] renders a button that does nothing.
 */
data class Toast(
    val emoji: String,
    val title: String,
    val body: String? = null,
    val err: Boolean = false,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

/** Records [error] for [section] without touching the global banner or any other section. */
internal fun DashboardState.withSectionError(section: DataSection, error: UiError): DashboardState =
    copy(sectionErrors = sectionErrors + (section to error))

/** Commits an awaited response only while its project, experiment and requested window remain selected. */
internal fun DashboardState.acceptExperimentResults(
    result: ExperimentResultsResponse,
    projectId: String,
    experimentKey: String,
    requestedRange: TimeRange,
): DashboardState = if (currentProjectId == projectId && selectedExperimentKey == experimentKey &&
    range == requestedRange && result.experiment.key == experimentKey) {
    copy(experimentResults = result, experimentResultsRange = requestedRange).clearingSectionError(DataSection.ExperimentResults)
} else this

/** Clears a previously-recorded section error, e.g. after that section reloads successfully. */
internal fun DashboardState.clearingSectionError(section: DataSection): DashboardState =
    copy(sectionErrors = sectionErrors - section)

/**
 * The first currently-recorded [DataSection] failure whose [UiError.kind] is
 * [ErrorKind.Network], or null if every background loader last succeeded (or hasn't run yet).
 * Drives the global "We can't reach your QuietMetrix server" banner
 * (design_handoff_dashboard_redesign/README.md > Header: "An error banner ... renders under the
 * header in Errors state, on every screen") — deliberately independent of
 * [DashboardState.error], which is reserved for user-initiated-action failures (create project,
 * invite, ...) and already has its own dismissible [com.quietmetrix.dashboard.ui.components.ErrorBanner].
 */
val DashboardState.serverUnreachableError: UiError?
    get() = sectionErrors.values.firstOrNull { it.kind == ErrorKind.Network }

/**
 * Clears [DashboardState.toast] only if [scheduledJob] — the auto-dismiss timer that is asking to
 * clear it — is still [currentJob], i.e. still the one [DashboardViewModel.toastDismissJob] points
 * at. If a newer toast (or a manual [DashboardViewModel.dismissToast]) has since taken over that
 * field, [scheduledJob] is stale and this is a no-op.
 *
 * Pulled out as a pure, directly-testable function (same reasoning as [confirmButtonSpec] in
 * `ConfirmDialog.kt`: isolate the decision from the coroutine/timing it's normally wrapped in) so
 * the one subtlety `Job.cancel()` can't fully guarantee on its own — cancellation is cooperative
 * and only takes effect at a suspension point, so a stale timer whose `delay()` already resumed
 * before `cancel()` could reach it would otherwise run this clear anyway, racing with whatever
 * toast replaced it — has a test (`ToastQueueTest`) that exercises the no-op branch deterministically,
 * without needing real concurrent threads or timing to reproduce the race itself.
 */
internal fun DashboardState.clearingToastIfStillCurrent(scheduledJob: Job, currentJob: Job?): DashboardState =
    if (scheduledJob === currentJob) copy(toast = null) else this

/**
 * Holds [DashboardState] and every mutator that transitions it, backed by [api] calls launched on
 * [scope].
 *
 * @param scope the coroutine scope every suspending mutator launches into. Defaults to this
 * view model's own supervisor scope for real callers; tests inject a virtual-time `TestScope`
 * (see `ToastQueueTest`) so timers like [showToast]'s ~5s auto-dismiss can be driven with
 * `advanceTimeBy` instead of a real delay.
 */
class DashboardViewModel(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private lateinit var api: ApiClient
    private val json = Json { ignoreUnknownKeys = true }
    private val connectionRequest = MutableStateFlow<Any?>(null)
    private val askRequest = MutableStateFlow<Any?>(null)
    private val aiCapabilityRequest = MutableStateFlow<Any?>(null)
    private var aiRetryRequest: AiRequest? = null
    private var observationPreferences = runCatching {
        json.decodeFromString<List<ObservationPreference>>(ObservationStorage.read() ?: "[]")
    }.getOrDefault(emptyList())
    private var observationExpiryJob: Job? = null
    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun refreshObservationPreferences() {
        _state.update { it.copy(dismissedWorthALookIds = hiddenObservationIds(observationPreferences, it.currentProjectId, analyticsAnchorToday())) }
        if (observationPreferences.any { it.snoozed && it.hiddenOn == analyticsAnchorToday() } && observationExpiryJob?.isActive != true) {
            observationExpiryJob = scope.launch {
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                delay(86_400_000L - now.mod(86_400_000L))
                observationExpiryJob = null
                refreshObservationPreferences()
            }
        }
    }
    private fun saveObservationPreferences() {
        ObservationStorage.write(json.encodeToString(observationPreferences))
        refreshObservationPreferences()
    }
    private val analyticsRequests = MutableStateFlow<Map<DataSection, Any>>(emptyMap())

    /** Persists the signed-in user so role/email survive a page reload. */
    private fun persistUser(user: ApiUser) {
        runCatching { TokenStorage.saveUser(json.encodeToString(ApiUser.serializer(), user)) }
    }

    suspend fun init(apiBaseUrl: String) {
        api = ApiClient(apiBaseUrl)
        _state.update { it.copy(serverBaseUrl = apiBaseUrl.trimEnd('/')) }
        checkServerConnectionOnce()
    }

    /** Refreshes the public API metadata; this does not probe a local MCP process. */
    fun checkServerConnection() { scope.launch { checkServerConnectionOnce() } }

    /** Only the latest metadata response can update the connection indicator. */
    private suspend fun checkServerConnectionOnce() {
        val request = Any()
        connectionRequest.value = request
        _state.update { it.copy(serverConnection = ServerConnection.Checking) }
        try {
            val meta = kotlinx.coroutines.withTimeout(10_000) { api.meta() }
            _state.update { if (connectionRequest.value === request) it.copy(serverDebug = meta.debug, serverSelfhost = meta.selfhost, serverVersion = meta.version, serverConnection = ServerConnection.Online) else it }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            _state.update { if (connectionRequest.value === request) it.copy(serverConnection = ServerConnection.Offline) else it }
        }
    }

    suspend fun login(email: String, password: String) {
        _state.update { it.copy(loading = true, loginErrorRes = null, error = null) }
        try {
            val res = api.login(email, password)
            _state.update { it.copy(token = res.token, user = res.user, loading = false) }
            TokenStorage.save(res.token)
            res.refreshToken?.let { TokenStorage.saveRefresh(it) }
            persistUser(res.user)
            loadProjects()
            loadActive(_state.value.activeDestination)
        } catch (e: ApiException) {
            _state.update { it.copy(loading = false, loginErrorRes = mapLoginError(e)) }
        } catch (_: Throwable) {
            _state.update { it.copy(loading = false, loginErrorRes = Res.string.login_error_network) }
        }
    }

    private fun mapLoginError(e: ApiException): StringResource = when {
        e.status == 401 -> Res.string.login_error_invalid_credentials
        e.code == "misconfigured" -> Res.string.login_error_misconfigured
        e.status in 500..599 -> Res.string.login_error_server
        else -> Res.string.login_error_unknown
    }

    // ---- Invitation acceptance ----

    /** Validates an invite token and loads the invitee's email for display. */
    fun loadInvite(inviteToken: String) {
        scope.launch {
            try {
                val preview = api.getInvite(inviteToken)
                _state.update { it.copy(inviteEmail = preview.email, inviteTokenInvalid = false) }
            } catch (_: Throwable) {
                _state.update { it.copy(inviteTokenInvalid = true) }
            }
        }
    }

    /** Sets the password, activates the account, and signs the user in. */
    fun acceptInvite(inviteToken: String, password: String) {
        _state.update { it.copy(inviteSubmitting = true) }
        scope.launch {
            try {
                val res = api.acceptInvite(inviteToken, password)
                TokenStorage.save(res.token)
                res.refreshToken?.let { TokenStorage.saveRefresh(it) }
                persistUser(res.user)
                _state.update {
                    it.copy(
                        token = res.token,
                        user = res.user,
                        inviteSubmitting = false,
                        inviteEmail = null,
                    )
                }
                loadProjects()
            } catch (_: Throwable) {
                _state.update { it.copy(inviteSubmitting = false, inviteTokenInvalid = true) }
            }
        }
    }

    /** Abandons the invite flow and returns to the normal sign-in screen. */
    fun cancelInvite() {
        _state.update { it.copy(inviteEmail = null, inviteTokenInvalid = false, inviteSubmitting = false) }
    }

    fun logout() {
        api.refreshToken?.let { refresh -> scope.launch { api.revokeSession(refresh) } }
        aiRetryRequest = null
        askRequest.value = null
        aiCapabilityRequest.value = null
        api.token = null
        api.refreshToken = null
        TokenStorage.clear()
        TokenStorage.clearRefresh()
        TokenStorage.clearLastProjectId()
        TokenStorage.clearUser()
        _state.value = DashboardState(serverDebug = _state.value.serverDebug, serverSelfhost = _state.value.serverSelfhost, serverBaseUrl = _state.value.serverBaseUrl, serverVersion = _state.value.serverVersion, serverConnection = _state.value.serverConnection)
    }

    fun restoreSession(token: String) {
        api.token = token
        api.refreshToken = TokenStorage.getRefresh()
        val savedUser = TokenStorage.getUser()?.let {
            runCatching { json.decodeFromString(ApiUser.serializer(), it) }.getOrNull()
        } ?: decodeUserFromJwt(token)  // fallback: read role/email from the token itself
        _state.update { it.copy(token = token, user = savedUser, loading = true) }
        scope.launch {
            try {
                val res = api.listProjects()
                val lastId = TokenStorage.getLastProjectId()
                val currentId = when {
                    lastId != null && res.projects.any { it.id == lastId } -> lastId
                    else -> res.projects.firstOrNull()?.id
                }
                _state.update { it.copy(projects = res.projects, currentProjectId = currentId, loading = false) }
                if (currentId != null) {
                    loadAggregates()
                    loadTransitions()
                    loadSearch()
                    loadFriction()
                    loadSessions()
                    loadRetentionOnce()
                    loadFunnelsOnce()
                }
            } catch (e: ApiException) {
                if (e.status == 401) {
                    if (api.refresh()) {
                        TokenStorage.save(api.token!!)
                        TokenStorage.saveRefresh(api.refreshToken!!)
                        try {
                            val res = api.listProjects()
                            val lastId = TokenStorage.getLastProjectId()
                            val currentId = when {
                                lastId != null && res.projects.any { it.id == lastId } -> lastId
                                else -> res.projects.firstOrNull()?.id
                            }
                            _state.update { it.copy(projects = res.projects, currentProjectId = currentId, loading = false) }
                            if (currentId != null) {
                                loadAggregates()
                                loadTransitions()
                                loadSearch()
                                loadFriction()
                                loadSessions()
                                loadRetentionOnce()
                            }
                        } catch (e: Throwable) {
                            _state.update { it.copy(error = e.toUiError("GET /projects (after token refresh)"), loading = false) }
                        }
                        return@launch
                    }
                    api.token = null
                    api.refreshToken = null
                    TokenStorage.clear()
                    TokenStorage.clearRefresh()
                    TokenStorage.clearLastProjectId()
                    TokenStorage.clearUser()
                    _state.update { it.copy(token = null, user = null, loading = false) }
                    return@launch
                }
                _state.update { it.copy(error = e.toUiError("GET /projects"), loading = false) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("GET /projects"), loading = false) }
            }
        }
    }

    fun navigateTo(destination: NavDestination) {
        _state.update { it.copy(activeDestination = destination) }
        scope.launch { loadActive(destination) }
    }

    /** Refreshes live data while preserving and dimming the current screen. */
    fun refresh() {
        if (_state.value.refreshing) return
        refreshObservationPreferences()
        val destination = _state.value.activeDestination
        _state.update { it.copy(refreshing = true, analyticsAnchor = analyticsAnchorToday(), analyticsRevision = it.analyticsRevision + 1) }
        scope.launch {
            try {
                // Keep the spinner visible long enough to read even on fast loads.
                val minVisible = launch { kotlinx.coroutines.delay(350) }
                loadActive(destination)
                minVisible.join()
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** Suspending fetch for a destination, shared by navigation and refresh. */
    private suspend fun loadActive(destination: NavDestination) {
        when (destination) {
            // Task 19 fix round 1: loadRetentionOnce() added so the Overview vitals grid's
            // "Came back next day" card has real data without requiring a separate visit to the
            // Retention tab first (see OverviewVitalsLogic.kt's computeVitalsNumbers KDoc).
            NavDestination.Overview  -> { loadAggregates(); loadSessions(); loadRetentionOnce() }
            NavDestination.Flow      -> { loadAggregates(); loadTransitions(); loadSearch(); loadFriction() }
            NavDestination.Retention -> loadRetentionOnce()
            NavDestination.Projects  -> loadProjects()
            NavDestination.Users     -> loadUsersOnce()
            NavDestination.Funnels   -> loadFunnelsOnce()
            NavDestination.Experiments -> {
                loadExperimentsOnce()
            }
            NavDestination.Settings  -> loadTokensOnce()
        }
    }

    fun selectProject(projectId: String) {
        aiRetryRequest = null
        askRequest.value = null
        _state.update {
            it.copy(
                currentProjectId = projectId, overviewFilter = null, overviewBaseAggregates = null, askDrawer = AskDrawerState(), ai = AiCapabilities(), aiInsight = null, aggregates = null, sessions = null, retention = null, transitions = null,
                search = null, friction = null, comparison = ComparisonData(), comparisonErrors = emptyMap(),
                sectionErrors = emptyMap(), analyticsRevision = it.analyticsRevision + 1, drillDown = null, funnels = emptyList(), selectedFunnelKey = null, selectedFunnelRevision = null, funnelCatalog = emptyMap(), funnelSaveError = null, funnelResults = null,
                experiments = emptyList(), experimentLimit = null, selectedExperimentKey = null, experimentResults = null,
            )
        }
        refreshObservationPreferences()
        TokenStorage.saveLastProjectId(projectId)
        scope.launch { loadAggregates(); loadTransitions(); loadSearch(); loadFriction(); loadSessions(); loadRetentionOnce(); loadFunnelsOnce(); loadExperimentsOnce() }
    }

    fun setOverviewFilter(kind: String? = null, value: String? = null) {
        val filter = if (kind == null || value == null) null else OverviewFilter(kind, value)
        if (_state.value.overviewFilter == filter) return
        aiRetryRequest = null
        askRequest.value = null
        _state.update { it.copy(overviewFilter = filter, aggregates = null, sessions = null, retention = null,
            comparison = ComparisonData(), analyticsRevision = it.analyticsRevision + 1,
            aiInsight = null, askDrawer = AskDrawerState(), drillDown = null) }
        scope.launch { loadAggregates(); loadSessions(); loadRetentionOnce() }
    }

    fun selectRange(range: TimeRange) {
        _state.update { it.copy(range = range, aiInsight = null, askDrawer = AskDrawerState(open = it.askDrawer.open), aggregates = null, transitions = null, sessions = null, retention = null,
            search = null, friction = null, funnelResults = null, experimentResults = null, experimentResultsRange = null,
            comparison = ComparisonData(), comparisonErrors = emptyMap(), sectionErrors = emptyMap(),
            analyticsAnchor = analyticsAnchorToday(), analyticsRevision = it.analyticsRevision + 1, drillDown = null) }
        scope.launch {
            loadAggregates(); loadTransitions(); loadSearch(); loadFriction(); loadSessions(); loadRetentionOnce()
            loadFunnelResultsOnce(); loadExperimentResultsOnce()
        }
    }

    /** Toggles the dashboard-wide previous-period comparison preference. */
    fun toggleCompare() {
        _state.update { it.copy(compare = !it.compare, aiInsight = null, askDrawer = AskDrawerState(), comparison = ComparisonData(), comparisonErrors = emptyMap(),
            analyticsRevision = it.analyticsRevision + 1, drillDown = null) }
        scope.launch { loadActive(_state.value.activeDestination) }
    }

    /** Opens a drill-down using values already present in dashboard state. */
    fun openDrillDown(kind: String, key: String) {
        _state.update { state -> state.copy(drillDown = DrillDownSelection(kind, key)) }
    }

    /** Closes the shared analytics drill-down drawer. */
    fun closeDrillDown() { _state.update { it.copy(drillDown = null) } }

    fun setDemoMode(enabled: Boolean) {
        if (!_state.value.serverDebug) return
        _state.update { it.copy(demoMode = enabled, ai = AiCapabilities(), aiInsight = null, askDrawer = AskDrawerState(), aggregates = null, comparison = ComparisonData(), analyticsRevision = it.analyticsRevision + 1, drillDown = null) }
        scope.launch { loadAggregates() }
    }

    fun createProject(name: String, description: String? = null, iconKey: String = "book", declaredPlatforms: List<String> = emptyList()) {
        scope.launch {
            try {
                val out = api.createProject(name, description, iconKey, declaredPlatforms)
                _state.update { it.copy(newProjectKeys = out) }
                loadProjects()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("POST /projects")) }
            }
        }
    }

    fun updateProject(projectId: String, name: String) {
        scope.launch {
            try {
                api.updateProject(projectId, name.trim())
                loadProjects()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = e.toUiError("PATCH /projects/{id}")) }
            }
        }
    }

    fun deleteProject(projectId: String) {
        scope.launch {
            try {
                api.deleteProject(projectId)
                loadProjects()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("DELETE /projects/{id}")) }
            }
        }
    }

    fun dismissNewProjectKeys() {
        _state.update { it.copy(newProjectKeys = null) }
    }

    // ---- API key regeneration ----

    fun regenerateApiKey(projectId: String) {
        scope.launch {
            try {
                val out = api.regenerateApiKey(projectId)
                _state.update { it.copy(regeneratedKey = out) }
                loadProjects()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("POST /projects/{id}/regenerate-key")) }
            }
        }
    }

    fun dismissRegeneratedKey() {
        _state.update { it.copy(regeneratedKey = null) }
    }

    // ---- User management (admin) ----

    fun loadUsers() {
        scope.launch { loadUsersOnce() }
    }

    private suspend fun loadUsersOnce() {
        loadList(DataSection.Users, "GET /users", fetch = { api.listUsers().users }) { copy(users = it) }
    }

    /** Tracks list loading and rejects replaced requests and project-scoped stale replies. */
    private suspend fun <T> loadList(section: DataSection, endpoint: String, projectId: String? = null, fetch: suspend () -> T, accept: DashboardState.(T) -> DashboardState) {
        val token = Any()
        analyticsRequests.update { it + (section to token) }
        _state.update { it.copy(loadingSections = it.loadingSections + section) }
        fun valid(state: DashboardState) = analyticsRequests.value[section] === token && (projectId == null || state.currentProjectId == projectId)
        try {
            val result = fetch()
            _state.update { if (valid(it)) accept(it, result).clearingSectionError(section) else it }
        } catch (e: Throwable) {
            _state.update { if (valid(it)) it.withSectionError(section, e.toUiError(endpoint)) else it }
        } finally {
            if (analyticsRequests.value[section] === token) _state.update { it.copy(loadingSections = it.loadingSections - section) }
        }
    }

    /** Sends one validated invitation at a time and preserves the resulting shareable link. */
    fun inviteUser(email: String, role: String, projectIds: List<String> = emptyList()) = inviteAccount(email, role, projectIds, false)

    fun inviteOrganization(email: String) {
        if (_state.value.user?.platformOperator != true || _state.value.serverSelfhost) return
        inviteAccount(email, UserRole.ADMIN, emptyList(), true)
    }

    private fun inviteAccount(email: String, role: String, projectIds: List<String>, newOrganization: Boolean) {
        if (!com.quietmetrix.dashboard.ui.screens.isInvitationEmailValid(email)) return
        while (true) {
            val state = _state.value
            if (!state.isAdmin || state.invitationPending) return
            if (_state.compareAndSet(state, state.copy(invitationPending = true, error = null, lastInviteLink = null))) break
        }
        val session = _state.value.token
        scope.launch {
            try {
                val res = api.inviteUser(email.trim(), role, newOrganization)
                _state.update { if (it.token == session) it.copy(lastInviteLink = res.inviteLink) else it }
                for (projectId in projectIds.distinct()) api.addMember(projectId, email.trim())
                if (_state.value.token == session) {
                    loadUsers()
                    projectIds.distinct().forEach(::loadMembers)
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { if (it.token == session) it.copy(error = e.toUiError("POST /users/invite")) else it }
            } finally {
                _state.update { if (it.token == session) it.copy(invitationPending = false) else it }
            }
        }
    }

    fun recoverUser(userId: String) {
        val session = _state.value.token
        if (!_state.value.isAdmin || _state.value.invitationPending) return
        _state.update { it.copy(invitationPending = true, lastInviteLink = null) }
        scope.launch {
            try {
                val result = api.recoverUser(userId)
                _state.update { if (it.token == session) it.copy(lastInviteLink = result.inviteLink) else it }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { if (it.token == session) it.copy(error = e.toUiError("POST /users/{id}/recover")) else it }
            } finally { _state.update { if (it.token == session) it.copy(invitationPending = false) else it } }
        }
    }

    fun dismissInviteLink() {
        _state.update { it.copy(lastInviteLink = null) }
    }

    fun updateUserRole(userId: String, role: String) {
        scope.launch {
            try {
                api.updateUserRole(userId, role)
                loadUsers()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("PATCH /users/{id}")) }
            }
        }
    }

    fun deleteUser(userId: String) {
        scope.launch {
            try {
                api.deleteUser(userId)
                loadUsers()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("DELETE /users/{id}")) }
            }
        }
    }

    // ---- Access tokens (agents/CLIs) ----

    fun loadTokens() { scope.launch { loadTokensOnce() } }

    private suspend fun loadTokensOnce() {
        loadList(DataSection.Tokens, "GET /tokens", fetch = { api.listTokens().tokens }) { copy(accessTokens = it) }
    }

    fun createToken(name: String, scopes: List<String>? = null, expiresInDays: Long? = null) {
        scope.launch {
            try {
                val res = api.createToken(name, scopes, expiresInDays)
                _state.update { it.copy(newAccessToken = res.token) }
                loadTokens()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("POST /tokens")) }
            }
        }
    }

    fun dismissNewAccessToken() {
        _state.update { it.copy(newAccessToken = null) }
    }

    fun revokeToken(tokenId: String) {
        scope.launch {
            try {
                api.revokeToken(tokenId)
                loadTokens()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("DELETE /tokens/{id}")) }
            }
        }
    }

    // ---- Project assignment (admin) ----

    fun loadMembers(projectId: String) {
        scope.launch {
            try {
                val res = api.listMembers(projectId)
                _state.update { it.copy(members = it.members + (projectId to res.members)) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("GET /projects/{id}/members")) }
            }
        }
    }

    fun assignMember(projectId: String, email: String) {
        scope.launch {
            try {
                api.addMember(projectId, email)
                loadMembers(projectId)
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("POST /projects/{id}/members")) }
            }
        }
    }

    fun unassignMember(projectId: String, userId: String) {
        scope.launch {
            try {
                api.removeMember(projectId, userId)
                loadMembers(projectId)
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("DELETE /projects/{id}/members/{id}")) }
            }
        }
    }

    // ---- Funnels ----

    private suspend fun loadFunnelsOnce() {
        val s = _state.value
        val projectId = s.currentProjectId ?: return
        loadList(DataSection.Funnels, "GET /projects/{id}/funnels", projectId, fetch = { api.listFunnels(projectId) }) { res ->
            val currentKey = selectedFunnelKey?.takeIf { key -> res.funnels.any { it.funnelKey == key } } ?: res.funnels.firstOrNull()?.funnelKey
            copy(funnels = res.funnels, selectedFunnelKey = currentKey, selectedFunnelRevision = selectedFunnelRevision.takeIf { currentKey == selectedFunnelKey })
        }
        if (_state.value.currentProjectId == projectId) {
            runCatching { api.projectReport(projectId, s.range, s.analyticsAnchor) }.getOrNull()?.let { report ->
                _state.update { if (it.currentProjectId == projectId) it.copy(funnelCatalog = report.definitions) else it }
            }
            loadFunnelResultsOnce()
        }
    }

    /** Loads selected funnel statistics without allowing stale controls to commit a response. */
    private suspend fun loadFunnelResultsOnce() {
        val snapshot = _state.value
        val projectId = snapshot.currentProjectId ?: return
        val key = snapshot.selectedFunnelKey ?: return
        loadAnalytics(DataSection.FunnelResults, "GET /projects/{id}/funnels/{key}/results", snapshot,
            fetch = { previous, anchor -> api.funnelResults(projectId, key, snapshot.range, previous = previous, anchor = anchor, revision = snapshot.selectedFunnelRevision) },
            accept = { result, previous ->
                if (previous) copy(comparison = comparison.copy(funnel = result)) else copy(funnelResults = result)
            })
    }

    fun selectFunnel(funnelKey: String) {
        askRequest.value = null; aiRetryRequest = null
        _state.update { it.copy(askContext = emptyMap(), askDrawer = AskDrawerState(open = it.askDrawer.open)) }
        _state.update { it.copy(selectedFunnelKey = funnelKey, selectedFunnelRevision = null, funnelResults = null, comparison = it.comparison.copy(funnel = null), analyticsRevision = it.analyticsRevision + 1, drillDown = null) }
        scope.launch { loadFunnelResultsOnce() }
    }

    fun selectFunnelRevision(revision: Int) {
        askRequest.value = null; aiRetryRequest = null
        _state.update { it.copy(askContext = emptyMap(), askDrawer = AskDrawerState(open = it.askDrawer.open)) }
        _state.update { it.copy(selectedFunnelRevision = revision, funnelResults = null,
            comparison = it.comparison.copy(funnel = null), analyticsRevision = it.analyticsRevision + 1) }
        scope.launch { loadFunnelResultsOnce() }
    }
    fun clearFunnelSaveError() { _state.update { it.copy(funnelSaveError = null) } }

    fun deleteFunnel(funnelKey: String) {
        val projectId = _state.value.currentProjectId ?: return
        scope.launch {
            try {
                api.deleteFunnel(projectId, funnelKey)
                _state.update {
                    it.copy(selectedFunnelKey = if (it.selectedFunnelKey == funnelKey) null else it.selectedFunnelKey)
                }
                loadFunnelsOnce()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("DELETE /projects/{id}/funnels/{key}")) }
            }
        }
    }

    fun createFunnel(request: CreateFunnelRequest) = saveFunnel(request)

    fun saveFunnel(request: CreateFunnelRequest, editing: Boolean = false, onSaved: () -> Unit = {}) {
        val snapshot = _state.value
        val projectId = snapshot.currentProjectId ?: return
        if (snapshot.funnelSaving) return
        _state.update { it.copy(funnelSaving = true, funnelSaveError = null) }
        scope.launch {
            try {
                val saved = if (editing) api.updateFunnel(projectId, request.funnelKey,
                    com.quietmetrix.dashboard.api.UpdateFunnelRequest(name=request.name, description=request.description,
                        steps=request.steps, windowSeconds=request.windowSeconds, countMode=request.countMode,
                        identityScope=request.identityScope, correlationProperty=request.correlationProperty))
                    else api.createFunnel(projectId, request)
                if (_state.value.currentProjectId == projectId && _state.value.token == snapshot.token) {
                    _state.update { it.copy(selectedFunnelKey = saved.funnelKey, selectedFunnelRevision = null, funnelResults = null) }
                    onSaved()
                    loadFunnelsOnce()
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { if (it.currentProjectId == projectId && it.token == snapshot.token) it.copy(funnelSaveError = e.toUiError("Save funnel")) else it }
            } finally {
                _state.update { it.copy(funnelSaving = false) }
            }
        }
    }

    // ---- Experiments (A/B testing) ----

    private suspend fun loadExperimentsOnce() {
        val s = _state.value
        val projectId = s.currentProjectId ?: return
        loadList(DataSection.Experiments, "GET /projects/{id}/experiments", projectId, fetch = { api.listExperiments(projectId) }) { res ->
            val currentKey = selectedExperimentKey?.takeIf { key -> res.experiments.any { it.key == key } } ?: res.experiments.firstOrNull()?.key
            copy(experiments = res.experiments, experimentLimit = res.limit, selectedExperimentKey = currentKey)
        }
        if (_state.value.currentProjectId == projectId) loadExperimentResultsOnce()
    }

    /** Loads selected experiment statistics without allowing stale controls to commit a response. */
    private suspend fun loadExperimentResultsOnce() {
        val snapshot = _state.value
        val projectId = snapshot.currentProjectId ?: return
        val key = snapshot.selectedExperimentKey ?: return
        loadAnalytics(DataSection.ExperimentResults, "GET /projects/{id}/experiments/{key}/results", snapshot,
            fetch = { previous, anchor -> api.experimentResults(projectId, key, snapshot.range, previous = previous, anchor = anchor) },
            accept = { result, previous ->
                if (previous) copy(comparison = comparison.copy(experiment = result)) else copy(experimentResults = result, experimentResultsRange = snapshot.range)
            })
    }

    fun selectExperiment(experimentKey: String) {
        _state.update { it.copy(selectedExperimentKey = experimentKey, experimentResults = null, experimentResultsRange = null, comparison = it.comparison.copy(experiment = null), analyticsRevision = it.analyticsRevision + 1, drillDown = null) }
        scope.launch { loadExperimentResultsOnce() }
    }

    /** Creates a `draft` experiment. On `experiment_limit_reached`, sets
     *  [DashboardState.experimentLimitReached] instead of the generic error path so the
     *  create form can show the free-plan upgrade message specifically. */
    fun createExperiment(request: CreateExperimentRequest) {
        val projectId = _state.value.currentProjectId ?: return
        scope.launch {
            try {
                api.createExperiment(projectId, request)
                _state.update { it.copy(experimentLimitReached = false) }
                loadExperimentsOnce()
            } catch (e: ApiException) {
                if (e.code == "experiment_limit_reached") {
                    _state.update { it.copy(experimentLimitReached = true) }
                } else {
                    _state.update { it.copy(error = e.toUiError("POST /projects/{id}/experiments")) }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("POST /projects/{id}/experiments")) }
            }
        }
    }

    fun dismissExperimentLimitReached() {
        _state.update { it.copy(experimentLimitReached = false) }
    }

    fun updateExperiment(experimentKey: String, request: UpdateExperimentRequest) {
        val projectId = _state.value.currentProjectId ?: return
        scope.launch {
            try {
                api.updateExperiment(projectId, experimentKey, request)
                loadExperimentsOnce()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("PATCH /projects/{id}/experiments/{key}")) }
            }
        }
    }

    /** Toggles the shared expert preference across dashboard destinations. */
    fun toggleExpertMode() {
        _state.update { it.copy(expertMode = !it.expertMode) }
    }

    /** Convenience for the status-only transitions the list screen's action buttons trigger
     *  (Start/Pause/Resume/End) — see ExperimentsLogic.availableActions(). */
    fun changeExperimentStatus(experimentKey: String, status: String, selectedVariant: String? = null) {
        updateExperiment(experimentKey, UpdateExperimentRequest(status = status, selectedVariant = selectedVariant))
    }

    fun deleteExperiment(experimentKey: String) {
        val projectId = _state.value.currentProjectId ?: return
        scope.launch {
            try {
                api.deleteExperiment(projectId, experimentKey)
                _state.update {
                    it.copy(selectedExperimentKey = if (it.selectedExperimentKey == experimentKey) null else it.selectedExperimentKey)
                }
                loadExperimentsOnce()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("DELETE /projects/{id}/experiments/{key}")) }
            }
        }
    }

    // ---- Experiments setup guide ----

    /** Opens the guide on demand (help button) — always works, regardless of dismissal state. */
    fun openExperimentsGuide() {
        _state.update { it.copy(experimentsGuideOpen = true, experimentsGuideStep = 0) }
    }

    fun nextExperimentsGuideStep() {
        _state.update { it.copy(experimentsGuideStep = guideStepAfter(it.experimentsGuideStep, EXPERIMENTS_GUIDE_STEP_COUNT)) }
    }

    fun previousExperimentsGuideStep() {
        _state.update { it.copy(experimentsGuideStep = guideStepBefore(it.experimentsGuideStep)) }
    }

    /** Closes the guide, whether finished or skipped. */
    fun closeExperimentsGuide() {
        _state.update { it.copy(experimentsGuideOpen = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSectionError(section: DataSection) {
        _state.update { it.clearingSectionError(section) }
    }

    fun showTabInfo(destination: NavDestination) {
        _state.update { it.copy(showInfo = destination) }
    }

    fun dismissTabInfo() {
        _state.update { it.copy(showInfo = null) }
    }

    private suspend fun loadProjects() {
        loadList(DataSection.Projects, "GET /projects", fetch = { api.listProjects() }) { res ->
            val selected = currentProjectId?.takeIf { key -> res.projects.any { it.id == key } } ?: res.projects.firstOrNull()?.id
            val changed = selected != currentProjectId
            copy(projects = res.projects, currentProjectId = selected, ai = if (changed) AiCapabilities() else ai,
                aiInsight = if (changed) null else aiInsight, askDrawer = if (changed) AskDrawerState() else askDrawer,
                analyticsRevision = analyticsRevision + if (changed) 1 else 0)
        }
        refreshObservationPreferences()
    }

    /** Loads aggregates and an optional previous period within one captured selection. */
    private suspend fun loadAggregates() {
        scope.launch { loadAi() }
        val snapshot = _state.value
        if (snapshot.currentProjectId == null && !snapshot.demoMode) return
        loadAnalytics(DataSection.Aggregates, "GET /projects/{id}/aggregates", snapshot,
            fetch = { previous, anchor ->
                val filter = snapshot.reportFilter()
                val filtered = filter != OverviewFilter("platform", "all")
                val legacy = if (filtered) AggregatesResponse() else api.aggregates(snapshot.currentProjectId.orEmpty(), snapshot.range, snapshot.demoMode, previous = previous, anchor = anchor)
                if (snapshot.demoMode || snapshot.range == TimeRange.Hour1) legacy else {
                    val report = if (filtered) api.projectReport(snapshot.currentProjectId.orEmpty(), snapshot.range, anchor, snapshot.compare, filter.kind, filter.value)
                        else runCatching { api.projectReport(snapshot.currentProjectId.orEmpty(), snapshot.range, anchor, snapshot.compare) }.getOrNull()
                    report?.toAggregates(legacy, previous) ?: legacy
                }
            },
            accept = { result, previous ->
                if (previous) copy(comparison = comparison.copy(aggregates = result)) else copy(aggregates = result, overviewBaseAggregates = if (overviewFilter == null) result else overviewBaseAggregates)
            })
    }

    /** Loads transitions and an optional previous period within one captured selection. */
    private suspend fun loadTransitions() {
        val snapshot = _state.value
        if (snapshot.currentProjectId == null && !snapshot.demoMode) return
        loadAnalytics(DataSection.Transitions, "GET /projects/{id}/transitions", snapshot,
            fetch = { previous, anchor -> api.transitions(snapshot.currentProjectId.orEmpty(), snapshot.range, previous = previous, anchor = anchor, platform = snapshot.flowPlatform) },
            accept = { result, previous ->
                if (previous) copy(comparison = comparison.copy(transitions = result)) else copy(transitions = result)
            })
    }

    fun setFlowNode(node: String?) {
        if (_state.value.flowNode == node) return
        askRequest.value = null; aiRetryRequest = null
        _state.update { it.copy(flowNode = node, askContext = emptyMap(), askDrawer = AskDrawerState(open = it.askDrawer.open)) }
    }
    fun setSelectedCohorts(cohorts: String?) {
        if (_state.value.selectedCohorts == cohorts) return
        askRequest.value = null; aiRetryRequest = null
        _state.update { it.copy(selectedCohorts = cohorts, askContext = emptyMap(), askDrawer = AskDrawerState(open = it.askDrawer.open)) }
    }

    fun setFlowPlatform(platform: String) {
        if (platform !in setOf("all", "android", "ios", "web")) return
        if (_state.value.flowPlatform == platform) return
        _state.update { it.copy(flowPlatform = platform, transitions = null) }
        scope.launch { loadTransitions() }
    }

    /** Loads search and an optional previous period within one captured selection. */
    private suspend fun loadSearch() {
        val snapshot = _state.value
        if (snapshot.currentProjectId == null && !snapshot.demoMode) return
        loadAnalytics(DataSection.Search, "GET /projects/{id}/search", snapshot,
            fetch = { previous, anchor -> api.search(snapshot.currentProjectId.orEmpty(), snapshot.range, previous = previous, anchor = anchor) },
            accept = { result, previous ->
                if (previous) copy(comparison = comparison.copy(search = result)) else copy(search = result)
            })
    }

    /** Loads friction and an optional previous period within one captured selection. */
    private suspend fun loadFriction() {
        val snapshot = _state.value
        if (snapshot.currentProjectId == null && !snapshot.demoMode) return
        loadAnalytics(DataSection.Friction, "GET /projects/{id}/friction", snapshot,
            fetch = { previous, anchor -> api.friction(snapshot.currentProjectId.orEmpty(), snapshot.range, previous = previous, anchor = anchor) },
            accept = { result, previous ->
                if (previous) copy(comparison = comparison.copy(friction = result)) else copy(friction = result)
            })
    }

    /** Loads sessions and an optional previous period within one captured selection. */
    private suspend fun loadSessions() {
        val snapshot = _state.value
        if (snapshot.currentProjectId == null && !snapshot.demoMode) return
        loadAnalytics(DataSection.Sessions, "GET /projects/{id}/sessions", snapshot,
            fetch = { previous, anchor -> if (snapshot.reportFilter() != OverviewFilter("platform", "all")) SessionsResponse() else api.sessions(snapshot.currentProjectId.orEmpty(), snapshot.range, previous = previous, anchor = anchor) },
            accept = { result, previous ->
                if (previous) copy(comparison = comparison.copy(sessions = result)) else copy(sessions = result)
            })
    }

    /** Loads retention and an optional previous period within one captured selection. */
    private suspend fun loadRetentionOnce() {
        val snapshot = _state.value
        if (snapshot.currentProjectId == null && !snapshot.demoMode) return
        loadAnalytics(DataSection.Retention, "GET /projects/{id}/retention", snapshot,
            fetch = { previous, anchor ->
                val filter = snapshot.reportFilter()
                val filtered = filter != OverviewFilter("platform", "all")
                val legacy = if (filtered) RetentionResponse(source = "v2") else api.retention(snapshot.currentProjectId.orEmpty(), snapshot.range, previous = previous, anchor = anchor)
                if (snapshot.demoMode || snapshot.range == TimeRange.Hour1) legacy else {
                    val report = runCatching { api.projectReport(snapshot.currentProjectId.orEmpty(), snapshot.range, anchor, compare = snapshot.compare, filterKind = filter.kind, filterValue = filter.value) }.getOrNull()
                    (if (previous) report?.toPreviousRetentionResponse() else report?.toRetentionResponse()) ?: legacy
                }
            },
            accept = { result, previous ->
                if (previous) copy(comparison = comparison.copy(retention = result)) else copy(retention = result)
            })
    }

    /** Loads current data first; comparison failure preserves the successful current response. */
    private suspend fun <T> loadAnalytics(
        section: DataSection,
        endpoint: String,
        snapshot: DashboardState,
        fetch: suspend (Boolean, String) -> T,
        accept: DashboardState.(T, Boolean) -> DashboardState,
    ) {
        val context = analyticsContext(snapshot)
        val request = Any()
        analyticsRequests.update { it + (section to request) }
        _state.update { it.copy(loadingSections = it.loadingSections + section) }
        fun stillCurrent(state: DashboardState): Boolean =
            state.matchesAnalyticsContext(context) && analyticsRequests.value[section] === request
        try {
            val current = fetch(false, context.anchor)
            _state.update { if (stillCurrent(it)) it.accept(current, false).clearingSectionError(section) else it }
            if (context.compare && !context.demo && stillCurrent(_state.value)) {
                try {
                    val previous = fetch(true, context.anchor)
                    _state.update { if (stillCurrent(it)) it.accept(previous, true).copy(comparisonErrors = it.comparisonErrors - section) else it }
                } catch (e: Throwable) {
                    _state.update { if (stillCurrent(it)) it.copy(comparisonErrors = it.comparisonErrors + (section to e.toUiError(endpoint + "?period=previous"))) else it }
                }
            }
        } catch (e: Throwable) {
            _state.update { if (stillCurrent(it)) it.withSectionError(section, e.toUiError(endpoint)) else it }
        } finally {
            if (analyticsRequests.value[section] === request) {
                _state.update { it.copy(loadingSections = it.loadingSections - section) }
            }
        }
    }

    /** Captures a bounded prior conversation and the dashboard's current UTC date context. */
    private fun aiRequest(snapshot: DashboardState, question: String = ""): AiRequest {
        val history = snapshot.askDrawer.messages.filter { it.role != AskMessageRole.Error }.dropLast(if (question.isNotEmpty()) 1 else 0).takeLast(6)
            .map { AiHistory(if (it.role == AskMessageRole.User) "user" else "assistant", it.text.take(2000)) }
        return AiRequest(context = aiSelectionContext(snapshot), question = question, language = androidx.compose.ui.text.intl.Locale.current.language, range = snapshot.range.token, anchor = snapshot.analyticsAnchor, compare = snapshot.compare,
            history = if (question.isEmpty()) emptyList() else history, operation_id = kotlin.random.Random.nextBytes(16).joinToString("") { byte -> (byte.toInt() and 255).toString(16).padStart(2, '0') })
    }

    /** Capability refresh rejects responses from a previous selection or logged-out session. */
    private suspend fun loadAiCapabilities(snapshot: DashboardState): AiCapabilities? {
        val project = snapshot.currentProjectId ?: return null
        val request = Any()
        aiCapabilityRequest.value = request
        return try {
            val result = api.aiCapabilities(project)
            _state.update { if (aiCapabilityRequest.value === request && it.currentProjectId == project && it.token == snapshot.token && it.analyticsRevision == snapshot.analyticsRevision)
                it.copy(ai = result, aiInsight = if (!result.enabled) null else it.aiInsight, askDrawer = if (!result.enabled) AskDrawerState() else if (!result.available) it.askDrawer.copy(loading = false) else it.askDrawer) else it }
            result
        } catch (_: Throwable) {
            _state.update { if (aiCapabilityRequest.value === request && it.currentProjectId == project && it.token == snapshot.token && it.analyticsRevision == snapshot.analyticsRevision) it.copy(ai = AiCapabilities(), aiInsight = null, askDrawer = AskDrawerState()) else it }
            null
        }
    }

    /** Computed summaries render first; optional AI text loads without blocking analytics. */
    private suspend fun loadAi() {
        val snapshot = _state.value
        if (snapshot.token == null || snapshot.demoMode) return
        val access = loadAiCapabilities(snapshot) ?: return
        if (!access.enabled) return
        try {
            val summary = api.aiInsight(snapshot.currentProjectId!!, aiRequest(snapshot))
            _state.update { if (it.currentProjectId == snapshot.currentProjectId && it.token == snapshot.token && it.analyticsRevision == snapshot.analyticsRevision && aiSelectionContext(it) == aiSelectionContext(snapshot) && it.ai.enabled) it.copy(aiInsight = summary) else it }
            loadAiCapabilities(snapshot)
        } catch (_: Throwable) { }
    }

    // ---- Ask drawer ----

    /** Opens the Ask drawer, optionally pre-filling a drill-down or result-specific question. */
    fun openAskDrawer(prefill: String? = null, context: Map<String, String> = emptyMap()) {
        _state.update { it.copy(askContext = context) }
        _state.update { it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Open(prefill)), drillDown = null) }
    }

    /** Closes the Ask drawer without discarding its conversation. */
    fun closeAskDrawer() {
        _state.update { it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Close)) }
    }

    /** Updates the Ask input draft. */
    fun editAskDraft(text: String) {
        _state.update { it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Edit(text))) }
    }

    /** Sends a question to the authenticated, privacy-gated computed analytics endpoint. */
    fun askQuestion(question: String? = null) {
        val current = _state.value
        if (current.askDrawer.loading || !current.ai.available) return
        val q = question ?: current.askDrawer.draft
        val pending = reduceAskDrawer(current.askDrawer, AskDrawerEvent.Submit(q))
        if (pending === current.askDrawer || pending.messages == current.askDrawer.messages) return
        _state.update { it.copy(askDrawer = pending) }
        val snapshot = _state.value
        val request = Any()
        askRequest.value = request
        scope.launch {
            val projectId = snapshot.currentProjectId
            if (projectId == null) {
                _state.update { it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Fail("", Res.string.ask_error_project))) }
                return@launch
            }
            try {
                val submission = aiRequest(snapshot, pending.retryQuestion.orEmpty())
                aiRetryRequest = submission
                val answer = api.aiAsk(projectId, submission)
                _state.update { if (askRequest.value === request && it.currentProjectId == snapshot.currentProjectId && it.range == snapshot.range && it.analyticsRevision == snapshot.analyticsRevision && it.token == snapshot.token && aiSelectionContext(it) == aiSelectionContext(snapshot) && it.ai.available && it.askDrawer.loading) it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Answer(answer.answer, answer.sources.joinToString(" · ")))) else it }
                loadAiCapabilities(snapshot)
            } catch (e: Throwable) {
                if (e is ApiException && e.code in setOf("ai_provider_failed", "ai_operation_expired", "ai_operation_conflict")) aiRetryRequest = null
                loadAiCapabilities(snapshot)
                _state.update { if (askRequest.value === request && it.currentProjectId == snapshot.currentProjectId && it.range == snapshot.range && it.analyticsRevision == snapshot.analyticsRevision && it.token == snapshot.token && aiSelectionContext(it) == aiSelectionContext(snapshot) && it.askDrawer.loading) it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Fail("", Res.string.ask_error_fetch))) else it }
            }
        }
    }

    /** Retries the latest failed question with the current project and range. */
    fun retryAskQuestion() {
        val state = _state.value.askDrawer
        val question = state.retryQuestion ?: return
        if (state.loading || !_state.value.ai.available) return
        _state.update { it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Retry)) }
        val snapshot = _state.value
        val request = Any()
        askRequest.value = request
        scope.launch {
            val projectId = snapshot.currentProjectId ?: run {
                _state.update { it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Fail("", Res.string.ask_error_project))) }
                return@launch
            }
            try {
                val submission = aiRetryRequest?.takeIf { it.question == question && it.range == snapshot.range.token && it.anchor == snapshot.analyticsAnchor && it.compare == snapshot.compare && it.context == aiSelectionContext(snapshot) } ?: aiRequest(snapshot, question)
                aiRetryRequest = submission
                val answer = api.aiAsk(projectId, submission)
                _state.update { if (askRequest.value === request && it.currentProjectId == snapshot.currentProjectId && it.range == snapshot.range && it.analyticsRevision == snapshot.analyticsRevision && it.token == snapshot.token && aiSelectionContext(it) == aiSelectionContext(snapshot) && it.ai.available && it.askDrawer.loading) it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Answer(answer.answer, answer.sources.joinToString(" · ")))) else it }
                loadAiCapabilities(snapshot)
            } catch (e: Throwable) {
                if (e is ApiException && e.code in setOf("ai_provider_failed", "ai_operation_expired", "ai_operation_conflict")) aiRetryRequest = null
                loadAiCapabilities(snapshot)
                _state.update { if (askRequest.value === request && it.currentProjectId == snapshot.currentProjectId && it.range == snapshot.range && it.analyticsRevision == snapshot.analyticsRevision && it.token == snapshot.token && aiSelectionContext(it) == aiSelectionContext(snapshot) && it.askDrawer.loading) it.copy(askDrawer = reduceAskDrawer(it.askDrawer, AskDrawerEvent.Fail("", Res.string.ask_error_fetch))) else it }
            }
        }
    }

    // ---- Toasts ----

    /** The in-flight auto-dismiss timer for the currently-shown toast, if any. Cancelled whenever
     *  that toast is replaced or manually closed, so a stale timer can never dismiss a different
     *  toast than the one it was scheduled for. `@Volatile` because [scope] (in production)
     *  dispatches on [Dispatchers.Default], a real thread pool: `showToast` can run on one thread
     *  while a previous toast's auto-dismiss coroutine body resumes on another, so this field is
     *  read and written across threads without any other synchronization. */
    @Volatile
    private var toastDismissJob: Job? = null

    /**
     * Shows [toast] bottom-center, replacing whatever toast is currently showing, and schedules
     * it to auto-dismiss after ~5s (design_handoff_dashboard_redesign/README.md > Interactions &
     * Behavior > "Toasts"). Cancels any pending auto-dismiss timer from a previous toast first —
     * otherwise that stale timer would later fire and incorrectly dismiss this new one.
     *
     * `cancel()` alone is not enough to guarantee that, though: it is cooperative and only takes
     * effect at the cancelled coroutine's next suspension point, so if the previous timer's
     * `delay(5_000)` had *already* resumed and its body was already running (on another thread,
     * under the real multi-threaded [Dispatchers.Default] scope) at the moment this function is
     * called, `cancel()` cannot stop that in-flight `_state.update` — the two updates could then
     * land in either order, and a stale clear landing after this toast is published would wipe it
     * out a moment after it appeared. The `toastDismissJob === job` identity check inside the
     * launched block closes that gap: even if the stale body still runs to completion, it is now
     * a no-op once [toastDismissJob] no longer points at it (because `showToast` or [dismissToast]
     * already moved it on to something else) — see [clearingToastIfStillCurrent].
     */
    fun showToast(toast: Toast) {
        toastDismissJob?.cancel()
        _state.update { it.copy(toast = toast) }
        lateinit var job: Job
        job = scope.launch {
            delay(5_000)
            _state.update { it.clearingToastIfStillCurrent(scheduledJob = job, currentJob = toastDismissJob) }
        }
        toastDismissJob = job
    }

    /** Dismisses the current toast immediately (the ✕ close button) and cancels its pending
     *  auto-dismiss timer so it can't later re-fire against whatever toast shows next. */
    fun dismissToast() {
        toastDismissJob?.cancel()
        toastDismissJob = null
        _state.update { it.copy(toast = null) }
    }

    // ---- "Worth a look" dismiss/snooze (Task 21) ----

    /** Saves project-local choices; a snooze expires at the next UTC midnight. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun hideWorthALook(id: String, snooze: Boolean) {
        val project = _state.value.currentProjectId
        val old = observationPreferences.firstOrNull { it.project == project && it.id == id }
        if (project != null) {
            observationPreferences = observationPreferences.filterNot { it.project == project && it.id == id } +
                ObservationPreference(project, id, analyticsAnchorToday(), snooze)
            saveObservationPreferences()
        } else _state.update { it.copy(dismissedWorthALookIds = it.dismissedWorthALookIds + id) }
        showToast(Toast(
            emoji = if (snooze) "💤" else "👋",
            title = if (snooze) "Snoozed until tomorrow." else "Hidden.",
            body = if (snooze) "It comes back if it's still true." else "Hidden for this project.",
            actionLabel = "Undo",
            action = {
                observationPreferences = observationPreferences.filterNot { it.project == project && it.id == id } + listOfNotNull(old)
                saveObservationPreferences()
                if (project == null) _state.update { it.copy(dismissedWorthALookIds = it.dismissedWorthALookIds - id) }
                dismissToast()
            },
        ))
    }
}
