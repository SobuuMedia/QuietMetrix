# QuietMetrix SDK 0.8.2

Coordinated SDK distribution for browser/web, Android and iOS. Core, Compose and optional
debug artifacts use version **0.8.2**, matching the npm web package of the same version.
The public JavaScript API, aggregate counter protocol, consent behavior and queued receipts
are unchanged.

## Install

| Platform | Artifact |
| --- | --- |
| Browser JavaScript/TypeScript | `npm install @sobuumedia/quietmetrix-sdk@0.8.2` |
| Android / Kotlin Multiplatform | `io.github.sobuumedia:quietmetrix-sdk:0.8.2` on Maven Central |
| Compose experiment placements | `io.github.sobuumedia:quietmetrix-sdk-compose:0.8.2` |
| Optional debug overlay | `io.github.sobuumedia:quietmetrix-sdk-debug:0.8.2` |
| iOS Swift/SwiftUI | `QuietMetrix-0.8.2.xcframework.zip` plus `ExperimentElement.swift` in the GitHub release |

## Changes

- Use native browser fetch instead of the browser Ktor runtime.
- Generate ES2015 modules and browser source maps.
- Reuse rolling-window calendar projections and counter hashes.
- Yield between browser recording operations and recheck consent before recording.
- Build with Kotlin 2.4.21, Gradle 9.8.1 and Compose Multiplatform 1.12.2.

The XCFramework includes arm64 devices and arm64/x86_64 simulators. SwiftUI placement source,
MIT license, release notes and SHA-256 checksums accompany the archive. A hosted Swift Package
is not provided. The npm package includes its runtime, TypeScript definitions and license.

KMP metadata also retains JVM, WASM and desktop-native variants so published variant
references remain complete. Compose/debug targets retain their established platform support.

## Integration and compatibility

- Use a Kotlin 2.4.21-compatible compiler. Compose consumers use Compose Multiplatform 1.12.2.
- Supply an Android application context. Browser integrations use the npm package through their
  bundler. iOS integrations link the XCFramework and, if needed, compile the SwiftUI source.
- Country/language targeting uses explicit application inputs; locale is not a geographic lookup.
- The separate backend compatibility fix for legacy funnel revision zero is not part of this
  SDK package; the backend must be updated independently.
