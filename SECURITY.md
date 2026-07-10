# Security Policy

## Supported versions

QuietMetrix is pre-1.0. Security fixes are applied to the latest release on the
`main` branch. Please always test against the latest version before reporting.

## Reporting a vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, report privately via one of:

- GitHub's [private vulnerability reporting](https://github.com/SobuuMedia/QuietMetrix/security/advisories/new)
  ("Report a vulnerability" under the Security tab), or
- email **security@getsobuu.com** with details and, if possible, a proof of concept.

Please include:

- affected component (SDK, Ktor backend, PHP backend, or dashboard),
- version / commit,
- reproduction steps or PoC,
- impact assessment.

We aim to acknowledge reports within **72 hours** and to provide a remediation
timeline after triage. Please give us a reasonable window to release a fix before
public disclosure.

## Scope

In scope: the code in this repository — the SDK, both backends, and the dashboard.

Out of scope: third-party dependencies (report upstream), and any self-hosted
deployment's own infrastructure/misconfiguration.

## Handling of secrets

- Never commit real credentials. Per-deployment secrets live in untracked files
  (`php-hosting/config.php`, `.env`) that are git-ignored.
- CI runs `scripts/check-repo-hygiene.sh`, which fails the build if private
  hostnames or credentials are found in tracked files.
