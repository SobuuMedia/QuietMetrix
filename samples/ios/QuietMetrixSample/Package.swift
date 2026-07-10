// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "QuietMetrixSample",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "QuietMetrixSample", targets: ["QuietMetrixSample"])
    ],
    dependencies: [
        // The QuietMetrix XCFramework is produced by the KMP build:
        //   ./gradlew :quietmetrix-sdk:assembleQuietMetrixXCFramework
        // Copy the framework into Frameworks/ or link it from the output path.
    ],
    targets: [
        .target(
            name: "QuietMetrixSample",
            dependencies: [],
            path: "."
        )
    ]
)
