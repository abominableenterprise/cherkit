# CherKit

CherKit is the shared library for Abominable Enterprise's iOS and Android apps.
It ships as a Swift Package (iOS 16+, macOS 13+) and as Kotlin Multiplatform modules (Android + JVM). Each module is
its own product, so an app imports only what it needs.

## Modules

| Module | What it does | Swift product | Kotlin module |
| --- | --- | --- | --- |
| [Analytics](kotlin/analytics/README.md) | Typed, opt-in PostHog facade with a fixed event list | `CherKitAnalytics` | `:analytics` |
| [Sunset](kotlin/sunset/README.md) | Soft or hard "please update" from a hosted min-version config | `CherKitSunset` | `:sunset` |
| [WhatsNew](kotlin/whatsnew/README.md) | "What's new" notes and installed-version history | `CherKitWhatsNew` | `:whatsnew` |
| [CherKitUI](Sources/CherKitUI/README.md) | SwiftUI views for Sunset and WhatsNew | `CherKitUI` | n/a |

Status: v0.1.0: Analytics (PostHog), Sunset and WhatsNew logic plus SwiftUI views; Android UI not included.

## Install

Releases are git tags `vX.Y.Z`. Use the same version on both platforms.

### iOS / macOS (SwiftPM)

Add the package `https://github.com/abominableenterprise/cherkit` (Xcode: File > Add Package Dependencies, rule
"Up to Next Major Version" from 0.1.0), then link only the products you use:
`CherKitAnalytics`, `CherKitSunset`, `CherKitWhatsNew`, `CherKitUI` (SwiftUI views for Sunset and WhatsNew) and
`CherKitCore` (pulled in by Sunset and WhatsNew).

```swift
dependencies: [
    .package(url: "https://github.com/abominableenterprise/cherkit", from: "0.1.0"),
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
    implementation("com.github.abominableenterprise.cherkit:analytics:v0.1.0")
    implementation("com.github.abominableenterprise.cherkit:sunset:v0.1.0")
    implementation("com.github.abominableenterprise.cherkit:whatsnew:v0.1.0")
    implementation("com.github.abominableenterprise.cherkit:core:v0.1.0") // already included by sunset and whatsnew
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
scripts/test.sh sunset       # one module (analytics, sunset or whatsnew) on both platforms
```

On macOS with Xcode, Swift tests run with `swift test` and Kotlin tests with `./gradlew jvmTest`.
Contributors and agents: read [AGENTS.md](AGENTS.md) first.

License: MIT (see [LICENSE](LICENSE)).
