analytics: added

- `Analytics` (Swift `CherKitAnalytics`, Kotlin `com.abominableenterprise.cherkit.analytics`): a typed, privacy-first
  facade. Define your allowlist as a type conforming to `AnalyticsEvent` (a Swift `String` enum, or a Kotlin enum
  overriding `eventName`) and call `track(event)`, which returns whether the event was sent.
- Events are sent only when `buildEnabled` (passed at init; pass `false` in debug, tests and emulators),
  `consentGranted` (default `false`) and `sessionActive` (never on a kid's device) are all true, and the name matches
  `^[a-z][a-z0-9_]{0,63}$`. Event properties are deliberately not supported.
- Provider adapters implement `AnalyticsBackend` (`start`, `capture(name)`, `stop`). The backend starts when the gate
  opens and stops whenever it closes; a `start()` that throws is followed at once by `stop()` to roll back a partial
  opt-in. No provider ships yet; the PostHog adapter follows in a later release.
- `AnalyticsTestHost.isRunningTests` detects XCTest (Swift) or JUnit / AndroidX Test (Kotlin) so tests stay offline.
- The scaffold placeholders `AnalyticsModuleInfo` (Swift and Kotlin) are removed.
