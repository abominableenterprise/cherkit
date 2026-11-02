# CherKit Telemetry Firebase (Android)

Implements the [Telemetry core](../telemetry/README.md) with Crashlytics non-fatals, crash collection,
breadcrumbs, custom keys and opt-in Performance traces. Available from the next release as
`com.github.abominableenterprise.cherkit:telemetry-firebase:<next-tag>` (not in v0.7.0).

## App-side Firebase setup

Firebase is **compileOnly** here: the adapter does not select or bundle the runtime SDKs. The app owns its
Firebase project, `google-services.json`, SDK versions, initialization and symbol uploads. Add `google()`
to plugin and dependency repositories and [JitPack](https://jitpack.io) to dependency repositories.
In the root build script declare the plugins, then apply them in the Android application module:

```kotlin
// Root build.gradle.kts
plugins {
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
    id("com.google.firebase.firebase-perf") version "2.0.2" apply false
}
```

```kotlin
// app/build.gradle.kts (alongside the app's Android/Kotlin plugins)
plugins {
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

dependencies {
    implementation("com.github.abominableenterprise.cherkit:telemetry-firebase:<next-tag>")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
}

```

In the app's root `gradle.properties`, disable plugin instrumentation globally (all variants):

```properties
# REQUIRED privacy default: no bytecode-instrumented HTTP/URL monitoring.
firebasePerformanceInstrumentationEnabled=false
```

See Firebase's [Crashlytics setup](https://firebase.google.com/docs/crashlytics/get-started?platform=android),
[Performance setup](https://firebase.google.com/docs/perf-mon/get-started-android) and
[instrumentation controls](https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android).
The Crashlytics plugin uploads release R8/ProGuard mapping files; retain that task in release CI. NDK crash
symbols require the app's separate NDK integration. No google-services file or Firebase plugins belong in CherKit.

### Close startup collection gates

Firebase initializes before `Application.onCreate`. Put these defaults in the **app manifest**, including
release builds; debug/test/emulator variants must keep them off. The adapter calls both runtime collection
switches off at construction as well, but cannot undo data captured before it exists. Runtime Firebase collection
choices persist across process launches and override manifest defaults: initialize this helper at the start of
`Application.onCreate`, before any diagnostics, and set the persisted user/restricted switches immediately.

```xml
<application>
    <meta-data android:name="firebase_crashlytics_collection_enabled" android:value="false" />
    <meta-data android:name="firebase_performance_collection_enabled" android:value="false" />
</application>
```

Do not add Firebase Analytics merely for breadcrumbs: Analytics-driven automatic Crashlytics logs bypass this
adapter. If the app already uses Analytics, keep its automatic breadcrumb collection disabled separately.

## Usage

```kotlin
import com.abominableenterprise.cherkit.telemetry.firebase.AndroidTelemetry

val telemetry = AndroidTelemetry.firebase(
    context = applicationContext,
    enabled = settings.diagnosticsEnabled,
    restricted = session.isRestricted,
    isRunningTests = appTestHost,
    isEmulator = appEmulatorFlag,
    performanceEnabled = false,
)
telemetry.recordError(domain = "storage", code = 3, attributes = mapOf("retry" to true))
telemetry.breadcrumb("sync.started", mapOf("source" to "manual"))
val span = telemetry.startSpan("sync.apply")
span.setAttribute("source", "manual")
span.end()
// Keep these synchronized with app settings/session changes:
telemetry.enabled = settings.diagnosticsEnabled
telemetry.restricted = session.isRestricted
```

`AndroidTelemetry.firebase(context, enabled = true, restricted = false, isRunningTests = detectedTests(),
isEmulator = detectedEmulator(), performanceEnabled = false): Telemetry` is the recommended public entry point.
It blocks debuggable apps, detects common JVM/instrumentation test hosts and emulator build fingerprints, and
accepts explicit app flags for hosts those heuristics miss. Never pass false test/emulator flags on a test host.
Use one instance on the main thread. `FirebaseTelemetryBackend(performanceEnabled = false)` is also public for
apps constructing `Telemetry` themselves; those apps must supply the core build gate and set restriction before
opening `enabled`. It implements the existing backend methods plus the additive default `setRestricted` hook.

### Performance privacy and opt-in

Performance collection defaults **off**, even when crash collection starts: the Android SDK has no public
manual-traces-only collection switch and also records automatic app-start/screen traces. Set
`performanceEnabled = true` only after reviewing that automatic data and keeping plugin network instrumentation
disabled as above. Restricted sessions always disable Performance, regardless of this option. To intentionally
opt into automatic network monitoring, set `firebasePerformanceInstrumentationEnabled=true` **and**
set `performanceEnabled = true`; review URL paths/query strings and every instrumented client first. This opts
out of CherKit's safe network default and is not appropriate for restricted sessions. Do not use Firebase's
manual HTTP metric APIs or independently wrapped HTTP clients under this policy.

## Guarantees and limits

- No `setUserId`, raw errors, cause chains, free-text messages, URLs or arbitrary objects are passed to Firebase.
  Error type/domain/code are sanitized into **per-exception** keys; allowlisted attributes cannot overwrite those
  identity keys. The core provides no original stack, so non-fatals use a synthetic exception with **no message,
  cause or stack**. Native fatal crash stacks remain Firebase-owned; this is not an API for raw exception capture.
- The adapter repeats shape validation; apps must still choose static, non-PII identifiers and values. Shape
  validation cannot recognize a name or money amount encoded as a valid token/number. There is no safe-message API.
- Restricted sessions permit only crashes/non-fatals and core-allowed error attributes. No logs, custom keys or
  manual traces. Entering restriction or disabling closes traces and neutralizes previously set custom key values.
- Firebase has no API to retract existing logs/custom-key names, erase already uploaded reports or abort a trace
  without ending it. Already captured context cannot be withdrawn by a switch; do not share an unrestricted
  diagnostics session with a child's session. Collection off does not delete previously persisted SDK reports.
- Trace keys must be ASCII letters/underscores (starting with a letter), at most 40 characters, excluding reserved
  `firebase_`, `google_`, `ga_` prefixes; values at most 100 characters (the core's token limit is stricter: 64).
  Five attributes per trace; extras/invalid entries drop, existing attributes can update. Trace names use the core's
  64-character static identifier rules. Error attributes/custom keys cap at 60, leaving room for identity keys.
- All SDK operations catch every `Throwable`, independently on cleanup. Missing Firebase or bad initialization
  never crashes the app through this adapter; it also means there may be no report. Configure Firebase first.

## Build and test

The KMP module publishes root metadata plus `telemetry-firebase-android` (AAR) and `telemetry-firebase-jvm` (JAR).
The Firebase client and public Android factory/backend exist only in the Android variant; the JVM variant contains
only internal vendor-free policy logic for recording-fake tests, not a usable Firebase JVM backend. JitPack resolves
the Android AAR automatically from the root artifact. Firebase is absent from published runtime dependencies.

Run `scripts/test.sh telemetry telemetry-firebase` or the full `scripts/test.sh`, and
`./gradlew :telemetry-firebase:publishToMavenLocal`. Tests never initialize a real Firebase project. Live crash upload,
release symbolication, startup/automatic capture behavior and the consuming app's Gradle plugin configuration must
also be verified in the app on a physical release device; they cannot be proven by host recording tests.
