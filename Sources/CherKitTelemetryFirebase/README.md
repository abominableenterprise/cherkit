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
3. On the main actor, call `FirebaseApp.configure()` once, then create and retain one backend/facade. Supply the
   build gate yourself: false for DEBUG, unit/UI tests and simulators. Load the user's diagnostics preference
   and restricted-session flag **before any operation or enabled assignment**:

   ```swift
   import FirebaseCore
   import CherKitTelemetry
   import CherKitTelemetryFirebase

   @MainActor
   func makeTelemetry(buildAllowed: Bool, userEnabled: Bool, restricted: Bool) -> Telemetry {
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
collections and instrumentation and requests deletion of unsent Crashlytics reports. `start` opens Crashlytics
and manual Performance collection; `stop` disables them, ends retained traces, clears owned custom keys and
requests deletion of unsent reports. SDK errors are best-effort; a failed start rolls collection back and the core
may retry. Firebase SDK APIs do not throw Objective-C exceptions as Swift errors; those cannot be recovered here.

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

**Firebase limitations:** collection and instrumentation settings persist across launches. In particular, Firebase
documents Crashlytics collection changes and post-configuration instrumentation changes as next-launch controls.
Do not treat a runtime switch as retracting reports already queued, sent, or logs already recorded. Crashlytics
has no API to clear breadcrumb history; clearing custom keys is best-effort. Do not mix normal and restricted
users in one Firebase process expecting retroactive removal of context. A restricted startup must begin with
privacy defaults and must not inherit an app-enabled automatic instrumentation setting. Apps with stronger
immediate revocation requirements must avoid initializing Firebase diagnostics for that process. The adapter
cannot override Firebase's startup behavior before the app initializes it.

## Optional automatic network monitoring

Keep it OFF: automatic instrumentation can capture URLs, including private query strings, and automatic app-start
metrics. If a non-restricted app explicitly accepts that risk, use
`FirebaseTelemetryBackend(automaticInstrumentationEnabled: true)` and set the app's
`firebase_performance_instrumentation_enabled` startup flag to `YES` **before Firebase configuration**, in an
eligible release process only. Firebase persists runtime overrides, so changes may require a subsequent launch.
Do not enable it in restricted/debug/test/simulator processes. Audit URL contents and consent independently;
CherKit cannot sanitize Firebase's automatic requests. See
[Performance collection controls](https://firebase.google.com/docs/perf-mon/disable-sdk?platform=ios).

## Verification

Recording-fake tests cover closed gates, restricted sessions, clean errors, trace/attribute caps, lifecycle cleanup
and SDK failures. Run the full `scripts/test-mac.sh`, `scripts/test.sh` and `scripts/size-check` for a release change.
Firebase console delivery, dSYM symbolication and automatic networking require an app/project and physical device;
they are not exercised by library tests.
