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

Status: scaffold only; modules contain placeholders.

## Running the tests

```sh
scripts/setup-linux.sh       # once per machine: Swift, JDK, Android SDK (idempotent, cached)
scripts/test.sh              # size check + swift test + Gradle JVM and Android host tests
scripts/test.sh sunset       # one module (analytics, sunset or whatsnew) on both platforms
```

On macOS with Xcode, Swift tests run with `swift test` and Kotlin tests with `./gradlew jvmTest`.
Contributors and agents: read [AGENTS.md](AGENTS.md) first.

License: MIT (see [LICENSE](LICENSE)).
