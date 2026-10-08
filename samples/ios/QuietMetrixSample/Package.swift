// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "QuietMetrixSample",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "QuietMetrixSample", targets: ["QuietMetrixSample"])
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "QuietMetrix",
            path: "Frameworks/QuietMetrix.xcframework"
        ),
        .target(
            name: "QuietMetrixSample",
            dependencies: ["QuietMetrix"],
            path: ".",
            exclude: ["Frameworks", "README.md"]
        )
    ]
)
