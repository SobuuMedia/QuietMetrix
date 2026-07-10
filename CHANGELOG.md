# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
