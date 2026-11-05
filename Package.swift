// swift-tools-version: 5.9
// Every planned CherKit target is declared here so module PRs rarely touch this file.
// Each module is its own library product: apps import only what they need.
import PackageDescription

var firebaseProducts: [Product] = []
var firebasePackages: [Package.Dependency] = []
var firebaseTargetDependencies: [Target.Dependency] = ["CherKitTelemetry", "CherKitCore"]
#if !os(Linux)
firebaseProducts = [.library(name: "CherKitTelemetryFirebase", targets: ["CherKitTelemetryFirebase"])]
firebasePackages = [.package(url: "https://github.com/firebase/firebase-ios-sdk", "11.0.0"..<"14.0.0")]
firebaseTargetDependencies += [
    .product(name: "FirebaseCrashlytics", package: "firebase-ios-sdk",
             condition: .when(platforms: [.iOS, .macOS])),
    // Firebase Performance does not support macOS.
    .product(name: "FirebasePerformance", package: "firebase-ios-sdk",
             condition: .when(platforms: [.iOS])),
]
#endif

// Optional attribution products: apps link only the adapters they select.
// Linux keeps the empty targets/tests, but never resolves the Apple SDK packages.
var attributionPackages: [Package.Dependency] = []
var revenueCatDependencies: [Target.Dependency] = ["CherKitCore"]
var metaDependencies: [Target.Dependency] = ["CherKitAttributionRevenueCat"]
#if !os(Linux)
attributionPackages = [
    .package(url: "https://github.com/RevenueCat/purchases-ios", "5.0.0"..<"6.0.0"),
    .package(url: "https://github.com/facebook/facebook-ios-sdk", "19.0.0"..<"20.0.0"),
]
revenueCatDependencies += [
    .product(name: "RevenueCat", package: "purchases-ios", condition: .when(platforms: [.iOS, .macOS])),
]
metaDependencies += [
    .product(name: "FacebookCore", package: "facebook-ios-sdk", condition: .when(platforms: [.iOS])),
]
#endif

let package = Package(
    name: "CherKit",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "CherKitCore", targets: ["CherKitCore"]),
        .library(name: "CherKitAttributionRevenueCat", targets: ["CherKitAttributionRevenueCat"]),
        .library(name: "CherKitAttributionMeta", targets: ["CherKitAttributionMeta"]),
        .library(name: "CherKitAnalytics", targets: ["CherKitAnalytics"]),
        .library(name: "CherKitTelemetry", targets: ["CherKitTelemetry"]),
    ] + firebaseProducts + [
        .library(name: "CherKitSunset", targets: ["CherKitSunset"]),
        .library(name: "CherKitWhatsNew", targets: ["CherKitWhatsNew"]),
        .library(name: "CherKitReview", targets: ["CherKitReview"]),
        .library(name: "CherKitUI", targets: ["CherKitUI"]),
    ],
    dependencies: [
        // PostHog SDK for the Analytics adapter, pinned version.
        // Linked only on Apple platforms (see CherKitAnalytics), so Linux never compiles it.
        .package(url: "https://github.com/PostHog/posthog-ios", exact: "3.71.4"),
    ] + firebasePackages + attributionPackages,
    targets: [
        // Logic modules: Foundation only, built and tested on Linux.
        // CherKitCore: tiny shared helpers (AppVersion) used by other modules.
        .target(name: "CherKitCore", exclude: ["README.md"]),
        .target(name: "CherKitAttributionRevenueCat", dependencies: revenueCatDependencies),
        .target(name: "CherKitAttributionMeta", dependencies: metaDependencies),
        // CherKitAnalytics: PostHog is linked on Apple platforms only; code using it stays in `#if canImport(PostHog)`.
        .target(
            name: "CherKitAnalytics",
            dependencies: [
                .product(name: "PostHog", package: "posthog-ios", condition: .when(platforms: [.iOS, .macOS])),
            ],
            exclude: ["README.md"]
        ),
        .target(name: "CherKitTelemetry", dependencies: ["CherKitCore"]),
        .target(
            name: "CherKitTelemetryFirebase",
            // Keep the Foundation-only policy and recording-fake tests on Linux.
            // Live SDK imports are already guarded with canImport in the sources.
            dependencies: firebaseTargetDependencies,
            exclude: ["README.md"]
        ),
        .target(name: "CherKitSunset", dependencies: ["CherKitCore"], exclude: ["README.md"]),
        .target(name: "CherKitWhatsNew", dependencies: ["CherKitCore"], exclude: ["README.md"]),
        // CherKitReview: store-review prompt rules only; the StoreKit/SwiftUI adapter lives in CherKitUI.
        .target(name: "CherKitReview", dependencies: ["CherKitCore"], exclude: ["README.md"]),
        // SwiftUI views. Sources are wrapped in `#if canImport(SwiftUI)` so Linux builds stay green.
        .target(
            name: "CherKitUI",
            dependencies: ["CherKitSunset", "CherKitWhatsNew", "CherKitReview"],
            exclude: ["README.md"]
        ),
        // Test-only helper that loads the shared JSON contract in fixtures/. Not a product.
        .target(name: "CherKitTestSupport", path: "Tests/CherKitTestSupport"),
        .testTarget(name: "CherKitCoreTests", dependencies: ["CherKitCore", "CherKitTestSupport"]),
        .testTarget(name: "CherKitAttributionRevenueCatTests",
                    dependencies: ["CherKitAttributionRevenueCat", "CherKitTestSupport"]),
        .testTarget(name: "CherKitAttributionMetaTests", dependencies: ["CherKitAttributionMeta", "CherKitTestSupport"]),
        .testTarget(name: "CherKitAnalyticsTests", dependencies: ["CherKitAnalytics", "CherKitTestSupport"]),
        .testTarget(name: "CherKitTelemetryTests", dependencies: ["CherKitTelemetry", "CherKitTestSupport"]),
        .testTarget(name: "CherKitTelemetryFirebaseTests",
                    dependencies: ["CherKitTelemetryFirebase", "CherKitTelemetry", "CherKitCore"]),
        .testTarget(name: "CherKitSunsetTests", dependencies: ["CherKitSunset", "CherKitTestSupport"]),
        .testTarget(name: "CherKitWhatsNewTests", dependencies: ["CherKitWhatsNew", "CherKitTestSupport"]),
        .testTarget(name: "CherKitReviewTests", dependencies: ["CherKitReview", "CherKitTestSupport"]),
        // The logic behind the CherKitUI views. Its files are wrapped in `#if canImport(SwiftUI)`, so empty on Linux.
        .testTarget(
            name: "CherKitUITests",
            dependencies: ["CherKitUI", "CherKitCore", "CherKitSunset", "CherKitWhatsNew", "CherKitReview"]
        ),
    ]
)
