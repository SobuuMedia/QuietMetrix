# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Personal access tokens (`qm_pat_…`) so an agent or CLI can create and list projects
  without a user's password. Minted via `POST /api/v1/tokens` (session-authenticated,
  admin-only for the `projects:create` scope); manage them at Dashboard → Access tokens.
- `@sobuumedia/quietmetrix-cli` — provisions projects from the command line
  (`quietmetrix project create`), for agents and scripts.
- `@sobuumedia/quietmetrix-mcp` — an MCP server exposing project creation/listing as tools,
  for MCP-aware agents.
- `Idempotency-Key` support on `POST /api/v1/projects` — a retried create returns the
  existing project instead of minting a duplicate.
- `GET /api/v1/_meta` on the Ktor backend, matching the PHP backend's existing endpoint.
- [Agent-driven setup](docs/agents/setup.md) documentation and a copy-pasteable Claude Code
  skill.

## [0.4.0] - 2026-08-12

### Added
- Declarative funnel registration from app code across the supported Kotlin
  Multiplatform targets.
- Stable anonymous-install identity support for funnel analysis.
- Native HTTP transport support for funnel registration on Android, Apple,
  JVM, Linux, Windows, JavaScript, and Wasm targets.

### Changed
- Improved SDK queue restoration and batch transport behavior.
- Updated the published SDK coordinates and documentation for the 0.4 series.

## [0.2.0] - 2026-07-11

### Added
- One-command self-hosting via root `docker-compose.yml` (PostgreSQL → Flyway
  migrations → Ktor serving the API and the dashboard on port 8080).
- `scripts/check-repo-hygiene.sh` and a CI job that fail if private
  infrastructure strings leak into tracked files.
- Community health files: `CONTRIBUTING.md`, `SECURITY.md`,
  `CODE_OF_CONDUCT.md`, issue/PR templates.
- SDK now builds for all documented platforms: Android, iOS, JVM/desktop,
  Web (Wasm), and native (linux/macOS/Windows).

### Changed
- **Renamed the SDK module `quietmetrix-core` → `quietmetrix-sdk`.** Maven
  artifacts follow the Kotlin Multiplatform convention: `quietmetrix-sdk`,
  `quietmetrix-sdk-android`, `quietmetrix-sdk-jvm`, `quietmetrix-sdk-iosarm64`,
  etc. Update your dependency coordinates accordingly.
- SDK version bumped to `0.2.0`.

### Removed
- Commercial/billing code (Stripe/Adyen providers, checkout/usage routes) and
  paid-plan quota tiers. QuietMetrix is now a fully self-hostable, unlimited
  analytics server. The `plan_id` column is retained but imposes no limits.
- Internal commercial-strategy and payments documentation.

## [0.1.5] - Initial

- Initial internal release: KMP SDK, Ktor + PHP backends, Compose-Wasm dashboard.
