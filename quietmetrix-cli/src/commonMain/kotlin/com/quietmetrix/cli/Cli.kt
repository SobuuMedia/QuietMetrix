package com.quietmetrix.cli

import com.quietmetrix.cli.http.HttpClient
import kotlinx.serialization.json.Json

/** Exit codes the process should return — an agent scripting against the CLI keys off these. */
object ExitCode {
    const val OK = 0
    const val USAGE = 1
    const val AUTH = 2
    const val SERVER = 3
}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * The argv-driven front end over [QuietMetrixClient] — the machinery behind
 * `docs/agents/setup.md`'s "create a QuietMetrix project and wire it into this app" flow.
 * All network access goes through [http] (via [QuietMetrixClient]), so this class is fully
 * testable with a fake and no real HTTP calls. The MCP server (quietmetrix-mcp) calls the
 * same [QuietMetrixClient] directly, so the two entry points never drift on request/response
 * handling — only on how the result is presented.
 */
class QuietMetrixCli(http: HttpClient) {
    private val client = QuietMetrixClient(http)

    /**
     * Runs one CLI invocation. [args] excludes the interpreter/script argv Node prepends.
     * Writes to [out]/[err] rather than stdout/stderr directly so tests can capture output.
     * Returns the process exit code — see [ExitCode].
     */
    suspend fun run(
        args: List<String>,
        env: Map<String, String>,
        out: (String) -> Unit,
        err: (String) -> Unit,
    ): Int {
        return when (val command = parseArgs(args, env)) {
            is ParsedCommand.Help -> {
                out(HELP_TEXT)
                ExitCode.OK
            }
            is ParsedCommand.UsageError -> {
                err("error: ${command.message}\n\n$HELP_TEXT")
                ExitCode.USAGE
            }
            is ParsedCommand.Whoami -> whoami(command, out, err)
            is ParsedCommand.ProjectCreate -> projectCreate(command, env, out, err)
            is ParsedCommand.ProjectList -> projectList(command, env, out, err)
            is ParsedCommand.AnalyticsAggregates -> withToken(env, out, err) { token ->
                client.getAggregates(command.url, token, command.projectId, command.range)
            }
            is ParsedCommand.AnalyticsTransitions -> withToken(env, out, err) { token ->
                client.getTransitions(command.url, token, command.projectId, command.range)
            }
            is ParsedCommand.AnalyticsSessions -> withToken(env, out, err) { token ->
                client.getSessions(command.url, token, command.projectId, command.range)
            }
            is ParsedCommand.AnalyticsRetention -> withToken(env, out, err) { token ->
                client.getRetention(command.url, token, command.projectId, command.range)
            }
            is ParsedCommand.FunnelList -> withToken(env, out, err) { token ->
                client.listFunnels(command.url, token, command.projectId)
            }
            is ParsedCommand.FunnelResults -> withToken(env, out, err) { token ->
                client.getFunnelResults(command.url, token, command.projectId, command.funnelKey, command.range)
            }
        }
    }

    /**
     * Shared shape for every read-only analytics/funnel command: require a token, run
     * [fetch], print the raw server JSON verbatim on success, map the outcome to an exit code.
     */
    private suspend fun withToken(
        env: Map<String, String>,
        out: (String) -> Unit,
        err: (String) -> Unit,
        fetch: suspend (token: String) -> Outcome<String>,
    ): Int {
        val token = requireToken(env, err) ?: return ExitCode.AUTH
        return when (val outcome = fetch(token)) {
            is Outcome.Success -> { out(outcome.value); ExitCode.OK }
            is Outcome.AuthError -> { err("error: ${outcome.message}"); ExitCode.AUTH }
            is Outcome.ServerError -> { err("error: ${outcome.message}"); ExitCode.SERVER }
        }
    }

    private fun requireToken(env: Map<String, String>, err: (String) -> Unit): String? {
        val token = env["QUIETMETRIX_TOKEN"]
        if (token.isNullOrBlank()) {
            err(
                "error: QUIETMETRIX_TOKEN is not set.\n" +
                    "Mint one at Dashboard > Access tokens, then:\n" +
                    "  export QUIETMETRIX_TOKEN=qm_pat_..."
            )
            return null
        }
        return token
    }

    private suspend fun whoami(command: ParsedCommand.Whoami, out: (String) -> Unit, err: (String) -> Unit): Int {
        return when (val outcome = client.whoami(command.url)) {
            is Outcome.Success -> { out(outcome.value); ExitCode.OK }
            is Outcome.AuthError -> { err("error: ${outcome.message}"); ExitCode.AUTH }
            is Outcome.ServerError -> { err("error: ${outcome.message}"); ExitCode.SERVER }
        }
    }

    private suspend fun projectCreate(
        command: ParsedCommand.ProjectCreate,
        env: Map<String, String>,
        out: (String) -> Unit,
        err: (String) -> Unit,
    ): Int {
        val token = requireToken(env, err) ?: return ExitCode.AUTH
        val outcome = client.createProject(command.url, token, command.name, command.description, command.idempotencyKey)
        return when (outcome) {
            is Outcome.Success -> {
                out(json.encodeToString(ProjectCreateOutput.serializer(), outcome.value))
                ExitCode.OK
            }
            is Outcome.AuthError -> { err("error: ${outcome.message}"); ExitCode.AUTH }
            is Outcome.ServerError -> { err("error: ${outcome.message}"); ExitCode.SERVER }
        }
    }

    private suspend fun projectList(
        command: ParsedCommand.ProjectList,
        env: Map<String, String>,
        out: (String) -> Unit,
        err: (String) -> Unit,
    ): Int {
        val token = requireToken(env, err) ?: return ExitCode.AUTH
        return when (val outcome = client.listProjects(command.url, token)) {
            is Outcome.Success -> { out(outcome.value); ExitCode.OK }
            is Outcome.AuthError -> { err("error: ${outcome.message}"); ExitCode.AUTH }
            is Outcome.ServerError -> { err("error: ${outcome.message}"); ExitCode.SERVER }
        }
    }
}
