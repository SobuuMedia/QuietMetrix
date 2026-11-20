# QuietMetrix SDK 0.8.0

Coordinated SDK distribution for browser/web, Android and iOS. Core, Compose and optional
debug artifacts use version **0.8.0**. This release retains the aggregate protocol and public
SDK integrations from 0.7.0; no new collection permissions or raw-event transport is introduced.

## Install

| Platform | Artifact |
| --- | --- |
| Browser JavaScript/TypeScript | `npm install @sobuumedia/quietmetrix-sdk@0.8.0` |
| Android / Kotlin Multiplatform | `io.github.sobuumedia:quietmetrix-sdk:0.8.0` on Maven Central |
| Compose experiment placements | `io.github.sobuumedia:quietmetrix-sdk-compose:0.8.0` |
| Optional debug overlay | `io.github.sobuumedia:quietmetrix-sdk-debug:0.8.0` |
| iOS Swift/SwiftUI | `QuietMetrix-0.8.0.xcframework.zip` plus `ExperimentElement.swift` in the GitHub release |

The XCFramework includes arm64 devices and arm64/x86_64 simulators. SwiftUI placement source,
MIT license, release notes and SHA-256 checksums accompany the archive. A hosted Swift Package
is not provided. The npm package includes its runtime, TypeScript definitions and license.

KMP metadata also retains JVM, WASM and desktop-native variants so published variant references
remain complete. Compose/debug targets retain their established platform support.

## Integration and compatibility

- Tracking defaults to disabled until consent. Explicit refusal/opt-out persist across reloads
  and restarts; unavailable storage fails safely.
- Use a Kotlin 2.4.20-compatible compiler. Compose consumers use Compose Multiplatform 1.12.0.
- Supply an Android application context. Browser integrations use the npm package through their
  bundler. iOS integrations link the XCFramework and, if needed, compile the SwiftUI source.
- Remote funnels collect future activity after SDK config refresh. Semantic definition changes
  retain separate revisions. Experiment placement code stays in the integrating application.
- Country/language targeting uses explicit application inputs; locale is not a geographic lookup.
- The recent dashboard filter, AI, retention and funnel-editor fixes are backend/dashboard
  changes. Deploy that server/dashboard revision separately to use those management features;
  publishing an SDK does not deploy the analytics server.

## Validation and publication

The release workflow tests native hosts and SDK suites, builds all signed Maven components,
checks independent Android/JVM/WASM consumers, typechecks the SwiftUI integration, and runs the
production npm browser consent/reload/storage-denial proof before publishing. It verifies npm
and XCFramework metadata/architectures and the release checksum manifest.
