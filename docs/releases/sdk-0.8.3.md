# QuietMetrix SDK 0.8.3

This release broadens Android build compatibility across the core and Compose SDKs.

- Core requires consumer compile SDK 24; the optional Compose adapter requires compile SDK 34. Both support Android devices from API 24. The libraries themselves build against SDK 37.
- The Compose adapter uses Compose Runtime without unused UI and Foundation dependencies. Other app dependencies, including Compose tooling, may require a higher compile SDK.
- The optional debug overlay has separate UI dependencies and its own compile SDK requirements.
- Core, Compose and debug artifacts use version 0.8.3, as does the npm package. Use a Kotlin 2.4.21-compatible compiler.

## Install

| Platform | Artifact |
| --- | --- |
| Browser JavaScript/TypeScript | `npm install @sobuumedia/quietmetrix-sdk@0.8.3` |
| Android / Kotlin Multiplatform | `io.github.sobuumedia:quietmetrix-sdk:0.8.3` |
| Compose experiment placements | `io.github.sobuumedia:quietmetrix-sdk-compose:0.8.3` |
| Optional debug overlay | `io.github.sobuumedia:quietmetrix-sdk-debug:0.8.3` |
| iOS | `QuietMetrix-0.8.3.xcframework.zip` and `ExperimentElement.swift` |

The release includes iOS device and simulator slices, SwiftUI source, the MIT license and checksums. KMP artifacts also provide the established JVM, WASM and desktop-native variants.
