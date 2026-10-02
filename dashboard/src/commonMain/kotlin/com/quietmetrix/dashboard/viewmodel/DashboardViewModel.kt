package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AccessTokenDto
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ApiClient
import com.quietmetrix.dashboard.api.ApiException
import com.quietmetrix.dashboard.api.ApiManagedUser
import com.quietmetrix.dashboard.api.ApiMember
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.api.CreateExperimentRequest
import com.quietmetrix.dashboard.api.CreateProjectResponse
import com.quietmetrix.dashboard.api.ErrorKind
import com.quietmetrix.dashboard.api.ExperimentDto
import com.quietmetrix.dashboard.api.ExperimentResultsResponse
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FrictionResponse
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.RegenerateKeyResponse
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
import com.quietmetrix.dashboard.resources.login_error_invalid_credentials
import com.quietmetrix.dashboard.resources.login_error_misconfigured
import com.quietmetrix.dashboard.resources.login_error_network
import com.quietmetrix.dashboard.resources.login_error_server
import com.quietmetrix.dashboard.resources.login_error_unknown
import com.quietmetrix.dashboard.storage.TokenStorage
import com.quietmetrix.dashboard.ui.screens.guideStepAfter
import com.quietmetrix.dashboard.ui.screens.guideStepBefore
import com.quietmetrix.dashboard.ui.screens.shouldAutoOpenGuide
import com.quietmetrix.dashboard.ui.screens.EXPERIMENTS_GUIDE_STEP_COUNT
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

data class DashboardState(
    val token: String? = null,
    val user: ApiUser? = null,
    val projects: List<ApiProject> = emptyList(),
    val currentProjectId: String? = null,
    val aggregates: AggregatesResponse? = null,
    val range: TimeRange = TimeRange.Default,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** User-initiated-action failures only (create project, invite, regenerate key, ...). */
    val error: UiError? = null,
    /** Background-loader failures, keyed by section, so one bad panel doesn't blank the rest. */
    val sectionErrors: Map<DataSection, UiError> = emptyMap(),
    val loginErrorRes: StringResource? = null,
    val serverDebug: Boolean = false,
    val demoMode: Boolean = false,
    val activeDestination: NavDestination = NavDestination.Overview,
    val newProjectKeys: CreateProjectResponse? = null,
    val showInfo: NavDestination? = null,
    val transitions: TransitionsResponse? = null,
    val sessions: SessionsResponse? = null,
    val retention: RetentionResponse? = null,
    val search: SearchResponse? = null,
    val friction: FrictionResponse? = null,
    val funnels: List<FunnelDto> = emptyList(),
    val selectedFunnelKey: String? = null,
    val funnelResults: FunnelResultsResponse? = null,
    // A/B testing experiments
    val experiments: List<ExperimentDto> = emptyList(),
    /** Null when the project's plan has no cap (paid plan, or self-host). */
    val experimentLimit: Int? = null,
    val selectedExperimentKey: String? = null,
    val experimentResults: ExperimentResultsResponse? = null,
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

/** One background data panel that can fail independently without blanking the dashboard. */
enum class DataSection { Aggregates, Transitions, Sessions, Retention, Search, Friction, Funnels, FunnelResults, Experiments, ExperimentResults }

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

    /** Persists the signed-in user so role/email survive a page reload. */
    private fun persistUser(user: ApiUser) {
        runCatching { TokenStorage.saveUser(json.encodeToString(ApiUser.serializer(), user)) }
    }

    suspend fun init(apiBaseUrl: String) {
        api = ApiClient(apiBaseUrl)
        try {
            val meta = api.meta()
            _state.update { it.copy(serverDebug = meta.debug) }
        } catch (_: Throwable) {
            _state.update { it.copy(serverDebug = false) }
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
        api.token = null
        api.refreshToken = null
        TokenStorage.clear()
        TokenStorage.clearRefresh()
        TokenStorage.clearLastProjectId()
        TokenStorage.clearUser()
        _state.value = DashboardState(serverDebug = _state.value.serverDebug)
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

    /** Re-run the active screen's loaders, blanking it to a spinner while in flight. */
    fun refresh() {
        val destination = _state.value.activeDestination
        _state.update { it.copy(refreshing = true) }
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
            NavDestination.Flow      -> { loadTransitions(); loadSearch(); loadFriction() }
            NavDestination.Retention -> loadRetentionOnce()
            NavDestination.Projects  -> loadProjects()
            NavDestination.Users     -> loadUsersOnce()
            NavDestination.Funnels   -> loadFunnelsOnce()
            NavDestination.Experiments -> {
                loadExperimentsOnce()
                if (shouldAutoOpenGuide(TokenStorage.isExperimentsGuideSeen())) {
                    _state.update { it.copy(experimentsGuideOpen = true, experimentsGuideStep = 0) }
                }
            }
            NavDestination.Settings  -> { /* no fetch */ }
        }
    }

    fun selectProject(projectId: String) {
        _state.update {
            it.copy(
                currentProjectId = projectId, funnels = emptyList(), selectedFunnelKey = null, funnelResults = null,
                experiments = emptyList(), experimentLimit = null, selectedExperimentKey = null, experimentResults = null,
            )
        }
        TokenStorage.saveLastProjectId(projectId)
        scope.launch { loadAggregates(); loadTransitions(); loadSearch(); loadFriction(); loadSessions(); loadFunnelsOnce(); loadExperimentsOnce() }
    }

    fun selectRange(range: TimeRange) {
        _state.update { it.copy(range = range) }
        scope.launch { loadAggregates(); loadSessions(); loadFunnelResultsOnce(); loadExperimentResultsOnce() }
    }

    fun setDemoMode(enabled: Boolean) {
        if (!_state.value.serverDebug) return
        _state.update { it.copy(demoMode = enabled) }
        scope.launch { loadAggregates() }
    }

    fun createProject(name: String, description: String? = null) {
        scope.launch {
            try {
                val out = api.createProject(name, description)
                _state.update { it.copy(newProjectKeys = out) }
                loadProjects()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("POST /projects")) }
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
        try {
            _state.update { it.copy(users = api.listUsers().users) }
        } catch (e: Throwable) {
            _state.update { it.copy(error = e.toUiError("GET /users")) }
        }
    }

    fun inviteUser(email: String, role: String) {
        scope.launch {
            try {
                val res = api.inviteUser(email, role)
                _state.update { it.copy(lastInviteLink = res.inviteLink) }
                loadUsers()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("POST /users/invite")) }
            }
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

    fun loadTokens() {
        scope.launch {
            try {
                _state.update { it.copy(accessTokens = api.listTokens().tokens) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("GET /tokens")) }
            }
        }
    }

    fun createToken(name: String, scopes: List<String>? = null) {
        scope.launch {
            try {
                val res = api.createToken(name, scopes)
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
        try {
            val res = api.listFunnels(projectId)
            val currentKey = s.selectedFunnelKey?.takeIf { key -> res.funnels.any { it.funnelKey == key } }
                ?: res.funnels.firstOrNull()?.funnelKey
            _state.update {
                it.copy(funnels = res.funnels, selectedFunnelKey = currentKey)
                    .clearingSectionError(DataSection.Funnels)
            }
            loadFunnelResultsOnce()
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Funnels, e.toUiError("GET /projects/{id}/funnels")) }
        }
    }

    private suspend fun loadFunnelResultsOnce() {
        val s = _state.value
        val projectId = s.currentProjectId ?: return
        val funnelKey = s.selectedFunnelKey ?: run {
            _state.update { it.copy(funnelResults = null).clearingSectionError(DataSection.FunnelResults) }
            return
        }
        try {
            val res = api.funnelResults(projectId, funnelKey, s.range)
            _state.update { it.copy(funnelResults = res).clearingSectionError(DataSection.FunnelResults) }
        } catch (e: Throwable) {
            _state.update {
                it.withSectionError(DataSection.FunnelResults, e.toUiError("GET /projects/{id}/funnels/{key}/results"))
            }
        }
    }

    fun selectFunnel(funnelKey: String) {
        _state.update { it.copy(selectedFunnelKey = funnelKey) }
        scope.launch { loadFunnelResultsOnce() }
    }

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

    // ---- Experiments (A/B testing) ----

    private suspend fun loadExperimentsOnce() {
        val s = _state.value
        val projectId = s.currentProjectId ?: return
        try {
            val res = api.listExperiments(projectId)
            val currentKey = s.selectedExperimentKey?.takeIf { key -> res.experiments.any { it.key == key } }
                ?: res.experiments.firstOrNull()?.key
            _state.update {
                it.copy(experiments = res.experiments, experimentLimit = res.limit, selectedExperimentKey = currentKey)
                    .clearingSectionError(DataSection.Experiments)
            }
            loadExperimentResultsOnce()
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Experiments, e.toUiError("GET /projects/{id}/experiments")) }
        }
    }

    private suspend fun loadExperimentResultsOnce() {
        val s = _state.value
        val projectId = s.currentProjectId ?: return
        val key = s.selectedExperimentKey ?: run {
            _state.update { it.copy(experimentResults = null).clearingSectionError(DataSection.ExperimentResults) }
            return
        }
        try {
            val res = api.experimentResults(projectId, key, s.range)
            _state.update { it.copy(experimentResults = res).clearingSectionError(DataSection.ExperimentResults) }
        } catch (e: Throwable) {
            _state.update {
                it.withSectionError(DataSection.ExperimentResults, e.toUiError("GET /projects/{id}/experiments/{key}/results"))
            }
        }
    }

    fun selectExperiment(experimentKey: String) {
        _state.update { it.copy(selectedExperimentKey = experimentKey) }
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

    /** Convenience for the status-only transitions the list screen's action buttons trigger
     *  (Start/Pause/Resume/End) — see ExperimentsLogic.availableActions(). */
    fun changeExperimentStatus(experimentKey: String, status: String) {
        updateExperiment(experimentKey, UpdateExperimentRequest(status = status))
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

    /** Closes the guide, whether finished or skipped, and marks it seen so it won't auto-open again. */
    fun closeExperimentsGuide() {
        TokenStorage.saveExperimentsGuideSeen()
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
        try {
            val res = api.listProjects()
            val current = _state.value.currentProjectId
                ?: res.projects.firstOrNull()?.id
            _state.update { it.copy(projects = res.projects, currentProjectId = current) }
        } catch (e: Throwable) {
            _state.update { it.copy(error = e.toUiError("GET /projects")) }
        }
    }

    private suspend fun loadAggregates() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val agg = api.aggregates(
                projectId = s.currentProjectId.orEmpty(),
                range = s.range,
                demo = s.demoMode,
            )
            _state.update { it.copy(aggregates = agg).clearingSectionError(DataSection.Aggregates) }
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Aggregates, e.toUiError("GET /projects/{id}/aggregates")) }
        }
    }

    private suspend fun loadTransitions() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.transitions(s.currentProjectId.orEmpty(), s.range)
            _state.update { it.copy(transitions = res).clearingSectionError(DataSection.Transitions) }
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Transitions, e.toUiError("GET /projects/{id}/transitions")) }
        }
    }

    private suspend fun loadSearch() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.search(s.currentProjectId.orEmpty(), (s.range.seconds / 86_400).toInt().coerceAtLeast(1))
            _state.update { it.copy(search = res).clearingSectionError(DataSection.Search) }
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Search, e.toUiError("GET /projects/{id}/search")) }
        }
    }

    private suspend fun loadFriction() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.friction(s.currentProjectId.orEmpty(), (s.range.seconds / 86_400).toInt().coerceAtLeast(1))
            _state.update { it.copy(friction = res).clearingSectionError(DataSection.Friction) }
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Friction, e.toUiError("GET /projects/{id}/friction")) }
        }
    }

    private suspend fun loadSessions() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.sessions(s.currentProjectId.orEmpty(), s.range)
            _state.update { it.copy(sessions = res).clearingSectionError(DataSection.Sessions) }
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Sessions, e.toUiError("GET /projects/{id}/sessions")) }
        }
    }

    private suspend fun loadRetentionOnce() {
        val s = _state.value
        if (s.currentProjectId == null) return
        try {
            val res = api.retention(s.currentProjectId, (s.range.seconds / 86_400).toInt().coerceAtLeast(1))
            _state.update { it.copy(retention = res).clearingSectionError(DataSection.Retention) }
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Retention, e.toUiError("GET /projects/{id}/retention")) }
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
}
