# QuietMetrix SDK 0.7.0

Published on 2026-10-08: all 23 Maven Central components, npm `@sobuumedia/quietmetrix-sdk@0.7.0`, and [web/iOS release downloads](https://github.com/SobuuMedia/QuietMetrix/releases/tag/sdk-v0.7.0).

Minor release from 0.6.0. Core, Compose and optional debug artifacts use 0.7.0.

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

## Validation

Cross-platform release validation passed on macOS, Linux and Windows, including the complete 23-component signed Maven bundle, independent app/WASM consumers, SwiftUI typecheck and installed npm browser privacy checks. [Validation run](https://github.com/SobuuMedia/QuietMetrix/actions/runs/37761715191).

The npm and XCFramework archives are the exact binaries validated in that run.
