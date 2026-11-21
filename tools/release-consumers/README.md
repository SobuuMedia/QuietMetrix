# Independent SDK consumers

These projects consume Maven coordinates; they do not depend on repository SDK projects.
On macOS, first run `python3 tools/release/prepare-sdk.py --local` to install the
current core, Compose and debug artifacts without forcing unsupported native targets.
Then run `./gradlew -p tools/release-consumers/jvm compileKotlin -PqmSdkVersion=<version>` and
`./gradlew -p tools/release-consumers/android assembleDebug -PqmSdkVersion=<version>`.
Build the browser WASM consumer with
`./gradlew -p tools/release-consumers/wasm wasmJsBrowserDistribution -PqmSdkVersion=<version>`.
The release workflow passes the version from `quietmetrix-sdk/build.gradle.kts`, so these fixtures
always resolve the artifacts being released rather than a stale pin.
Compose consumers apply the Compose Multiplatform plugin to package the Skiko browser runtime.
After building, run `node tools/e2e/wasm-consumer-proof.cjs` with Playwright installed
(`QM_PLAYWRIGHT_MODULE` can specify its absolute module path). It verifies browser
initialization, durable refusal, and denied storage using the published WASM artifacts.

The release uses Kotlin 2.4.20 metadata. Android's built-in Kotlin compiler must be
compatible. The Android fixture pins a newer KGP on its buildscript classpath using
[Android's documented upgrade mechanism](https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade-higher-kgp).
It keeps built-in Kotlin and the current Android DSL enabled.

To prove consent and visit persistence, set `QM_ANDROID_DEVICE` to a test emulator's adb
serial and `QM_ADB` to adb, then run `python3 tools/release-consumers/android/verify-restart.py`.
It installs and resets **only** `com.quietmetrix.releaseproof`; it does not reset the sample
or demo app. The fixture has no tracking endpoint and sends no analytics externally.

The SwiftUI consumer lives under `samples/ios/QuietMetrixSample`; its README explains
local XCFramework integration. Remote registry publication is a separate release action.
