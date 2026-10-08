# iOS login experiment sample

This sample uses the SDK XCFramework generated from this repository. Replace the
HTTPS endpoint and publishable project key in `App.swift` with values for a test
project before running it. The sample supplies `FR` and `fr` as explicit audience
attributes to demonstrate country and language targeting; production apps should
only supply values already known to the app.

Build the framework and copy it into the Swift package:

```sh
./gradlew :quietmetrix-sdk:assembleQuietMetrixXCFramework
mkdir -p samples/ios/QuietMetrixSample/Frameworks
cp -R quietmetrix-sdk/build/XCFrameworks/release/QuietMetrix.xcframework \
  samples/ios/QuietMetrixSample/Frameworks/
```

Open `samples/ios/QuietMetrixSample/Package.swift` in Xcode and select an iOS
Simulator destination. The local Swift package links the copied XCFramework as
the `QuietMetrix` binary target. To type-check the sample sources directly from
the repository without copying the artifact:

```sh
swiftc -typecheck \
  -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -target arm64-apple-ios17.0-simulator \
  -F quietmetrix-sdk/build/XCFrameworks/release/QuietMetrix.xcframework/ios-arm64_x86_64-simulator \
  -framework QuietMetrix \
  samples/ios/QuietMetrixSample/App.swift \
  samples/ios/QuietMetrixSample/ContentView.swift \
  samples/ios/QuietMetrixSample/ExperimentElement.swift
```

The login screen includes the visibility and two-variant wrappers, an explicit
consent action, and a `login_success` goal event. The tested children only render
for the selected B decision; the control remains the fallback for A, pending,
ineligible, and unavailable states.
