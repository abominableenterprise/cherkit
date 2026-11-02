# CherKit Telemetry

## Privacy rules

- **Diagnostics, not tracking.** Never send user IDs, emails, names, free text, screen contents, URLs or money amounts.
  This facade validates shape, not meaning: apps must still choose safe, static names and attributes.
- **On by default, user-controlled.** `enabled` defaults to `true`. Set it from the persisted diagnostics preference
  before making any calls. Nothing reaches the backend unless `buildEnabled && enabled`.
- **Off in debug, tests and simulators/emulators.** Apps supply `buildEnabled`; there is no automatic release-build
  detection in the facade. Reuse the existing [AnalyticsTestHost](../../Sources/CherKitAnalytics/AnalyticsTestHost.swift)
  / [Kotlin AnalyticsTestHost](../analytics/src/commonMain/kotlin/com/abominableenterprise/cherkit/analytics/AnalyticsTestHost.kt)
  if the app already links Analytics. Otherwise pass an equivalent app-owned test-host flag. Telemetry does not
  depend on Analytics or its vendor SDK just for detection. Simulator/emulator detection remains app-owned too.
- **Restricted sessions:** set `restricted = true` for kids' devices or restricted sessions. Only error reports pass;
  breadcrumbs, spans, span attributes and custom keys are dropped. The user/build switches still apply to errors.
- **Names and keys:** ASCII `^[a-z][a-z0-9_.]{0,63}$`. Invalid names drop the operation; invalid attribute keys drop
  that attribute. Values are only Bool/Boolean, Int, finite Double, short ASCII strings matching
  `^[a-z0-9_.-]{1,64}$`, or [AppVersion](../../Sources/CherKitCore/AppVersion.swift). No collections, arbitrary objects,
  nulls, Float or Long. Validation is repeated for `Span.setAttribute` and `setKey`.
- **Errors:** adapters receive a `TelemetryError` containing only type/domain/code and filtered attributes, never the
  original error, `localizedDescription`, `message`, user info or cause. Domains should be static identifiers, not
  user-controlled text. The core deliberately does not transport a raw exception or stack: those may contain private
  text. Crash-stack capture and sanitization belong to the later platform adapters. There is no free-text message API.

## Swift usage

Link `CherKitTelemetry`; import `CherKitCore` when passing an `AppVersion`. Calls are `@MainActor`, like Analytics.
The app supplies a backend implementing `TelemetryBackend`; no concrete vendor backend ships in this PR.

```swift
import CherKitTelemetry
import CherKitCore
import CherKitAnalytics // only if already used by the app, for AnalyticsTestHost

@MainActor
func configure(backend: TelemetryBackend, isReleaseBuild: Bool,
               isSimulator: Bool, diagnosticsEnabled: Bool) -> Telemetry {
    let telemetry = Telemetry(
        backend: backend,
        buildEnabled: isReleaseBuild && !AnalyticsTestHost.isRunningTests && !isSimulator
    )
    telemetry.enabled = diagnosticsEnabled
    return telemetry
}
```

```swift
telemetry.breadcrumb(name: "sync.started", attributes: ["source": "manual"])
telemetry.recordError(domain: "storage", code: 3, attributes: ["retry": true])
telemetry.setKey("app.version", value: AppVersion("1.2.3")!)
let result = try telemetry.span("cache.read") { try readCache() }
let response = try await telemetry.span("sync.fetch") { try await fetch() }
let span = telemetry.startSpan(name: "sync.apply")
span.setAttribute("items", value: 4)
span.end(status: .ok)
```

## Kotlin usage

Depend on `:telemetry` (published as `com.github.abominableenterprise.cherkit:telemetry` at the next release).
Use one instance on the main thread; the Kotlin facade is not thread-safe, matching the Swift main-actor contract.

```kotlin
import com.abominableenterprise.cherkit.telemetry.*
import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.analytics.AnalyticsTestHost // if already linked

val telemetry = Telemetry(
    backend = myBackend,
    buildEnabled = !BuildConfig.DEBUG && !AnalyticsTestHost.isRunningTests && !isEmulator,
)
// Apply restriction before enabled: assigning enabled may start automatic collection.
telemetry.restricted = session.isRestricted
telemetry.enabled = settings.diagnosticsEnabled
telemetry.breadcrumb("sync.started", mapOf("source" to "manual"))
telemetry.recordError(domain = "storage", code = 3, attributes = mapOf("retry" to true))
telemetry.setKey("app.version", AppVersion.parse("1.2.3")!!)
// In a coroutine:
val result = telemetry.span("cache.read") { readCache() }
val span = telemetry.startSpan("sync.apply")
span.setAttribute("items", 4)
span.end(SpanStatus.OK)
```

## Lifecycle and adapter contract

The backend starts lazily on the first valid operation, or when an open `enabled` switch is assigned, matching
Analytics. Closing the gate stops it; reopening starts it again. Failed starts trigger best-effort `stop()` rollback
and later calls may retry. Backend exceptions never escape into app code. App block results and exceptions pass
through unchanged; `span` ends with success or error, including cancellation. Manual spans end at most once.
Disabling or entering restricted mode terminates active traces before changing the gate; old handles never revive.
`Telemetry.disabled` returns a fresh, permanently build-disabled instance. Read-only `isDisabled` reports a closed
build/user gate, not restricted mode (errors remain available there).

Adapters implement `start`, `stop`, `recordError`, `breadcrumb`, `startTrace` (returning an opaque string ID),
`stopTrace`, `setAttribute` and `setKey`. Kotlin adapters may also override the default no-op
`updateRestricted(restricted: Boolean)`: the core calls it with the current state before every `start`, and immediately
on each restriction change while the backend is active, after terminating live spans. Unchanged assignments do not
call the hook; assigning `restricted` never starts an inactive backend. The hook must apply the restriction without
starting collection itself. In restricted sessions it must disable automatic diagnostics and Performance collection
while leaving error reporting available. If the hook throws, the core skips startup or stops the active backend
(best effort); later operations may retry restriction setup before starting again.

Adapters must clear retained context and traces on `stop`. The core drops all new diagnostic context while restricted;
adapters must not enable automatic breadcrumb or trace capture. The core cannot retract vendor data already recorded before a
switch changes. A tiny [TelemetrySink](../../Sources/CherKitCore/TelemetrySink.swift) in Core exposes domain/code
errors and breadcrumbs for future module wiring; no other module reports telemetry in this PR.

## Backends: Firebase adapter coming

This is the core contract only: no Firebase or other vendor SDK dependencies. Later Android and Apple adapters will
use Crashlytics and Performance. Automatic Performance network monitoring must default **off**, because URLs may
contain private query strings. App-side Firebase setup, crash collection controls and symbol upload documentation
will ship with those adapters.

## Shared tests

[Shared fixtures](../../fixtures/telemetry) cover every build/user/restricted combination, lifecycle sequences and
validation boundaries; both platforms execute every case. In-memory recording backends exercise failures and spans.
Run `scripts/test.sh telemetry` (Swift, JVM and Android host tests) or `scripts/test.sh` for the full Linux suite.
Gradle settings, publishing and both test scripts discover the module folder automatically; no settings edit is needed.
