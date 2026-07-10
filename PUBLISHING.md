# Publishing QuietMetrix Core

This document describes how to release the `quietmetrix-sdk` Kotlin Multiplatform module.

## Coordinates

| Field      | Value                                                           |
| ---------- | --------------------------------------------------------------- |
| Group      | `com.quietmetrix`                                               |
| Artifact   | `quietmetrix-sdk` (+ KMP target variants)                      |
| Version    | Set in `quietmetrix-sdk/build.gradle.kts` (`version = "..."`)  |
| Repository | `sobuumedia/quietmetrix` on GitHub                              |

Bump `version` before each release, then commit and tag (`v0.1.0`, `v0.2.0`, …).

## Release targets

The module's `publishing` block activates each repository only when the matching env vars are present, so a single command (`:quietmetrix-sdk:publish`) is safe to run in any environment.

| Repository       | Activated by                                            | When to use                                          |
| ---------------- | ------------------------------------------------------- | ---------------------------------------------------- |
| `mavenLocal`     | Always                                                  | Quick dev integration tests on your machine.         |
| `GitHubPackages` | `GITHUB_ACTOR` + `GITHUB_TOKEN`                         | **Current production release target.**               |
| `sonatype`       | `OSSRH_USERNAME` + `OSSRH_TOKEN` (+ GPG signing vars)   | Maven Central — **future**, see below.               |

## GitHub Packages release (current)

### One-time: create a Personal Access Token

1. https://github.com/settings/tokens → *Generate new token (classic)*.
2. Scopes: `write:packages`, `read:packages`, `delete:packages`.

### Per-release

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<the-PAT>

./gradlew :quietmetrix-sdk:publish
```

Consumers add the registry to their `settings.gradle.kts`:
```kotlin
maven {
    url = uri("https://maven.pkg.github.com/sobuumedia/quietmetrix")
    credentials {
        username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
        password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.token").orNull
    }
}
```
…and depend on `com.quietmetrix:quietmetrix-sdk:<version>`. They need a PAT with at least `read:packages`.

## Maven Central release (future)

The module is **prepared** for Central — POM metadata, staging URL, and signing block are wired. The remaining steps are organisational:

### 1. Sonatype Central Portal account

- Sign up at https://central.sonatype.com.
- Verify ownership of `com.quietmetrix` by adding a TXT DNS record on `quietmetrix.com` (or whichever domain you control under that namespace).
- One verification covers every artifact under that namespace.

### 2. GPG key

```bash
gpg --gen-key
gpg --list-secret-keys --keyid-format=long
gpg --armor --export-secret-keys <KEY_ID>      # → GPG_SIGNING_KEY (ASCII-armored block)
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```
Store the armored private key and its passphrase in CI secrets:
- `GPG_SIGNING_KEY`
- `GPG_SIGNING_PASSWORD`

### 3. Central API token

- In Central Portal → *Account* → *Generate User Token*.
- Store the username + token in CI secrets:
  - `OSSRH_USERNAME`
  - `OSSRH_TOKEN`

### 4. Release command

```bash
./gradlew :quietmetrix-sdk:publishAllPublicationsToSonatypeRepository
```

Then log in to https://central.sonatype.com → *Deployments* → *Promote*.

### 5. SNAPSHOT releases

A `-SNAPSHOT` suffix on `version` automatically routes to the snapshots URL — no manual change needed.

## Checklist before tagging a release

- [ ] `version` bumped in `quietmetrix-sdk/build.gradle.kts`.
- [ ] Changelog updated.
- [ ] `./gradlew :quietmetrix-sdk:publishToMavenLocal` and a smoke test in a sample app pass.
- [ ] CI is green on `main`.
- [ ] Tag pushed (`git tag v0.x.y && git push --tags`).
- [ ] `:quietmetrix-sdk:publish` (GitHub Packages).
- [ ] (Future) `:quietmetrix-sdk:publishAllPublicationsToSonatypeRepository` + portal promote.
