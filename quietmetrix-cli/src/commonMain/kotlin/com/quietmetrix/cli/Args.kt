package com.quietmetrix.cli

const val HELP_TEXT = """quietmetrix <command> [options]

Commands:
  project create --name <name> [--url <url>] [--description <d>] [--idempotency-key <k>] [--json]
  project list   [--url <url>] [--json]
  whoami         [--url <url>]

  analytics aggregates  --project <id> [--url <url>] [--range <r>]
  analytics transitions --project <id> [--url <url>] [--range <r>]
  analytics sessions    --project <id> [--url <url>] [--range <r>]
  analytics retention   --project <id> [--url <url>] [--range <r>]
  funnel list           --project <id> [--url <url>]
  funnel results        --project <id> --funnel <key> [--url <url>] [--range <r>]

Options:
  --url <url>              QuietMetrix server URL. Falls back to the QUIETMETRIX_URL env var.
  --project <id>           Project id, as returned by `project create`/`project list` (the
                           `proj_...` form).
  --range <r>              One of 1h, 1d, 7d, 30d, 90d — a trailing window ending now, in UTC.
                           Defaults to 7d ("this week"). There is no calendar-week or
                           per-user-timezone bucketing.
  --json                   Print machine-readable JSON to stdout (project create always does).
  --idempotency-key <key>  Safe to retry: the same key returns the earlier project instead of
                           minting a second one.

Requires an access token with the analytics:read scope for the analytics/funnel commands
(project:create/read are enough for the project/whoami commands).

Auth:
  Reads QUIETMETRIX_TOKEN from the environment — a personal access token minted at
  Dashboard > Access tokens. Never pass it on the command line (shows up in shell history
  and process listings).
"""

/** A fully-parsed, ready-to-run CLI invocation, or a reason it couldn't be parsed. */
sealed class ParsedCommand {
    data class ProjectCreate(
        val url: String,
        val name: String,
        val description: String?,
        val idempotencyKey: String?,
    ) : ParsedCommand()

    data class ProjectList(val url: String, val json: Boolean) : ParsedCommand()

    data class Whoami(val url: String) : ParsedCommand()

    data class AnalyticsAggregates(val url: String, val projectId: String, val range: String) : ParsedCommand()
    data class AnalyticsTransitions(val url: String, val projectId: String, val range: String) : ParsedCommand()
    data class AnalyticsSessions(val url: String, val projectId: String, val range: String) : ParsedCommand()
    data class AnalyticsRetention(val url: String, val projectId: String, val range: String) : ParsedCommand()
    data class FunnelList(val url: String, val projectId: String) : ParsedCommand()
    data class FunnelResults(val url: String, val projectId: String, val funnelKey: String, val range: String) : ParsedCommand()

    data object Help : ParsedCommand()

    data class UsageError(val message: String) : ParsedCommand()
}

private val VALUE_FLAGS = setOf("url", "name", "description", "idempotency-key", "project", "range", "funnel")

/**
 * Parses CLI arguments (excluding the interpreter/script argv[0]/argv[1] Node prepends)
 * into a [ParsedCommand]. [env] supplies the QUIETMETRIX_URL fallback for `--url`.
 */
fun parseArgs(args: List<String>, env: Map<String, String>): ParsedCommand {
    if (args.isEmpty() || args[0] == "-h" || args[0] == "--help" || args[0] == "help") {
        return ParsedCommand.Help
    }

    val (command, rest) = when {
        args[0] == "project" && args.size > 1 && (args[1] == "create" || args[1] == "list") ->
            "project ${args[1]}" to args.drop(2)
        args[0] == "whoami" -> "whoami" to args.drop(1)
        args[0] == "analytics" && args.size > 1 && args[1] in setOf("aggregates", "transitions", "sessions", "retention") ->
            "analytics ${args[1]}" to args.drop(2)
        args[0] == "funnel" && args.size > 1 && (args[1] == "list" || args[1] == "results") ->
            "funnel ${args[1]}" to args.drop(2)
        else -> return ParsedCommand.UsageError("Unknown command: ${args[0]}")
    }

    val flags = mutableMapOf<String, String>()
    val boolFlags = mutableSetOf<String>()
    var i = 0
    while (i < rest.size) {
        val token = rest[i]
        if (!token.startsWith("--")) return ParsedCommand.UsageError("Unexpected argument: $token")
        val key = token.removePrefix("--")
        if (key == "json") {
            boolFlags += key
            i++
            continue
        }
        if (key !in VALUE_FLAGS) return ParsedCommand.UsageError("Unknown option: $token")
        val value = rest.getOrNull(i + 1) ?: return ParsedCommand.UsageError("Missing value for $token")
        flags[key] = value
        i += 2
    }

    val url = flags["url"] ?: env["QUIETMETRIX_URL"]
    if (url.isNullOrBlank()) {
        return ParsedCommand.UsageError("Missing --url (or set QUIETMETRIX_URL)")
    }
    val normalizedUrl = normalizeBaseUrl(url)

    return when (command) {
        "project create" -> {
            val name = flags["name"] ?: return ParsedCommand.UsageError("Missing --name")
            if (name.isBlank()) return ParsedCommand.UsageError("--name must not be blank")
            ParsedCommand.ProjectCreate(normalizedUrl, name, flags["description"], flags["idempotency-key"])
        }
        "project list" -> ParsedCommand.ProjectList(normalizedUrl, "json" in boolFlags)
        "whoami" -> ParsedCommand.Whoami(normalizedUrl)
        "analytics aggregates", "analytics transitions", "analytics sessions", "analytics retention",
        "funnel list", "funnel results" -> {
            val projectId = flags["project"] ?: return ParsedCommand.UsageError("Missing --project")
            if (projectId.isBlank()) return ParsedCommand.UsageError("--project must not be blank")
            val range = flags["range"] ?: RangeToken.DEFAULT
            if (!RangeToken.isValid(range)) {
                return ParsedCommand.UsageError("Invalid --range '$range' — expected one of ${RangeToken.VALID.sorted().joinToString(", ")}")
            }
            when (command) {
                "analytics aggregates" -> ParsedCommand.AnalyticsAggregates(normalizedUrl, projectId, range)
                "analytics transitions" -> ParsedCommand.AnalyticsTransitions(normalizedUrl, projectId, range)
                "analytics sessions" -> ParsedCommand.AnalyticsSessions(normalizedUrl, projectId, range)
                "analytics retention" -> ParsedCommand.AnalyticsRetention(normalizedUrl, projectId, range)
                "funnel list" -> ParsedCommand.FunnelList(normalizedUrl, projectId)
                else -> {
                    val funnelKey = flags["funnel"] ?: return ParsedCommand.UsageError("Missing --funnel")
                    if (funnelKey.isBlank()) return ParsedCommand.UsageError("--funnel must not be blank")
                    ParsedCommand.FunnelResults(normalizedUrl, projectId, funnelKey, range)
                }
            }
        }
        else -> ParsedCommand.UsageError("Unknown command: $command")
    }
}
