# Claude Code skill: QuietMetrix setup

Copy the block below into `.claude/skills/quietmetrix-setup/SKILL.md` in the app repo you want
wired up. It gives Claude Code a standing procedure for "set up QuietMetrix" requests instead
of re-deriving the steps each time. See [Agent-driven setup](setup.md) for the full flow this
implements, and the [publishable-API-key threat model](../security/publishable-api-key.md) for
why the two credentials below are handled so differently.

````markdown
---
name: quietmetrix-setup
description: Create a QuietMetrix analytics project and wire the SDK into this app. Use when the user asks to "set up QuietMetrix", "add analytics", or "create a QuietMetrix project".
---

# QuietMetrix setup

Provisions a QuietMetrix project and installs/configures the SDK in this repository.

## Prerequisites

- `QUIETMETRIX_TOKEN` must be set in the environment (a `qm_pat_…` personal access token,
  minted at the target server's Dashboard → Access tokens). If it's missing, stop and ask the
  user to export it — do not proceed without it, and never ask the user to paste it into chat.
- You need the QuietMetrix server URL. If the user didn't give one, ask.

## Steps

1. Run:
   ```bash
   npx @sobuumedia/quietmetrix-cli project create --url <URL> --name "<app name>"
   ```
   Derive `<app name>` from the repo/package name if the user didn't specify one. Parse the
   JSON on stdout: `api_key`, `api_key_last4`, `tracking_endpoint`.

2. Check whether the QuietMetrix SDK is already a dependency in this repo (search build files
   for `com.quietmetrix:quietmetrix-sdk`, `@sobuumedia/quietmetrix-sdk`, or an existing
   `QuietMetrix.init(...)` / `init(...)` call).

3. **If not installed:** add the dependency for this project's platform and create the
   `QuietMetrix.init(...)` call at the platform's standard entry point. **If already present:**
   only update the `trackingEndpoint`/`apiKey` fields — leave every other option (funnels,
   `flushIntervalMs`, consent defaults, `storageKeyPrefix`, …) untouched.

   | Platform | Entry point |
   |---|---|
   | Android | `Application.onCreate()` |
   | iOS | `AppDelegate.application(_:didFinishLaunchingWithOptions:)` |
   | Web / JS | App entry point, before first render |
   | JVM / Desktop | `fun main()`, before tracked work starts |

4. Write the returned `api_key` and `tracking_endpoint` into the config. The `qm_ak_…` key is
   **publishable** — safe to commit, safe inside the compiled app — write it as a plain string
   literal, the same way the platform's own docs show.

5. **Never** write `QUIETMETRIX_TOKEN` or any `qm_pat_…` value into this repository, in any
   file, comment, or commit. It stays an environment variable on the machine running the agent.

6. Report what changed: which file(s), whether the SDK was newly installed, and the
   project's dashboard URL (`<server URL>/dashboard/`).

## Retrying

If step 1 fails partway (network drop, timeout) and you're unsure whether the project was
created, re-run the same command with an added `--idempotency-key <a-key-you-choose-and-reuse>`
— retries with the same key return the existing project (HTTP 200, no new key) instead of
creating a duplicate. Note the response won't include `api_key` on a replay — the plaintext key
is only ever shown once, at first creation.
````
