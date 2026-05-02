package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ApiClient
import com.quietmetrix.dashboard.api.ApiException
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.api.CreateProjectResponse
import com.quietmetrix.dashboard.api.EventRow
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TransitionsResponse
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
import org.jetbrains.compose.resources.StringResource

enum class Tab { Overview, Events, Flow, Live, Projects, Settings }

data class DashboardState(
    val token: String? = null,
    val user: ApiUser? = null,
    val projects: List<ApiProject> = emptyList(),
    val currentProjectId: String? = null,
    val aggregates: AggregatesResponse? = null,
    val recentEvents: List<EventRow> = emptyList(),
    val windowDays: Int = 30,
    val loading: Boolean = false,
    val error: String? = null,
    val loginErrorRes: StringResource? = null,
    val serverDebug: Boolean = false,
    val demoMode: Boolean = false,
    val activeTab: Tab = Tab.Overview,
    val newProjectKeys: CreateProjectResponse? = null,
    val showInfo: Tab? = null,
    val transitions: TransitionsResponse? = null,
    val sessions: SessionsResponse? = null,
    val liveEvents: List<EventRow> = emptyList(),
)

class DashboardViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private lateinit var api: ApiClient

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
            loadProjects()
        } catch (e: ApiException) {
            _state.update { it.copy(loading = false, loginErrorRes = mapLoginError(e)) }
        } catch (_: Throwable) {
            // Network failure, JSON parse error, host unreachable, CORS, etc.
            _state.update { it.copy(loading = false, loginErrorRes = Res.string.login_error_network) }
        }
    }

    private fun mapLoginError(e: ApiException): StringResource = when {
        e.status == 401 -> Res.string.login_error_invalid_credentials
        e.code == "misconfigured" -> Res.string.login_error_misconfigured
        e.status in 500..599 -> Res.string.login_error_server
        else -> Res.string.login_error_unknown
    }

    fun logout() {
        api.token = null
        api.refreshToken = null
        TokenStorage.clear()
        TokenStorage.clearRefresh()
        _state.value = DashboardState(serverDebug = _state.value.serverDebug)
    }

    fun restoreSession(token: String) {
        api.token = token
        api.refreshToken = TokenStorage.getRefresh()
        _state.update { it.copy(token = token, loading = true) }
        scope.launch {
            try {
                val res = api.listProjects()
                val currentId = res.projects.firstOrNull()?.id
                _state.update { it.copy(projects = res.projects, currentProjectId = currentId, loading = false) }
            } catch (e: ApiException) {
                if (e.status == 401) {
                    if (api.refresh()) {
                        TokenStorage.save(api.token!!)
                        TokenStorage.saveRefresh(api.refreshToken!!)
                        // Retry with new token
                        try {
                            val res = api.listProjects()
                            val currentId = res.projects.firstOrNull()?.id
                            _state.update { it.copy(projects = res.projects, currentProjectId = currentId, loading = false) }
                        } catch (_: Throwable) {
                            _state.update { it.copy(error = "Failed to load projects after token refresh", loading = false) }
                        }
                        return@launch
                    }
                    // Refresh failed — force logout
                    api.token = null
                    api.refreshToken = null
                    TokenStorage.clear()
                    TokenStorage.clearRefresh()
                    _state.update { it.copy(token = null, loading = false) }
                    return@launch
                }
                _state.update { it.copy(error = e.message, loading = false) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message, loading = false) }
            }
        }
    }

    fun selectTab(tab: Tab) {
        _state.update { it.copy(activeTab = tab) }
        when (tab) {
            Tab.Overview -> scope.launch { loadAggregates(); loadSessions() }
            Tab.Events   -> scope.launch { loadEvents() }
            Tab.Flow     -> scope.launch { loadTransitions() }
            Tab.Live     -> scope.launch { loadLiveEvents() }
            Tab.Projects -> scope.launch { loadProjects() }
            Tab.Settings -> { /* no fetch */ }
        }
    }

    fun selectProject(projectId: String) {
        _state.update { it.copy(currentProjectId = projectId) }
        scope.launch { loadAggregates(); loadEvents(); loadTransitions(); loadSessions() }
    }

    fun selectWindow(days: Int) {
        _state.update { it.copy(windowDays = days) }
        scope.launch { loadAggregates() }
    }

    fun setDemoMode(enabled: Boolean) {
        // Production guarantee: the toggle only takes effect when the server
        // reports debug=true. Production deployments cannot enable demo data.
        if (!_state.value.serverDebug) return
        _state.update { it.copy(demoMode = enabled) }
        scope.launch { loadAggregates(); loadEvents() }
    }

    fun createProject(name: String) {
        scope.launch {
            try {
                val out = api.createProject(name)
                _state.update { it.copy(newProjectKeys = out) }
                loadProjects()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteProject(projectId: String) {
        scope.launch {
            try {
                api.deleteProject(projectId)
                loadProjects()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissNewProjectKeys() {
        _state.update { it.copy(newProjectKeys = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun showTabInfo(tab: Tab) {
        _state.update { it.copy(showInfo = tab) }
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
            _state.update { it.copy(error = e.message) }
        }
    }

    private suspend fun loadAggregates() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val agg = api.aggregates(
                projectId = s.currentProjectId.orEmpty(),
                days = s.windowDays,
                demo = s.demoMode,
            )
            _state.update { it.copy(aggregates = agg) }
        } catch (e: Throwable) {
            _state.update { it.copy(error = e.message) }
        }
    }

    private suspend fun loadEvents() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.events(s.currentProjectId.orEmpty(), s.demoMode)
            _state.update { it.copy(recentEvents = res.events) }
        } catch (e: Throwable) {
            _state.update { it.copy(error = e.message) }
        }
    }

    private suspend fun loadTransitions() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.transitions(s.currentProjectId.orEmpty(), s.windowDays)
            _state.update { it.copy(transitions = res) }
        } catch (e: Throwable) {
            _state.update { it.copy(error = e.message) }
        }
    }

    private suspend fun loadSessions() {
        val s = _state.value
        if (!s.demoMode && s.currentProjectId == null) return
        try {
            val res = api.sessions(s.currentProjectId.orEmpty(), s.windowDays)
            _state.update { it.copy(sessions = res) }
        } catch (e: Throwable) {
            _state.update { it.copy(error = e.message) }
        }
    }

    fun loadLiveEvents() {
        scope.launch {
            val s = _state.value
            if (!s.demoMode && s.currentProjectId == null) return@launch
            try {
                val res = api.events(s.currentProjectId.orEmpty(), s.demoMode)
                _state.update { it.copy(liveEvents = res.events) }
            } catch (_: Throwable) {
                // Live feed failures are silent — don't spam error banner
            }
        }
    }
}
