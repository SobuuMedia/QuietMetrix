package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ApiClient
import com.quietmetrix.dashboard.api.ApiException
import com.quietmetrix.dashboard.api.ApiManagedUser
import com.quietmetrix.dashboard.api.ApiMember
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.api.CreateFunnelRequest
import com.quietmetrix.dashboard.api.CreateProjectResponse
import com.quietmetrix.dashboard.api.EventRow
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.FunnelStepDto
import com.quietmetrix.dashboard.api.RegenerateKeyResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.TransitionsResponse
import com.quietmetrix.dashboard.api.UiError
import com.quietmetrix.dashboard.api.UpdateFunnelRequest
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource

data class DashboardState(
    val token: String? = null,
    val user: ApiUser? = null,
    val projects: List<ApiProject> = emptyList(),
    val currentProjectId: String? = null,
    val aggregates: AggregatesResponse? = null,
    val recentEvents: List<EventRow> = emptyList(),
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
    val funnels: List<FunnelDto> = emptyList(),
    val selectedFunnelKey: String? = null,
    val funnelResults: FunnelResultsResponse? = null,
    /** null | "country" | "platform" | "device_class" | "language". */
    val funnelBreakdownDimension: String? = null,
    val liveEvents: List<EventRow> = emptyList(),
    /** The event whose detail panel is open on the Events screen, if any. */
    val selectedEvent: EventRow? = null,
    // User management + API-key UX
    val users: List<ApiManagedUser> = emptyList(),
    val members: Map<String, List<ApiMember>> = emptyMap(),
    val regeneratedKey: RegenerateKeyResponse? = null,
    val lastInviteLink: String? = null,
    // Invitation acceptance flow
    val inviteEmail: String? = null,
    val inviteTokenInvalid: Boolean = false,
    val inviteSubmitting: Boolean = false,
) {
    /** Global role of the signed-in user. */
    val role: String? get() = user?.role
    val isAdmin: Boolean get() = role == UserRole.ADMIN
    /** Admins and developers may regenerate API keys; reviewers may not. */
    val canRegenerate: Boolean get() = role == UserRole.ADMIN || role == UserRole.DEVELOPER
    /** Reviewers see everything read-only. */
    val isReadOnly: Boolean get() = role == UserRole.REVIEWER
}

/** One background data panel that can fail independently without blanking the dashboard. */
enum class DataSection { Aggregates, Events, Transitions, Sessions, Funnels, FunnelResults }

/** Records [error] for [section] without touching the global banner or any other section. */
internal fun DashboardState.withSectionError(section: DataSection, error: UiError): DashboardState =
    copy(sectionErrors = sectionErrors + (section to error))

/** Clears a previously-recorded section error, e.g. after that section reloads successfully. */
internal fun DashboardState.clearingSectionError(section: DataSection): DashboardState =
    copy(sectionErrors = sectionErrors - section)

class DashboardViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
                    loadEvents()
                    loadTransitions()
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
                                loadEvents()
                                loadTransitions()
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
            NavDestination.Overview -> { loadAggregates(); loadSessions() }
            NavDestination.Events   -> loadEvents()
            NavDestination.Flow     -> loadTransitions()
            NavDestination.Live     -> loadLiveEventsOnce()
            NavDestination.Projects -> loadProjects()
            NavDestination.Users    -> loadUsersOnce()
            NavDestination.Funnels  -> loadFunnelsOnce()
            NavDestination.Settings -> { /* no fetch */ }
        }
    }

    fun selectProject(projectId: String) {
        _state.update { it.copy(currentProjectId = projectId, funnels = emptyList(), selectedFunnelKey = null, funnelResults = null) }
        TokenStorage.saveLastProjectId(projectId)
        scope.launch { loadAggregates(); loadEvents(); loadTransitions(); loadSessions(); loadFunnelsOnce() }
    }

    fun selectRange(range: TimeRange) {
        _state.update { it.copy(range = range) }
        scope.launch { loadAggregates(); loadSessions(); loadFunnelResultsOnce() }
    }

    fun setDemoMode(enabled: Boolean) {
        if (!_state.value.serverDebug) return
        _state.update { it.copy(demoMode = enabled) }
        scope.launch { loadAggregates(); loadEvents() }
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
            val res = api.funnelResults(projectId, funnelKey, s.range, s.funnelBreakdownDimension, trend = true)
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

    fun selectFunnelBreakdown(dimension: String?) {
        _state.update { it.copy(funnelBreakdownDimension = dimension) }
        scope.launch { loadFunnelResultsOnce() }
    }

    fun createFunnel(funnelKey: String, name: String, steps: List<FunnelStepDto>, windowSeconds: Long) {
        val projectId = _state.value.currentProjectId ?: return
        scope.launch {
            try {
                val created = api.createFunnel(projectId, CreateFunnelRequest(funnelKey, name, steps = steps, windowSeconds = windowSeconds))
                _state.update { it.copy(selectedFunnelKey = created.funnelKey) }
                loadFunnelsOnce()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("POST /projects/{id}/funnels")) }
            }
        }
    }

    fun updateFunnel(funnelKey: String, name: String, steps: List<FunnelStepDto>, windowSeconds: Long) {
        val projectId = _state.value.currentProjectId ?: return
        scope.launch {
            try {
                api.updateFunnel(projectId, funnelKey, UpdateFunnelRequest(name = name, steps = steps, windowSeconds = windowSeconds))
                loadFunnelsOnce()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.toUiError("PATCH /projects/{id}/funnels/{key}")) }
            }
        }
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

    private suspend fun loadEvents() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.events(s.currentProjectId.orEmpty(), s.demoMode)
            _state.update { it.copy(recentEvents = res.events).clearingSectionError(DataSection.Events) }
        } catch (e: Throwable) {
            _state.update { it.withSectionError(DataSection.Events, e.toUiError("GET /projects/{id}/events")) }
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

    /** Opens the detail panel for [event] on the Events screen. */
    fun selectEvent(event: EventRow) {
        _state.update { it.copy(selectedEvent = event) }
    }

    /** Closes the event detail panel. */
    fun clearSelectedEvent() {
        _state.update { it.copy(selectedEvent = null) }
    }

    fun loadLiveEvents() {
        scope.launch { loadLiveEventsOnce() }
    }

    private suspend fun loadLiveEventsOnce() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.events(s.currentProjectId.orEmpty(), s.demoMode)
            _state.update { it.copy(liveEvents = res.events) }
        } catch (_: Throwable) {
        }
    }
}
