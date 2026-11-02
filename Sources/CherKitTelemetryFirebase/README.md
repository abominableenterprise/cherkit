# CherKit Telemetry Firebase (Apple)

Optional `CherKitTelemetryFirebase` product: Crashlytics on iOS 16+/macOS 13+ and manual Firebase Performance
traces on iOS. Performance is unavailable on macOS; those calls are no-ops. The
[vendor-free core](../../kotlin/telemetry/README.md) remains usable without this product. Firebase products are
Apple-only target dependencies, like PostHog; Linux compiles the adapter's policy and fake tests without Firebase.
The SDK range is `11.0.0..<14.0.0` (latest checked: 13.1.1). Newer Firebase releases may require a newer Xcode.

## App setup (required)

1. Register the app in the [Firebase console](https://console.firebase.google.com/). Add `GoogleService-Info.plist`
   to the app target (not this library); ensure its bundle ID matches. Link `CherKitTelemetry`,
   `CherKitTelemetryFirebase` and the app's `FirebaseCore` product. Do **not** link Firebase Analytics just for this
   adapter: Analytics can automatically add Crashlytics breadcrumbs outside CherKit's restricted gate.
2. In the **app's Info.plist**, set these Boolean defaults before Firebase loads:

   | Key | Value |
   | --- | --- |
   | `FirebaseCrashlyticsCollectionEnabled` | `NO` |
   | `firebase_performance_collection_enabled` | `NO` |
   | `firebase_performance_instrumentation_enabled` | `NO` |

   These are startup privacy defaults, not optional tuning. Do not use the Performance
   `firebase_performance_collection_deactivated` flag: it permanently disables manual traces as well.
3. On the main actor, call `try FirebaseTelemetryBackend.prepareForConfiguration()` **before every**
   `FirebaseApp.configure()`, including denied/restricted/debug launches. It resets persisted Performance collection
   and instrumentation overrides before Firebase can start its automatic hooks. Do not configure Firebase if reset
   fails. Plist defaults alone cannot override the previous process's runtime settings. Then create and retain one
   backend/facade. Supply the build gate yourself: false for DEBUG, unit/UI tests and simulators. Load the user's
   diagnostics preference and restricted-session flag **before any operation or enabled assignment**:

   ```swift
   import FirebaseCore
   import CherKitTelemetry
   import CherKitTelemetryFirebase

   @MainActor
   func makeTelemetry(buildAllowed: Bool, userEnabled: Bool, restricted: Bool) throws -> Telemetry {
       try FirebaseTelemetryBackend.prepareForConfiguration() // every launch, BEFORE configure
       FirebaseApp.configure() // once, owned by the app
       let backend = FirebaseTelemetryBackend()
       let telemetry = Telemetry(backend: backend, buildEnabled: buildAllowed)
       telemetry.restricted = restricted
       telemetry.enabled = userEnabled
       return telemetry
   }
   ```

   Use `#if DEBUG`, `#if targetEnvironment(simulator)` and your app's unit/UI-test launch flag to calculate
   `buildAllowed`. The core does not detect these automatically. Do not construct the live backend in unit tests;
   the adapter tests inject a recording SDK and never configure Firebase.
4. Enable **Debug Information Format = DWARF with dSYM File** for archive/release builds. Add the Crashlytics
   Run Script phase last in the app target:

   ```sh
   "${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run"
   ```

   Add these Input Files (including the debug dylib when `ENABLE_DEBUG_DYLIB=YES`):

   ```text
   ${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}
   ${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Resources/DWARF/${TARGET_NAME}
   ${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Info.plist
   $(TARGET_BUILD_DIR)/$(UNLOCALIZED_RESOURCES_FOLDER_PATH)/GoogleService-Info.plist
   $(TARGET_BUILD_DIR)/$(EXECUTABLE_PATH)
   ${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Resources/DWARF/${TARGET_NAME}.debug.dylib
   ```

   See Firebase's [dSYM setup](https://firebase.google.com/docs/crashlytics/get-started?platform=ios#set-up-dsym-uploading).
   Verify a symbolicated test crash from an eligible **physical release device**, disconnected from the debugger.

## Privacy and lifecycle

Use the backend through `Telemetry`, not as a bypass around its gates. Construction explicitly disables both
collections and instrumentation, without making Crashlytics' one-shot send/delete decision before consent is known.
`start` keeps **automatic Crashlytics collection disabled** and explicitly calls `sendUnsentReports()` only after the
core's current build/user gates pass. It never persists upload consent into the next launch. Pending reports are
submitted as a batch; current-session non-fatals/crashes normally await a later consenting launch. `start` also
opens Performance collection. `stop` disables it, ends retained traces, clears owned custom keys and requests
unsent-report deletion. Firebase's send/delete decision is one-shot per process: deleting after an authorized send
cannot retract it. SDK errors are best-effort; a failed start rolls collection back and the core may retry.
Firebase SDK APIs do not throw Objective-C exceptions as Swift errors; those cannot be recovered here.

Restricted mode reports crashes/non-fatals only: no new logs, keys, manual traces or automatic Performance
collection. The additive `TelemetryBackend.update(restricted:)` hook synchronizes this even before the first error;
its default implementation preserves existing backends. All non-fatal attributes are deliberately dropped, including
in restricted sessions. Errors become a new `NSError` with domain (or type fallback), code and **empty userInfo**.
No original error, localized description, message, cause, arbitrary object, URL or user identifier is sent. There is
no safe-message API and no original throw-site stack in the current core; Crashlytics captures its recording stack
and native crash stacks. The adapter never sets a Firebase user ID.

Breadcrumbs contain only the validated static event name (attributes are dropped). App-defined keys and values
are shape-validated again; never supply names, identifiers, emails, free text, screen contents, URLs or money
amounts even if a value fits that shape. Validation cannot infer meaning. Global keys are capped at 64. Trace
attributes are capped at five, including a reserved `status` (`ok`/`error`); four app keys are selected in sorted
order. Firebase attribute keys accept only lowercase letters/underscores, at most 40 bytes, excluding reserved
prefixes; unsupported keys are dropped rather than renamed. Values use the core's primitive/AppVersion allowlist
and Firebase's 100-byte cap. Updates to existing keys remain possible at the cap.

**Firebase limitations:** Performance collection/instrumentation settings persist across launches; the mandatory
pre-configuration reset overwrites them before Firebase starts. Crashlytics automatic collection stays false;
explicit submission is a per-process authorization, not persisted collection consent. An app migrating from code
that previously persisted Crashlytics collection `true` must first disable it in a consenting process and relaunch;
the plist cannot override that old setting and post-configure initialization cannot make that migration launch safe.
Do not initialize Crashlytics on a denied migration launch with a prior enabled override.

Do not treat a runtime switch as retracting reports already queued, sent, or logs already recorded. Crashlytics
has no API to clear breadcrumb history; clearing custom keys is best-effort. Do not mix normal and restricted
users in one Firebase process expecting retroactive removal of context. Automatic collection disabled still permits
local crash capture; a later explicit submission cannot filter individual reports by their original consent state.
Apps requiring no local diagnostics capture or immediate cancellation of queued uploads must avoid initializing
Firebase diagnostics. Startup resets protect new collection, not previously authorized transport work.

Performance collection is **not strictly manual-only**: even with instrumentation false, Firebase can emit app-start,
foreground/background, session and gauge metadata while collection is enabled. Only enable this backend when
that vendor behavior is acceptable; restricted and denied sessions keep Performance collection off. See the
[SDK collection controls](https://github.com/firebase/firebase-ios-sdk/blob/13.1.1/FirebasePerformance/Sources/Public/FirebasePerformance/FIRPerformance.h)
and [automatic lifecycle tracker](https://github.com/firebase/firebase-ios-sdk/blob/13.1.1/FirebasePerformance/Sources/AppActivity/FPRAppActivityTracker.m).

## Optional automatic network monitoring

Keep it OFF: automatic instrumentation can capture URLs, including private query strings, and automatic app-start
metrics. If a non-restricted app explicitly accepts that risk, use
`FirebaseTelemetryBackend(automaticInstrumentationEnabled: true)` in an eligible release process only. Keep the
plist defaults `NO` and always call `prepareForConfiguration()` before configuring Firebase, even after a previous
opted-in session. The backend enables instrumentation only when the current gates permit; the next launch resets
it before configuration. Once installed, Firebase's hooks cannot be unswizzled in-process; closing collection is
not a promise to undo instrumentation. Do not enable it in restricted/debug/test/simulator processes. Audit URL
contents and consent independently;
CherKit cannot sanitize Firebase's automatic requests. See
[Performance collection controls](https://firebase.google.com/docs/perf-mon/disable-sdk?platform=ios).

## Verification

Recording-fake tests cover closed gates, restricted sessions, clean errors, trace/attribute caps, lifecycle cleanup
and SDK failures. Run the full `scripts/test-mac.sh`, `scripts/test.sh` and `scripts/size-check` for a release change.
Firebase console delivery, dSYM symbolication and automatic networking require an app/project and physical device;
they are not exercised by library tests.
