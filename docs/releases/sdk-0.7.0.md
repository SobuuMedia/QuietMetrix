# QuietMetrix SDK 0.7.0

Prepared minor release from 0.6.0. Core, Compose and optional debug artifacts use 0.7.0.

This release adds dashboard-managed experiment placements and versioned remote funnels,
and corrects consent persistence, bounded aggregate delivery, receipt replay, hourly
deduplication and foreground/background visit recording. Browser analytics use the same
consent-gated aggregate pipeline as app analytics. No installation identifier is transmitted.

## Platform artifacts

| Platform | Installation |
| --- | --- |
| Android | `io.github.sobuumedia:quietmetrix-sdk:0.7.0` |
| iOS device and Apple/Intel simulators | Maven core variants or `QuietMetrix-0.7.0.xcframework.zip` |
| Browser JavaScript/TypeScript | `@sobuumedia/quietmetrix-sdk@0.7.0` on npm |
| Compose browser/WASM | Maven core and `quietmetrix-sdk-compose:0.7.0` |
| JVM/Compose Desktop on Windows, Linux and macOS | Maven JVM variants |
| Kotlin/Native macOS Apple Silicon, Linux x64, Windows x64 | `macosArm64`, `linuxX64`, `mingwX64` core variants |
| Compose experiment UI / debug overlay | `quietmetrix-sdk-compose:0.7.0` / `quietmetrix-sdk-debug:0.7.0` |

Compose/debug modules support Android, JVM, WASM and Apple Silicon iOS device/simulator
targets. Intel iOS simulator and bare desktop-native targets use the core SDK. SwiftUI
placements ship as source alongside the XCFramework; a hosted Swift package is not provided.

## Upgrade

Use a Kotlin 2.4.20-compatible compiler; Compose consumers use 1.12.0. Android initialization
requires the application context. Tracking defaults to disabled: request consent and call
`setCookieConsent(true)` after acceptance. Explicit refusal and analytics opt-out survive
restarts. Unavailable persistent storage disables tracking. Country/language audiences use
app-supplied values, and do not infer geographic location from locale.

Upgrade the backend to the current schema and APIs before enabling dashboard-managed
experiments and remote funnels. Schema-2 capability minimum versions remain 0.6.0; existing
clients and fixtures retain their advertised versions. This version bump does not change
the established protocol compatibility threshold.

## Prepare and release

`python3 tools/release/prepare-sdk.py --check` verifies all artifact versions and release
notes. Run it without `--check` on each supported host to stage that host's Maven components.
The tag release workflow builds/tests on macOS, Linux and Windows, merges all 23 Maven
components into one signed bundle, and refuses missing platform artifacts. This follows
[Kotlin's host-specific publication model](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html).

The workflow supports manual preparation with publishing disabled. Tagging `sdk-v0.7.0` enables
publication after all gates: Maven Central, npm and then the GitHub release assets. It needs
`GPG_SIGNING_KEY`, `GPG_SIGNING_PASSWORD`, `OSSRH_USERNAME`, `OSSRH_PASSWORD` (Central
Portal user-token credentials) and `NPM_TOKEN`. The combined bundle uses the
[Central Publisher API](https://central.sonatype.org/publish/publish-portal-api/), waits for
`PUBLISHED`, and fails on validation errors. npm publication likewise gates the GitHub release.

No tag, remote registry upload or release has been created during local preparation. Before
tagging, commit the complete intended SDK changes, inspect the prepared release assets, and
run the updated workflow on all three hosts. Maven/npm versions are immutable after release;
an upload failure must be inspected before retrying to avoid partial publication assumptions.

## Preparation verification

Local 0.7.0 checks pass: 244 JVM, 256 Node, 244 iOS simulator and 244 macOS native tests.
Independent JVM (including Compose/debug), Android and WASM consumers compile. The
SwiftUI sample typechecks against the release XCFramework. The installed npm archive
runs the Chromium consent/reload/storage-denial checks and sends SDK version 0.7.0.
Published WASM artifacts initialize successfully with normal and denied browser storage.

All 21 macOS-hosted Maven components were staged and their signatures, runtime archives,
metadata and documentation checked. The full bundle correctly refuses release until the
Linux and Windows host jobs supply the remaining two components. Those native host jobs
have not run locally. Release scripts have four regression checks for complete bundles,
missing Windows artifacts, missing signatures and mismatched versions. Both workflows
pass actionlint, and repository hygiene passes.

Prepared web/iOS downloads are in `build/sdk-release/assets/`, with a SHA-256 manifest.
`python3 tools/release/check-sdk-assets.py build/sdk-release/assets` checks npm metadata,
runtime/types/license, iOS device/simulator architectures and 0.7.0 framework versions.
This verification log does not claim external publication or completed remote CI.
