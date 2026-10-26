// swift-tools-version: 5.9
// Every planned CherKit target is declared here so module PRs rarely touch this file.
// Each module is its own library product: apps import only what they need.
import PackageDescription

let package = Package(
    name: "CherKit",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "CherKitCore", targets: ["CherKitCore"]),
        .library(name: "CherKitAnalytics", targets: ["CherKitAnalytics"]),
        .library(name: "CherKitSunset", targets: ["CherKitSunset"]),
        .library(name: "CherKitWhatsNew", targets: ["CherKitWhatsNew"]),
        .library(name: "CherKitUI", targets: ["CherKitUI"]),
    ],
    dependencies: [
        // PostHog SDK for the Analytics adapter, pinned version.
        // Linked only on Apple platforms (see CherKitAnalytics), so Linux never compiles it.
        .package(url: "https://github.com/PostHog/posthog-ios", exact: "3.71.4"),
    ],
    targets: [
        // Logic modules: Foundation only, built and tested on Linux.
        // CherKitCore: tiny shared helpers (AppVersion) used by other modules.
        .target(name: "CherKitCore", exclude: ["README.md"]),
        // CherKitAnalytics: PostHog is linked on Apple platforms only; code using it stays in `#if canImport(PostHog)`.
        .target(
            name: "CherKitAnalytics",
            dependencies: [
                .product(name: "PostHog", package: "posthog-ios", condition: .when(platforms: [.iOS, .macOS])),
            ],
            exclude: ["README.md"]
        ),
        .target(name: "CherKitSunset", dependencies: ["CherKitCore"], exclude: ["README.md"]),
        .target(name: "CherKitWhatsNew", dependencies: ["CherKitCore"], exclude: ["README.md"]),
        // SwiftUI views. Sources are wrapped in `#if canImport(SwiftUI)` so Linux builds stay green.
        .target(name: "CherKitUI", dependencies: ["CherKitSunset", "CherKitWhatsNew"], exclude: ["README.md"]),
        // Test-only helper that loads the shared JSON contract in fixtures/. Not a product.
        .target(name: "CherKitTestSupport", path: "Tests/CherKitTestSupport"),
        .testTarget(name: "CherKitCoreTests", dependencies: ["CherKitCore", "CherKitTestSupport"]),
        .testTarget(name: "CherKitAnalyticsTests", dependencies: ["CherKitAnalytics", "CherKitTestSupport"]),
        .testTarget(name: "CherKitSunsetTests", dependencies: ["CherKitSunset", "CherKitTestSupport"]),
        .testTarget(name: "CherKitWhatsNewTests", dependencies: ["CherKitWhatsNew", "CherKitTestSupport"]),
        // The logic behind the CherKitUI views. Its files are wrapped in `#if canImport(SwiftUI)`, so empty on Linux.
        .testTarget(
            name: "CherKitUITests",
            dependencies: ["CherKitUI", "CherKitCore", "CherKitSunset", "CherKitWhatsNew"]
        ),
    ]
)
