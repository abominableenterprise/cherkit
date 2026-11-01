# CherKit

CherKit is the shared library for Abominable Enterprise's iOS and Android apps.
It ships as a Swift Package (iOS 16+, macOS 13+) and as Kotlin Multiplatform modules (Android + JVM). Each module is
its own product, so an app imports only what it needs.

## Modules

| Module | What it does | Swift product | Kotlin module |
| --- | --- | --- | --- |
| [Analytics](kotlin/analytics/README.md) | Typed, opt-in PostHog facade with a fixed event list | `CherKitAnalytics` | `:analytics` |
| [Sunset](kotlin/sunset/README.md) | Soft or hard "please update" from a hosted min-version config | `CherKitSunset` | `:sunset` |
| [Sunset Compose](kotlin/sunset-compose/README.md) | Jetpack Compose update dialog and blocking screen for Sunset (Android) | n/a | `:sunset-compose` |
| [Review Compose](kotlin/review-compose/README.md) | Play In-App Review and Compose effects for Review (Android) | n/a | `:review-compose` |
| [Update Compose](kotlin/update-compose/README.md) | Play In-App Updates (flexible flow), once per session and never over another prompt (Android) | n/a | `:update-compose` |
| [WhatsNew](kotlin/whatsnew/README.md) | "What's new" notes and installed-version history | `CherKitWhatsNew` | `:whatsnew` |
| [WhatsNew Compose](kotlin/whatsnew-compose/README.md) | Jetpack Compose "What's new" sheet and version history for WhatsNew (Android) | n/a | `:whatsnew-compose` |
| [Review](kotlin/review/README.md) | Store-review prompt rules: when to ask, once per version, never over a modal (iOS prompt in `CherKitUI`; Android in Review Compose) | `CherKitReview` | `:review` |
| [CherKitUI](Sources/CherKitUI/README.md) | SwiftUI views for Sunset and WhatsNew, and the App Store review prompt | `CherKitUI` | n/a |

Status: v0.5.0: Analytics (PostHog), Sunset, WhatsNew and Review logic plus SwiftUI views (including the review prompt); Android UI: Sunset Compose, WhatsNew Compose and Review Compose.

## Install

Releases are git tags `vX.Y.Z`. Use the same version on both platforms.

### iOS / macOS (SwiftPM)

Add the package `https://github.com/abominableenterprise/cherkit` (Xcode: File > Add Package Dependencies, rule
"Up to Next Major Version" from 0.5.0), then link only the products you use:
`CherKitAnalytics`, `CherKitSunset`, `CherKitWhatsNew`, `CherKitReview`, `CherKitUI` (SwiftUI views for Sunset and WhatsNew, and the review prompt) and
`CherKitCore` (pulled in by Sunset and WhatsNew).

```swift
dependencies: [
    .package(url: "https://github.com/abominableenterprise/cherkit", from: "0.5.0"),
],
targets: [
    .target(name: "MyApp", dependencies: [
        .product(name: "CherKitSunset", package: "cherkit"),
        .product(name: "CherKitWhatsNew", package: "cherkit"),
        .product(name: "CherKitUI", package: "cherkit"),
    ]),
]
```

### Android / JVM (Gradle, via JitPack)

Add JitPack to the repositories in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then depend on the modules you use. The version is the git tag, including the `v`. Gradle picks the Android (AAR) or
JVM variant automatically, and from a Kotlin Multiplatform module you can add them to `commonMain`.

```kotlin
dependencies {
    implementation("com.github.abominableenterprise.cherkit:analytics:v0.5.0")
    implementation("com.github.abominableenterprise.cherkit:sunset:v0.5.0")
    implementation("com.github.abominableenterprise.cherkit:sunset-compose:v0.5.0")   // Android only; brings sunset
    implementation("com.github.abominableenterprise.cherkit:whatsnew:v0.5.0")
    implementation("com.github.abominableenterprise.cherkit:whatsnew-compose:v0.5.0") // Android only; brings whatsnew
    implementation("com.github.abominableenterprise.cherkit:review:v0.5.0")
    implementation("com.github.abominableenterprise.cherkit:review-compose:v0.5.0")   // Android only; brings review
    implementation("com.github.abominableenterprise.cherkit:update-compose:v0.6.0")   // Android only; available from 0.6.0
    implementation("com.github.abominableenterprise.cherkit:core:v0.5.0")             // already included by sunset and whatsnew
}
```

The Kotlin packages stay `com.abominableenterprise.cherkit.<module>`; only the Maven group follows JitPack's
`com.github.<owner>.<repo>` convention.

The `.module` files JitPack serves name the component `com.github.abominableenterprise:cherkit` (JitPack rewrites that
block for every artifact of a multi-module build) instead of `com.github.abominableenterprise.cherkit:<module>`. Gradle
ignores those fields: it identifies each artifact by the coordinates you request, takes capabilities from them and
follows the correct `available-at` references, so several CherKit modules on one classpath resolve without capability
conflicts. The build itself writes the right coordinates and refuses to publish metadata that names any other (see
`build.gradle.kts`).

## Running the tests

```sh
scripts/setup-linux.sh       # once per machine: Swift, JDK, Android SDK (idempotent, cached)
scripts/test.sh              # size check + swift test + Gradle JVM and Android host tests
scripts/test.sh sunset       # one module (analytics, review, sunset or whatsnew) on both platforms
```

On macOS with Xcode, Swift tests run with `swift test` and Kotlin tests with `./gradlew jvmTest`.
Contributors and agents: read [AGENTS.md](AGENTS.md) first.

License: MIT (see [LICENSE](LICENSE)).
