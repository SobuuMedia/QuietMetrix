# Agent-driven setup

Wire QuietMetrix into an app by telling an AI coding agent (Claude Code, or any agent with a
shell or MCP support) to do it — no dashboard clicking, no copy-pasting a key between windows.

## What you say to the agent

Inside the app's own repository:

> Create a QuietMetrix project at `https://qm.example.com` and wire it into this app.

The agent creates the project, gets back a publishable API key, and writes the key plus the
tracking endpoint into the app's QuietMetrix config — installing the SDK first if it isn't
there yet. See [What the agent does](#what-the-agent-does) below for the exact steps, and
[Per-platform config](#per-platform-config) for where the values land on each platform.

## One-time setup

The agent needs a credential to create projects on your behalf. This is **not** the same as
the SDK's `qm_ak_…` API key — see [Two different credentials](#two-different-credentials)
below for why.

1. Log into the dashboard and go to **Settings → Access tokens**.
2. Click **New token**, name it (e.g. `laptop-agent`), and copy the `qm_pat_…` value shown —
   it is displayed exactly once.
3. Add it to your shell profile so every agent session can see it:

   ```bash
   export QUIETMETRIX_TOKEN=qm_pat_your_token_here
   ```

4. *(Optional, recommended for Claude Code and other MCP-aware agents)* Add the MCP server to
   the target project's `.mcp.json`:

   ```json
   {
     "mcpServers": {
       "quietmetrix": {
         "command": "npx",
         "args": ["-y", "@sobuumedia/quietmetrix-mcp"],
         "env": { "QUIETMETRIX_TOKEN": "qm_pat_your_token_here" }
       }
     }
   }
   ```

   Without this, an agent with shell access still works — it runs the CLI directly (see
   below) — the MCP entry just makes the tool discoverable without extra prompting.

## What the agent does

1. **Creates the project.** Either via the CLI:

   ```bash
   npx @sobuumedia/quietmetrix-cli project create \
     --url https://qm.example.com \
     --name "My App"
   ```

   or via the MCP tool `quietmetrix_create_project` (same underlying call). Both return JSON:

   ```json
   {
     "id": "proj_xyz789",
     "name": "My App",
     "api_key": "qm_ak_9f3c1a2b4e5d6f708192a3b4c5d6e7f8",
     "api_key_last4": "e7f8",
     "tracking_endpoint": "https://qm.example.com/api/v1",
     "message": "Project created. Store this key securely — it will not be shown again."
   }
   ```

2. **Detects whether the SDK is already installed** in the target app. If not, adds the
   dependency for the app's platform.

3. **Writes `trackingEndpoint` and `apiKey`** into the app's `QuietMetrix.init(...)` call,
   creating that call if none exists yet — see [Per-platform config](#per-platform-config) for
   where it goes on each platform.

4. Reports what it changed. Nothing further needed — no dashboard step, no manual key copy.

If the app already has a `QuietMetrix.init(...)` call, the agent only updates the two fields;
everything else you've configured (funnels, `flushIntervalMs`, consent defaults, …) is left
alone.

## Per-platform config

| Platform | Where `QuietMetrix.init(...)` goes | Reference |
|---|---|---|
| Android | `Application.onCreate()` | [SDK: Android](../sdk/android.md#initialization) |
| iOS | `AppDelegate.application(_:didFinishLaunchingWithOptions:)` | [SDK: iOS](../sdk/ios.md#initialization) |
| Web / JS | Your app's entry point, before first render | [SDK: Web](../sdk/web.md#initialization) |
| JVM | `fun main()`, before any tracked work starts | [SDK: JVM](../sdk/jvm.md#initialization) |
| Desktop (macOS/Windows/Linux) | `fun main()`, before the UI starts | [SDK: Desktop](../sdk/desktop.md#initialization) |

`storageKeyPrefix` is not part of the create-project response — the agent derives it from the
app's own identity (package name, bundle id, or npm package name), following the
`"myapp_"`-style convention shown in each platform's guide above.

## Two different credentials

QuietMetrix has two unrelated credentials, with opposite handling:

| | `qm_ak_…` — API key | `qm_pat_…` — access token |
|---|---|---|
| Goes into | The app's `QuietMetrix.init(...)` config | The agent's environment (`QUIETMETRIX_TOKEN`) |
| Who can see it | Anyone — it ships inside your compiled app | Only you and your agent's shell/MCP config |
| What it can do | Write events to one project. Nothing else. | Create and list projects for your account |
| Safe to commit? | **Yes.** See [Publishable API Key](../security/publishable-api-key.md) | **No — never.** |

The `qm_ak_…` key is designed to be public: it is write-only, project-scoped, and expected to
ship inside client applications — that's why the agent writes it straight into your source.
The `qm_pat_…` token is the opposite: it can create new projects for your account, so it must
never enter the target repository, never be committed, and never be pasted into a prompt or
issue. Set it as an environment variable only.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `error: QUIETMETRIX_TOKEN is not set` | The agent's shell doesn't have the token. | `export QUIETMETRIX_TOKEN=qm_pat_…` and restart the agent session. |
| `401` / `Invalid or expired token` | Token was revoked, or expired if you set an expiry when minting it. | Mint a new one at Settings → Access tokens. |
| `403` / `Only admins can create projects` | The token's scope doesn't include `projects:create`, or the account that minted it isn't an admin. | Mint a token with `projects:create` from an admin account. |
| `403` / `project_limit_reached` | The account has hit its plan's project quota. | Not a token problem — same limit a human hits creating a project by hand. |
| Retried and got a second project | No `Idempotency-Key` was sent, or a different one each retry. | The CLI/MCP tool sets this automatically; if calling the API directly, reuse the same key across retries of the same logical request. |

## Reference

- CLI: `npx @sobuumedia/quietmetrix-cli --help`
- MCP server: `@sobuumedia/quietmetrix-mcp` (tools `quietmetrix_create_project`,
  `quietmetrix_list_projects`)
- API: `POST /api/v1/tokens`, `POST /api/v1/projects` — see [API Reference](../api-reference.md)
  and [openapi.yaml](../openapi.yaml)
