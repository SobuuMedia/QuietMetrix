# Contributing to QuietMetrix

Thanks for your interest in improving QuietMetrix! This document explains how to
build, test, and submit changes.

## Project layout

| Path | What it is |
| --- | --- |
| `quietmetrix-sdk/` | Kotlin Multiplatform analytics SDK (Android, iOS, JVM/desktop, Web/Wasm, native). |
| `servers/ktor/` | Kotlin/Ktor backend (PostgreSQL). The Docker default. |
| `php-hosting/` | Flat PHP backend (MySQL) for shared/IONOS-style hosting. |
| `dashboard/` | Compose Multiplatform (Wasm) web dashboard, served by either backend at `/dashboard/`. |
| `samples/` | Example apps consuming the SDK (android, desktop-jvm, ios, web). |
| `docs/` | MkDocs documentation site. |

## Backend parity rule

QuietMetrix ships **two** backends that must expose the same public HTTP API
(`docs/openapi.yaml`). **Any change to an API route, request/response shape, or
auth behaviour must be applied to both `servers/ktor/` and `php-hosting/`,** and
reflected in `docs/api-reference.md` / `docs/openapi.yaml`.

## Prerequisites

- JDK 21
- Docker + Docker Compose (to run the full stack)
- Xcode (only for building/testing the iOS target)
- PHP 8.2+ (only for the PHP backend)

## Build & test

```bash
# SDK unit tests (run on the iOS simulator target; requires macOS).
./gradlew :quietmetrix-sdk:iosSimulatorArm64Test

# Compile every SDK target.
./gradlew :quietmetrix-sdk:assemble

# Ktor server tests.
./gradlew :servers:ktor:test

# Dashboard tests (enforces i18n string parity + no hardcoded UI literals).
./gradlew :dashboard:jvmTest

# PHP backend syntax + smoke check.
cd php-hosting && php -l index.php

# Repo hygiene — fails if any private infra string leaks into tracked files.
./scripts/check-repo-hygiene.sh
```

> The SDK targets Android + iOS + JVM + Wasm + native. It has no `jvmTest` task;
> the canonical unit suite is `iosSimulatorArm64Test` (macOS only). Some
> `androidMain` tests need a real Android `Context` and run only under
> instrumentation.

## Run the full stack

```bash
cp docker/.env.example .env   # then edit the placeholder secrets
docker compose up --build
# API:       http://localhost:8080/api/v1/health
# Dashboard: http://localhost:8080/dashboard/
```

## Development workflow

We follow a tests-first, incremental workflow:

1. Open an issue describing the change (bug or feature) before large work.
2. Branch from `main`.
3. Write or update tests first, watch them fail, then implement until green.
4. Keep all existing tests green (`./gradlew build`).
5. Run `./scripts/check-repo-hygiene.sh` — never commit secrets or private hosts.
6. Open a pull request against `main`. CI must pass before review.

## Pull request checklist

- [ ] Tests added/updated and passing.
- [ ] API changes applied to **both** backends and to `docs/`.
- [ ] No secrets, credentials, or private hostnames added (hygiene check passes).
- [ ] `CHANGELOG.md` updated under *Unreleased* for user-facing changes.
- [ ] Commits are focused and clearly described.

## Reporting security issues

Please do **not** open public issues for vulnerabilities. See
[SECURITY.md](SECURITY.md).

## License

By contributing, you agree that your contributions are licensed under the
project's [MIT License](LICENSE).
