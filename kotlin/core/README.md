# CherKit Core

Tiny shared helpers used by the other CherKit modules (and available to apps). Foundation-only on Swift,
`commonMain`-only on Kotlin. Keep it tiny: anything that is a feature belongs in its own module, and every change to
Core needs its own small PR because Sunset and WhatsNew depend on it.

- **`AppVersion`**: parses store-style versions (`1`, `1.2`, `1.2.3`, `1.2.3.4`; surrounding whitespace, a leading
  `v`, and a ` (45)` / `+45` / `-beta` suffix are tolerated and ignored) and compares them component-wise, treating
  missing components as 0 (`1.2 == 1.2.0`). Malformed input (empty, `abc`, `1..2`, negative numbers, a component over
  9 digits) parses to `nil` / `null`; callers fail open.

```swift
import CherKitCore
if let installed = AppVersion("1.2.3 (45)"), let minimum = AppVersion("1.3"), installed < minimum { /* update */ }
```

```kotlin
val installed = AppVersion.parse("1.2.3 (45)")
val minimum = AppVersion.parse("1.3")
if (installed != null && minimum != null && installed < minimum) { /* update */ }
```

Swift: `Sources/CherKitCore`, tests in `Tests/CherKitCoreTests`. Kotlin: `kotlin/core` (`:core`, package
`com.abominableenterprise.cherkit.core`). Shared test cases: `fixtures/core/version.json`. Test with
`scripts/test.sh core`.
