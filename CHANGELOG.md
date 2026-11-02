# Changelog

Developer-facing notes on CherKit for the apps that use it. Versions follow semver; releases are git tags `vX.Y.Z`
(SwiftPM resolves the tag, JitPack publishes the Kotlin modules with the tag as the version). Between releases,
changes collect as fragments in [changelog/](changelog/README.md).

## 0.8.0

Telemetry: crash reports, non-fatal errors, breadcrumbs and timings through a vendor-free facade with a Firebase
Crashlytics + Performance backend. Diagnostics are on by default with a user switch, off in debug/tests/simulators,
and errors-only in restricted sessions; never send IDs, free text or URLs. Apps must supply the core build gate and
apply persisted user/restricted settings before collection. CherKit modules can now report their own failures and
key decisions via the optional Core `TelemetrySink`, without depending on a telemetry vendor.

No breaking changes. Module sinks default to `nil`/`null` (silent); existing Kotlin binaries retain their old
signatures through hidden overloads. Diagnostics failures never interrupt app behavior.

### Telemetry (new, `CherKitTelemetry`, `:telemetry`)

- added: Sanitized non-fatal errors, breadcrumbs, spans/timings and crash-context keys, with shared Swift/Kotlin
  privacy fixtures. Invalid names and unsafe attribute values are dropped; restricted mode drops breadcrumbs,
  spans and keys. The core transports only error type/domain/code and filtered attributes, never raw errors,
  messages, causes or original stacks. Apps must choose static, non-PII names and values; shape validation cannot
  establish meaning ([privacy rules and usage](kotlin/telemetry/README.md)).
- added: Tiny vendor-free `TelemetrySink` contract in Core, implemented by the Telemetry facade for module wiring.
- added: Default no-op backend hooks, Swift `update(restricted:)` and Kotlin `updateRestricted(restricted: Boolean)`,
  synchronize errors-only sessions without requiring changes to existing backends. Every start/restart requires a
  successful restriction update; a failed update stops active collection.

### Telemetry Firebase (new, `CherKitTelemetryFirebase`, `:telemetry-firebase`)

- added: Optional Apple and Android Crashlytics backends for crashes/non-fatals, breadcrumbs and custom keys, plus
  privacy-gated Performance traces. Automatic network instrumentation defaults off. Restricted sessions permit
  errors only. Runtime switches cannot retract previously captured or uploaded data; review the adapter guides'
  startup defaults, SDK revocation limits and physical release-device verification requirements.
- added: iOS/macOS adapter supports Firebase 11.x through 13.x; Performance spans are iOS-only. Apps **must** call
  `try FirebaseTelemetryBackend.prepareForConfiguration()` before every `FirebaseApp.configure()` to reset
  persisted Performance overrides, and must not configure Firebase if reset fails. Crashlytics automatic uploads
  stay disabled; reports are explicitly submitted only after current build/user gates pass. See the
  [Apple setup guide](Sources/CherKitTelemetryFirebase/README.md) for required plist defaults, dSYM uploads and
  limitations of automatic Performance lifecycle capture.
- added: Android Firebase dependencies are **compileOnly**: the app supplies the Firebase BoM, SDKs and Gradle
  plugins. **Performance is OFF by default on Android**; opting in also permits automatic app-start/screen traces.
  Keep plugin network instrumentation disabled. See the [Android setup guide](kotlin/telemetry-firebase/README.md)
  for required manifest defaults, collection controls, R8/ProGuard mapping uploads and app-side setup.

### Sunset (`CherKitSunset`, `:sunset`)

- added: Optional telemetry sinks report config/store fetch, timeout and decode failures with stable
  `cherkit.sunset` codes 1–6, and `cherkit.sunset.decision` breadcrumbs with only the fixed decision kind.
  Fail-open behavior is unchanged. Shared domain/code tables and wiring:
  [Swift](Sources/CherKitSunset/README.md#optional-telemetry),
  [Kotlin](kotlin/sunset/README.md#optional-telemetry-kotlin).

### WhatsNew (`CherKitWhatsNew`, `:whatsnew`)

- added: Optional telemetry sinks report notes loading, decoding and locale fallback with stable
  `cherkit.whatsnew` codes 1–3, plus a `cherkit.whatsnew.shown` breadcrumb after pending notes are marked seen.
  No note text, file names, locales, versions or exception messages are sent. Shared domain/code tables and wiring:
  [Swift](Sources/CherKitWhatsNew/README.md#optional-swift-telemetry),
  [Kotlin](kotlin/whatsnew/README.md#optional-telemetry-kotlin).

### Review (`CherKitReview`, `:review`)

- added: Optional telemetry sinks emit `cherkit.review.prompted` only after the coordinator successfully records
  the prompt handoff, not as proof the store UI appeared. No version, interruption source or user data is sent.
  See [Swift](Sources/CherKitReview/README.md#optional-telemetry) and
  [Kotlin](kotlin/review/README.md#optional-telemetry) wiring; Play failures are reported by Review Compose below.

### Review Compose (`:review-compose`)

- added: Optional sinks on the Compose, Play and platform-free launch APIs report `cherkit.review` code 1 for
  request/manager creation failure and code 2 for launch failure, without attributes or duplicate reports.
  Cancellation and skipped/non-Play installs are not failures. Pass the same sink to the coordinator for the
  prompt breadcrumb ([domain/code table and wiring](kotlin/review-compose/README.md#optional-telemetry)).

### Update Compose (`:update-compose`)

- added: Optional sinks report `cherkit.update` check/start/completion failures and fixed
  `cherkit.update.available` / `cherkit.update.downloaded` breadcrumbs, without exception text or private data.
  Play integer error codes pass through unchanged where available; cancellation is not a failure. See the
  [domain/code table and wiring](kotlin/update-compose/README.md#telemetry-optional) for codes and sink precedence.

### Build

- changed: Contributors only: `scripts/setup-linux.sh` restores Swift and the JDK + Android SDK from the VM's
  task-cache, saving fresh installs instead of downloading every run. A failed Gradle warm-up now warns, skips
  saving the Gradle cache and exits successfully; the test run resolves dependencies itself. No app behavior change.

## 0.7.0

No breaking changes; both new parameters default to `null` (the old behavior), and Kotlin keeps the 0.6.0
signatures as hidden overloads so existing binaries still link. No behavior change for apps that don't opt in.
What's new (Android only):

- **`WhatsNewPrompt(onLaunchCheckComplete = { hasNotes -> ... })`**: called once per process when the launch check
  of the "What's new" notes is done, with whether there are notes to show, also while the sheet is `suppressed`
  (see WhatsNew Compose).
- **`SunsetPrompt(onCheckingChange = { checking -> ... })`**: reports `true` while any Sunset check runs (launch,
  re-check on return from the background, quick retry) and `false` when it has finished (see Sunset Compose).
- **Cold-start ordering for Play's update prompt**: the recipe now keeps a second process-wide flag,
  `LaunchGate.whatsNewSettled`, and gates `PlayUpdatePrompt` with
  `suppressed = !LaunchGate.sunsetSettled || sunsetKind != null || sunsetChecking || !LaunchGate.whatsNewSettled || whatsNewVisible`,
  so Play's sheet opens only after Sunset and "What's new", and never while a re-check may still find a hard block
  ([recipe](kotlin/whatsnew-compose/README.md#order-with-plays-in-app-update)).
- **Restart to install**: `PlayUpdateState.completeUpdate()` restarts the app process at once; flush or confirm
  pending local writes first (see Update Compose).

### Sunset Compose (`:sunset-compose`)

- added: `SunsetPrompt(checker = ...)` takes an optional `onCheckingChange: ((Boolean) -> Unit)? = null`. It reports
  `true` when any check starts (the launch check, the re-check on return from the background, the quick retry after
  a failed fetch) and `false` when it has finished, with any outcome. Any `onPromptChange` / `onVisibilityChange`
  for that result runs before the `false`. `SunsetChecker.disabled()` never reports `true`. Add `sunsetChecking` to
  the gate of Play's update sheet so it cannot open while a re-check may still find a hard block
  ([README](kotlin/sunset-compose/README.md#know-when-a-check-is-running)). The 0.6.0 signature stays as a hidden
  overload, so code compiled against it keeps working.

### Update Compose (`:update-compose`)

- changed: docs: `PlayUpdateState.completeUpdate()` ("Restart to install") restarts the app process at once. Apps
  that queue local writes (e.g. an offline database or a sync queue) should flush or confirm them first, or disable
  or delay the restart button until nothing is pending. The cold-start recipe now also gates Play's sheet on
  Sunset's running checks and the "What's new" launch check:
  `suppressed = !LaunchGate.sunsetSettled || sunsetKind != null || sunsetChecking || !LaunchGate.whatsNewSettled || whatsNewVisible`
  ([Update Compose README](kotlin/update-compose/README.md#precedence-with-sunset)). No behavior change.

### WhatsNew Compose (`:whatsnew-compose`)

- added: `WhatsNewPrompt(checker = ...)` takes an optional
  `onLaunchCheckComplete: ((hasNotes: Boolean) -> Unit)? = null`. It is called once per process, right after the
  launch `onLaunch` has returned (same once-per-process guard), with whether there are notes to show (`false` when
  nothing is new or `onLaunch` failed open). It still comes while `suppressed = true`: the check runs, only the
  sheet waits. With notes and the sheet not suppressed, the sheet's `onVisibilityChange(true)` runs first, then
  this, in the same frame. Recomposition and rotation never call it again. Use it like Sunset's
  `onFirstCheckComplete`, e.g. to keep Play's update sheet back with
  `suppressed = ... || !LaunchGate.whatsNewSettled || whatsNewVisible`
  ([recipe](kotlin/whatsnew-compose/README.md#order-with-plays-in-app-update)). The 0.6.0 signature stays as a
  hidden overload, so code compiled against it keeps working.

## 0.6.0

No breaking changes; every new parameter defaults to the old behavior, and Kotlin keeps the old signatures as hidden
deprecated overloads so existing binaries still link. One behavior change: on iOS, `.sunsetPrompt(checker:)` now
shows at most one soft prompt per session (see UI). What's new:

- **"Update available" prompts**: besides the hosted config's `latestVersion`, a soft update prompt can now come
  from:
  - iOS: the App Store lookup (opt in with `URLSessionSunsetLookupSource`, release builds only);
  - iOS: a "new test build" prompt for TestFlight installs, driven by the new `ios.testFlight` block in the hosted
    config, which your release pipeline writes;
  - Android: Google Play In-App Updates (the flexible flow) in the new `:update-compose` module.
- **The hard block is unchanged and always wins**: it still comes only from the config's `minimumVersion`, and the
  new prompts never open over it.
- **Soft prompts at most once per session**: on iOS, once the user answers a soft prompt, re-checks in the same
  process show no other one; Play's sheet starts at most once per process. Each TestFlight build prompts at most
  once.
- **Docs**: the cold-start recipes now keep `sunsetSettled` in a process-wide object (`LaunchGate`) instead of
  `rememberSaveable` (see Sunset Compose).

### Sunset (`CherKitSunset`, `:sunset`)

- added: update sources. Besides the config's `latestVersion`, a soft prompt can now come from the App Store lookup
  (iOS release builds, opt in with `SunsetChecker(..., lookup:)` and `URLSessionSunsetLookupSource`) and from a new
  optional `ios.testFlight` config block (`latestBuild`, `url`, `notBefore`, `message`) for TestFlight installs. The
  lookup offers the higher of the two versions, respects the store's `minimumOsVersion`, and links the config's
  `storeURL` (else the lookup's `trackViewUrl`). Hard blocks still come from the config's `minimumVersion` only.
  Rules and the release pipeline recipe for the TestFlight block: [Sunset README](kotlin/sunset/README.md#update-sources).
- added: new inputs with defaults that keep today's behavior: `channel` (`SunsetChannel.store` / `.testFlight` /
  `.other`; Kotlin `STORE` / `TEST_FLIGHT` / `OTHER`) and `installedBuild` on `SunsetChecker.check` and
  `SunsetDecider.decide`.
- added: `SunsetPrompt` gains `kind` (`SunsetUpdateKind.update` / `.testBuild`) and `latestBuild`;
  `SunsetTestBuildPromptRule` shows a test-build prompt at most once per build ("Not now" and "Update" both remember
  it, under its own key).
- added: new types `SunsetBuildNumber`, `SunsetTestFlightConfig`, `SunsetLookupResult`, `SunsetLookupSource`,
  `SunsetPromptAction`. Kotlin keeps the old `SunsetPrompt`, `SunsetPlatformConfig`, `SunsetChecker` and
  `SunsetDecider` signatures as hidden deprecated overloads, so existing binaries still link.
- added: iOS: `SunsetChannel.current()` detects the install channel (`.testFlight` from the `sandboxReceipt` App
  Store receipt, `.other` in the simulator, `.store` otherwise). A library cannot detect debug builds: pass `.other`
  there.
- added: iOS: `URLSessionSunsetLookupSource(bundle:locale:)` opts in to the App Store lookup in one line (the app's
  bundle id and the user's region, `country(for:)`). See the
  [CherKitSunset README](Sources/CherKitSunset/README.md#ios-app-store-lookup-and-testflight-builds).

### Sunset Compose (`:sunset-compose`)

- changed: docs: the cold-start recipe for `onFirstCheckComplete` now keeps `sunsetSettled` in a process-wide object,
  `object LaunchGate { var sunsetSettled by mutableStateOf(false) }`, instead of `rememberSaveable` (as recommended
  in 0.5.0). After the process is killed in the background and restored, `rememberSaveable` brings back a stale
  `true` before the new process's launch check has answered, so a sheet could open ahead of a hard block. Move the
  flag into an `object` (or your app's own singleton) and gate with `!LaunchGate.sunsetSettled || sunsetKind != null`.
  The same change is in the WhatsNew Compose README and the `SunsetPrompt` / `WhatsNewPrompt` KDoc
  ([recipe](kotlin/sunset-compose/README.md#know-when-the-launch-check-is-done)). No behavior change.

### Update Compose (new, `:update-compose`)

- added: new Android module `com.github.abominableenterprise.cherkit:update-compose`: Google Play In-App Updates
  (FLEXIBLE flow) with `PlayUpdatePrompt(...)`. It runs only on Play installs (Play testing tracks included;
  sideloads get nothing) and is off in debug builds. It starts at most once per session, waits while `suppressed` is
  true (pass it while the Sunset prompt, What's New or another prompt shows, and until Sunset's launch check has
  answered), and remembers "Not now" per `availableVersionCode`.
- added: a downloaded update sets `rememberPlayUpdateState().isDownloaded` (also after a later resume) and calls
  `onDownloaded`; `completeUpdate()` installs it ("Restart to install").
- added: `onVisibilityChange` reports Play's sheet, e.g. to
  `ReviewCoordinator.setInterruptionVisible(it, UpdateInterruption.SOURCE)` (`"update"`). Opt in: apps that don't
  add the module are unchanged. Setup and precedence with Sunset: [Update Compose README](kotlin/update-compose/README.md).

### UI (SwiftUI, `CherKitUI`)

- added: `.sunsetPrompt(checker:channel:)` passes the channel and `CFBundleVersion` to the check. A TestFlight
  test-build prompt is a soft alert titled "A new test build is available" whose **Update** opens TestFlight; each
  build prompts at most once ("Update" and "Not now" both remember it, under
  `SunsetTestBuildPromptRule.defaultMemoryKey`). Both the `checker:` and `decision:` forms apply it, and
  `onPromptChange` reports it as `.soft`. Recipe in the [CherKitUI README](Sources/CherKitUI/README.md#ios-app-store-and-testflight-updates).
- added: new `SunsetStrings` texts `testBuildTitle`, `testBuildMessage`, `testBuildUpdateHint` (with
  `softTitle(for:)`, `softUpdateHint(for:)`); `SunsetSoftPromptMemory` gains `testBuildKey`, `rememberedTestBuild`
  and `remember(_:on:version:)`, and `reset()` now also forgets the remembered test build.
- changed: `.sunsetPrompt(checker:)` shows at most one soft prompt per session. Once the user answers a soft prompt
  ("Update" or "Not now"), re-checks on later returns from the background show no other soft prompt until the app is
  launched again. A hard block still shows. Before, an "Update" tap on a release prompt that was not followed by an
  update could show the prompt again on the next re-check in the same process.

## 0.5.0

No breaking changes; every new parameter defaults to the old behavior. One behavior change: Swift's
`ReviewCoordinator.disabled` is now a fresh instance on each access (see Review). What's new:

- **Sunset first on a cold start**: `onFirstCheckComplete` on Sunset's checker prompt on both platforms
  (`.sunsetPrompt(checker:)` in CherKitUI, `SunsetPrompt(checker = ...)` in Sunset Compose) says when the launch
  check has answered, so What's New can wait for it with `suppressed = !sunsetSettled || sunsetKind != null`.
- **WhatsNew Compose**: `WhatsNewHistoryScreen(showTitle = true)` shows its own heading, and `onVisibilityChange` no
  longer restarts the review cooldown when you pass a new lambda on recomposition.
- **WhatsNew**: `AndroidWhatsNew.history(context)` for a Settings screen without a checker of its own.
- **Review**: Swift's `ReviewCoordinator.disabled` is a fresh instance per access, and the docs show how to base
  `isEligible` on state created later.

### Review (`CherKitReview`, `:review`)

- changed: Swift: `ReviewCoordinator.disabled` now returns a fresh, independent disabled coordinator on each access
  instead of one shared instance, so setting `isEligible` (or any other state) on it no longer leaks to other
  callers. The call site is unchanged (`isUITest ? .disabled : ReviewCoordinator()`); keep the coordinator you got
  for the app's lifetime (a `let` or `@State`) rather than reading `.disabled` again, and don't compare coordinators
  with `=== .disabled` (check `isDisabled` instead). This matches Kotlin's `ReviewCoordinator.disabled()`.
- changed: docs: how to base eligibility on state created after the coordinator (e.g. a ViewModel): pass a closure
  that reads it lazily (`isEligible = { eligibilityHolder.value }`) or assign `review.isEligible = { ... }` later
  (Kotlin and Swift). It is read on every check, so keep it cheap and main-thread safe. No behavior change.

### Sunset Compose (`:sunset-compose`)

- added: `SunsetPrompt(checker = ...)` takes an optional `onFirstCheckComplete: (() -> Unit)? = null`. It is called
  once per process when the launch check's first attempt has finished, whatever it found (up to date, soft, hard or
  a failed fetch, which fails open), and at once for `SunsetChecker.disabled()`. Any `onPromptChange` /
  `onVisibilityChange` call for that first decision runs before it. Later re-checks, recomposition and rotation never
  call it again. Use it to hold back the "What's new" sheet on a cold start until Sunset has answered:
  `suppressed = !sunsetSettled || sunsetKind != null`, with `sunsetSettled` in `rememberSaveable` (recipe in the
  [Sunset Compose README](kotlin/sunset-compose/README.md)). Code compiled against 0.4.0 still links.

### WhatsNew

- added: `AndroidWhatsNew.history(context, locales = AndroidWhatsNew.preferredLocales())` returns the same list as
  `AndroidWhatsNew.checker(context).history(locales)`, so a Settings screen needs no checker of its own. Read-only:
  it never appends to the install history or marks notes seen.

### WhatsNew Compose (`:whatsnew-compose`)

- added: `WhatsNewHistoryScreen` takes an optional `showTitle: Boolean = false`. With `true` it shows
  `strings.historyTitle` as a heading (for TalkBack too) at the top of the scrolling list, for screens without a
  title of their own. Still no top bar: navigation stays with the app. The default keeps today's layout; code
  compiled against 0.4.0 still links.
- fixed: `WhatsNewPrompt`'s `onVisibilityChange` no longer gets a false `false` then `true` when the caller passes a
  new lambda instance on recomposition while the sheet is showing (e.g. an inline
  `{ review.setInterruptionVisible(it, ReviewInterruption.WHATS_NEW) }`), which restarted the review cooldown. The
  `false` still goes to the callback that got the `true`.

### UI (SwiftUI, `CherKitUI`)

- added: `.sunsetPrompt(checker:)` takes an optional `onFirstCheckComplete: (() -> Void)?`. It runs once per process
  when the launch check's first attempt resolves, whatever the outcome (up to date, soft, hard, or a failed fetch
  that fails open after the checker's timeout), and always after `onPromptChange` / `onVisibilityChange` reported
  that first decision. `SunsetChecker.disabled` calls it on the first appear; re-checks on a return from the
  background and the quick retry after a failed check never call it again. Hold What's New until Sunset has decided
  with `.whatsNew(checker: whatsNew, suppressed: !sunsetSettled || sunsetKind != nil)` (recipe in the
  [CherKitUI README](Sources/CherKitUI/README.md)).

### Tooling (contributors only)

- changed: nothing changes for apps: after `scripts/setup-linux.sh`, a bare `./gradlew <task>` in a fresh shell
  finds the JDK by itself (it sources `${CHERKIT_TOOLS:-~/.cherkit-tools}/env.sh` when `JAVA_HOME` is unset).
  Without that file (macOS, JitPack, IDEs) `./gradlew` behaves as before.

## 0.4.0

No breaking changes; every new parameter defaults to the old behavior. What's new:

- **Review**, a new module for the store-review ("rate this app") prompt: the shared rules in `CherKitReview` /
  `:review`, the iOS prompt `.reviewPrompt` in CherKitUI and the Android Play In-App Review in the new
  `:review-compose`.
- **WhatsNew Compose** (`:whatsnew-compose`), the Android "What's new" sheet and version history.
- Hooks so the prompts never stack: `onPromptChange` on Sunset's prompts (both platforms; iOS also gets
  `onVisibilityChange`) and `onVisibilityChange` / `suppressed` on What's New (iOS, and in WhatsNew Compose).

### Review (`CherKitReview`, `:review`)

- added: new module Review (JitPack artifact `review`): the store-review rules, shared by both platforms. It has no
  runtime dependencies; the platform prompts live in CherKitUI (iOS) and Review Compose (Android).
- added: `ReviewCoordinator`: due on the 5th `recordSuccess()` (configurable) or an `arm()`; at most once per app
  version; held while the session is not eligible; `noteInterruption()` holds it for a 2-minute cooldown.
- added: `setInterruptionVisible(_:source:)`: while any of your modals (a set of sources) is visible the review never
  shows; hiding one starts the cooldown, so it never fires at once. Sunset's prompts hook in with
  `ReviewInterruption.sunsetPrompt` / `.sunsetBlock` (see the module README), What's New with
  `ReviewInterruption.whatsNew` (Swift) / `ReviewInterruption.WHATS_NEW` (Kotlin), both the string `"whatsNew"`.
- added: `shouldPrompt(isIdle:)`, then `markPrompted()` right before launching the platform review; `disabled` and
  `enabled: false` for debug and test builds.
- added: stores `UserDefaultsReviewStore` (Swift), `AndroidReview.store` over SharedPreferences (Kotlin) and
  `InMemoryReviewStore`, keys under `cherkit.review.*`.

### Review Compose (`:review-compose`)

Targets Android. Brings `review` and Play In-App Review (`com.google.android.play:review-ktx` 2.0.2); apps without
Play Services can keep using `:review` alone.

- added: `ReviewPromptEffect(coordinator, isIdle)`: on a resumed, idle screen it waits a settle delay (1.5 s by
  default), then launches the Play review when `shouldPrompt()` holds, checking again while the screen stays idle
  and starting over on every `ON_RESUME`.
- added: `PlayReview.launch(activity, coordinator, requirePlayInstall = true)` calls `markPrompted()` only after
  `requestReviewFlow()` succeeds, right before `launchReviewFlow()`; a failed request keeps the prompt due. Builds
  the Play Store did not install (sideloaded, emulator installs, other stores) are skipped without consuming
  anything; Play testing tracks install through `com.android.vending` and pass. Play errors never crash; the
  `ReviewLaunchResult` says what happened. Play gives no signal when its quota shows nothing, so that version counts
  as prompted.
- added: `ReviewInterruptionEffect(coordinator, source, visible)` reports your own dialogs, sheets and banners; a
  source that flickers never stays stuck, and every hide restarts the cooldown.

### Sunset Compose (`:sunset-compose`)

- added: `onPromptChange: (SunsetPromptKind?) -> Unit = {}` on both `SunsetPrompt` overloads reports which prompt is
  showing: `SOFT` (the "Update available" dialog), `HARD` (the "Update required" block) or `null`. Use it to report
  the hard block too, e.g. as `ReviewInterruption.SUNSET_BLOCK`, and to show Sunset before What's New.
  `onVisibilityChange` is unchanged (soft dialog only) and a trailing lambda still binds to it; code compiled
  against 0.3.0 still links.

### WhatsNew

- fixed: docs: the Android setup in `kotlin/whatsnew/README.md` no longer says to add the repo's `whatsnew/` folder
  as an assets `srcDir`. That put the files at the root of `assets/`, so `AndroidWhatsNew.checker(context)` found no
  notes. It now shows a Gradle `Sync` task into `build/generated/whatsnew-assets/whatsnew/`, with the parent folder
  added as a plain-path assets `srcDir` and `preBuild` depending on the sync (works on AGP 9). If your app followed
  the old advice, switch to this setup or copy the files to `src/main/assets/whatsnew/`.
- fixed: the `WhatsNewStore` docs point to the shipped stores (`UserDefaultsWhatsNewStore`, `KeyValueWhatsNewStore`,
  `AndroidWhatsNew.store(context)`) instead of saying they come later.

### WhatsNew Compose (`:whatsnew-compose`)

Targets Android (the JVM artifact carries just the strings). Brings `whatsnew`.

- added: new module WhatsNew Compose (JitPack artifact `whatsnew-compose`), the Compose counterpart of CherKitUI's
  WhatsNew views. `WhatsNewPrompt(checker = ...)` at the root calls `onLaunch` once per process, shows the "What's
  new" bottom sheet when there are notes and calls `markSeen()` only when the user dismisses it (Continue, back or
  swipe).
- added: `suppressed = true` makes the sheet wait. Pass `suppressed = sunsetKind != null`, fed from `SunsetPrompt`'s
  `onPromptChange`, so it never stacks with either Sunset prompt (see the module README for showing Sunset first).
  `onVisibilityChange` reports when it appears and goes away, e.g. to a review coordinator.
- added: `WhatsNewPrompt(entries, onDismiss)` shows entries you got yourself, like iOS's
  `.whatsNew(entries:onDismiss:)`.
- added: `WhatsNewHistoryScreen(entries)` lists every release for Settings with the "You're on this version" badge;
  `WhatsNewStrings` has the same English defaults as iOS.

### UI (SwiftUI, `CherKitUI`)

- added: the iOS review prompt (CherKitUI now links `CherKitReview`). `.reviewPrompt(coordinator:isIdle:settleDelay:)`
  on a main screen waits until the screen has been idle for a settle delay (1.5 s by default), then calls
  `markPrompted()` right before StoreKit's `requestReview`. It runs only while the scene is active and starts over
  when the app comes back. The version counts as prompted even if StoreKit's quota shows nothing. Pass
  `ReviewCoordinator.disabled` in debug builds and test harnesses.
- added: `.reviewInterruption(isPresented:source:coordinator:)` reports any of your sheets or alerts, so the review
  never shows over them and waits a cooldown after them.
- added: `.sunsetPrompt` (both forms) has two optional callbacks: `onVisibilityChange` (the soft alert) and
  `onPromptChange` (`SunsetPromptKind.soft`, `.hard` or `nil`, like Android). Report both prompts with
  `review.setSunsetPromptKind($0)`.
- added: `.whatsNew` (both forms) has an optional `onVisibilityChange` (the sheet) and `suppressed`, which holds the
  sheet back while it is `true`, e.g. `suppressed: sunsetKind != nil` from `.sunsetPrompt(onPromptChange:)`.
  Report the sheet with `ReviewInterruption.whatsNew`.

## 0.3.0

No breaking changes. One behavior change: a failed Sunset check is retried once and doesn't use up
`minimumInterval` (see Sunset).

### Sunset

- fixed: `.sunsetPrompt(checker:)` (CherKitUI) and `SunsetPrompt(checker = ...)` (sunset-compose) no longer miss a
  hard block until the next cold start when a check fails (fetch dropped or timed out, or a body that doesn't parse,
  with no usable cached config, e.g. on a fresh install). A failed check no longer counts toward `minimumInterval`;
  it is retried once about 3 seconds later, and if that fails too, the next return from the background re-checks
  whatever the interval. After a success the interval applies again.
- added: `SunsetChecker.isDisabled` is public and read-only on both platforms, `true` only for
  `SunsetChecker.disabled` (Swift) / `SunsetChecker.disabled()` (Kotlin), so app tests can assert they wired the
  switched-off checker.
- added: `SunsetChecker.checkReportingFailure(installedVersion, osVersion, locale)` (Kotlin) returns a
  `SunsetCheckOutcome(decision, failed)`, where `failed` means no usable config at all. `check(...)` is unchanged and
  still fails open; most apps keep calling it.
- added: `SunsetCheckScheduler` (Kotlin, common code), the pure rule for when to check, the same as CherKitUI's:
  launch, return from the background, skip while a check is running, `minimumInterval`, and the quick retry
  (`checkFinished(failed)` returns whether to schedule it; `shouldRetry(now)`, `failuresInARow`, `isRetryPending`).

### Sunset Compose (`:sunset-compose`)

Targets Android (a JVM artifact is published for tooling).

- added: `SunsetPrompt(checker = ..., minimumInterval = 1.hours)` runs the Sunset check and shows the prompts, the
  Android twin of CherKitUI's `.sunsetPrompt(checker:)`. It calls `AndroidSunset.check(checker, context)` once per
  process at first composition, again only on a real return from the background (Lifecycle `ON_STOP`, then
  `ON_RESUME`; not the first `ON_RESUME`, `ON_PAUSE` → `ON_RESUME` or a rotation), never while a check is running,
  and at most once per `minimumInterval`. The decision drives the same soft dialog and hard block;
  `SunsetChecker.disabled()` shows nothing. No new dependency: it uses `androidx.lifecycle:lifecycle-runtime-compose`,
  which Compose `ui` already brings.
- added: `onVisibilityChange: (Boolean) -> Unit = {}` on both `SunsetPrompt` overloads, called with `true` when the
  soft "Update available" dialog appears and `false` when it goes away (the hard block is not reported). Apps that
  run their own checks can skip them while it is open instead of copying `SunsetSoftPromptRule` and the stored
  "Not now" version. The `SunsetPrompt(decision, ...)` overload stays source and binary compatible.
- changed: the module README and the `SunsetPrompt` KDoc show `SunsetPrompt(checker = ...)` and
  `AndroidSunset.check(checker, context)` instead of the deprecated `checker.check(context)` extension.

### UI (SwiftUI, `CherKitUI`)

- added: `tintLabel: Color?` on both `.sunsetPrompt` modifiers and on `SunsetUpdateView` sets the label color of the
  hard "Update required" button on the `tint` fill. With `nil` (the default) the label is now black or white from
  the tint's WCAG relative luminance in the current light or dark mode (white while it keeps 3:1 contrast, black on
  brighter tints), so a bright tint such as a light dark-mode accent no longer gets an unreadable white label.
  Without `tint` nothing changes.

## 0.2.0

No breaking changes. New Android module `sunset-compose`; one Kotlin extension is deprecated (see Sunset).

### All modules

- fixed: every project gets its Maven group and version before any module is configured, and publishing fails if a
  generated Gradle `.module` file names coordinates other than `com.github.abominableenterprise.cherkit:<module>`
  (component, `available-at` references, capabilities). The `.module` files JitPack serves still name the component
  `com.github.abominableenterprise:cherkit` because JitPack rewrites that block; Gradle ignores it and resolves
  `sunset`, `whatsnew` and `analytics` together without capability conflicts (see the README's Install section).

### Sunset

- added: `AndroidSunset.check(checker, context, locale = AndroidSunset.preferredLocales())` runs the launch check for
  the running app (`versionName`, `Build.VERSION.SDK_INT`, the user's locales) with no import beyond `AndroidSunset`.
- deprecated: the `SunsetChecker.check(context, locale)` extension (its import
  `com.abominableenterprise.cherkit.sunset.check` was easy to confuse with Kotlin's `check(Boolean)`). It still works;
  the IDE quick fix replaces `checker.check(context)` with `AndroidSunset.check(checker, context)`.
- added: `SunsetChecker.disabled` (Swift) / `SunsetChecker.disabled()` (Kotlin), a checker that always answers
  `upToDate` without touching the network or the cache. Use it in UI tests and on simulators or emulators, e.g.
  `let checker = isUITest ? .disabled : SunsetChecker(url: url)`.
- added: `SunsetPrompt.latestVersion` (`AppVersion?`, Swift and Kotlin): the config's `latestVersion` for the
  platform, or `nil` / `null` when the block has none. `SunsetDecider` (and so `SunsetChecker`) fills it in; the
  parameter defaults to `nil`, so existing code keeps compiling.

### Sunset Compose (Android, `:sunset-compose`)

- added: new Android-only module `com.github.abominableenterprise.cherkit:sunset-compose` (Jetpack Compose,
  Material 3; brings `:sunset`), the Android counterpart of CherKitUI's `.sunsetPrompt`.
  `SunsetPrompt(decision, onDecisionHandled)` shows a dismissible "Update available" dialog for `SoftUpdate` and a
  full-screen "Update required" block that back cannot dismiss for `HardUpdate`. See
  [kotlin/sunset-compose/README.md](kotlin/sunset-compose/README.md).
- "Not now" is remembered in SharedPreferences for `prompt.latestVersion` (else the installed version) by
  `SunsetSoftPromptMemory`; `SunsetSoftPromptRule` is the same rule as on iOS (`fixtures/sunset/soft-prompt.json`).
- `SunsetStrings` has the iOS English defaults, all overridable. `SunsetUpdateScreen` and `SunsetSoftPromptDialog`
  can be used on their own.
- Requirements: androidx Compose `runtime` / `ui` / `foundation` 1.9.5+ and `material3` 1.4.0+ (floors; newer
  versions in the app win), `compileSdk` 35+ and the Kotlin Compose compiler plugin in your app.

### UI (SwiftUI, `CherKitUI`)

- added: `.sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:)` runs the
  Sunset check for you: once per process at launch, again only when the app returns from the background
  (`.background` then `.active`, not `.inactive` then `.active`), never while a check is running, and at most once
  per `minimumInterval` (default 1 hour). It never fetches twice at cold launch, so it replaces your own
  `scenePhase` / `didBecomeActive` re-check. Pass `SunsetChecker.disabled` in UI tests. `.sunsetPrompt(decision:)`
  is unchanged.
- added: `tint: Color?` on both `.sunsetPrompt` modifiers and on `SunsetUpdateView` colors the hard "Update
  required" button and accents. On iOS the block's own window (which does not inherit the app's tint and so showed
  system blue) gets that `tintColor` too. Defaults to `nil` (unchanged); the soft alert keeps the app's tint.
- changed: `.sunsetPrompt(decision:)` remembers "Not now" for `prompt.latestVersion`, so you no longer need to pass
  `latestVersion:`. When given, the parameter still wins; with neither, the installed app version is used as before.

## 0.1.0

First release.

### All modules

- added: Swift package products `CherKitCore`, `CherKitAnalytics`, `CherKitSunset`, `CherKitWhatsNew` and
  `CherKitUI` (iOS 16+ / macOS 13+), and Kotlin Multiplatform modules `:core`, `:analytics`, `:sunset` and
  `:whatsnew` (Android + JVM, packages `com.abominableenterprise.cherkit.<module>`). Each module has its own entry
  point; there is no central configure call.
- added: the Kotlin modules are published through JitPack from release tags. Add `maven("https://jitpack.io")` to
  your repositories and depend on `com.github.abominableenterprise.cherkit:<module>:vX.Y.Z`, where `<module>` is
  `analytics`, `sunset`, `whatsnew` or `core`; Gradle picks the Android (AAR) or JVM variant itself. The README's
  Install section covers SwiftPM (`https://github.com/abominableenterprise/cherkit`, `from: "0.1.0"`) and Gradle.
- changed: CherKit is MIT-licensed.
- changed: every library product, including `CherKitUI`, is verified to build on macOS and for the iOS Simulator,
  and the Swift tests pass on macOS and on an iPhone simulator (`scripts/test-mac.sh`).

### Core

- added: `AppVersion` (`CherKitCore` / `:core`): parse store-style versions with `AppVersion("1.2.3")` (Swift) or
  `AppVersion.parse("1.2.3")` (Kotlin) and compare them with `<`, `==`, `compareTo`. Whitespace, a leading `v` and a
  ` (45)`, `+45` or `-beta` suffix are ignored; missing components count as 0 (`1.2 == 1.2.0`); malformed input
  returns `nil` / `null`.
- `CherKitSunset` and `CherKitWhatsNew` (and Kotlin `:sunset`, `:whatsnew`, via `api`) depend on Core.

### Analytics

- added: `Analytics` (Swift `CherKitAnalytics`, Kotlin `com.abominableenterprise.cherkit.analytics`): a typed,
  privacy-first facade. Define your allowlist as a type conforming to `AnalyticsEvent` (a Swift `String` enum, or a
  Kotlin enum overriding `eventName`) and call `track(event)`, which returns whether the event was sent.
- Events are sent only when `buildEnabled` (passed at init; pass `false` in debug, tests and emulators),
  `consentGranted` (default `false`) and `sessionActive` (never on a kid's device) are all true, and the name matches
  `^[a-z][a-z0-9_]{0,63}$`. Event properties are deliberately not supported.
- Provider adapters implement `AnalyticsBackend` (`start`, `capture(name)`, `stop`). The backend starts when the gate
  opens and stops whenever it closes; a `start()` that throws is followed at once by `stop()` to roll back a partial
  opt-in.
- `AnalyticsTestHost.isRunningTests` detects XCTest (Swift) or JUnit / AndroidX Test (Kotlin) so tests stay offline.
- added: PostHog backends. Swift (iOS and macOS): `PostHogAnalyticsBackend(apiKey:host:)`, e.g.
  `Analytics(backend: PostHogAnalyticsBackend(apiKey: key), buildEnabled: !isDebug && !AnalyticsTestHost.isRunningTests)`.
  Android: `PostHogAnalyticsBackend(context, apiKey, host = "https://us.i.posthog.com")`, passed to `Analytics(...)`.
  `host` defaults to `https://us.i.posthog.com` on both.
- Both use a hardened PostHog config: no autocapture (lifecycle, deep links, screen views, push, errors), no
  swizzling, no feature flags, no default person properties or person profiles, no session replay, no surveys. Only
  event names are sent; they never call `identify`, person properties, groups or aliases. A blank API key makes
  `start()` throw, which `Analytics` swallows, so analytics just stays off. On Android the SDK client is created on
  the first `start()` and opted out, closed and dropped on `stop()`.
- The API key is your PostHog project's public client key. Do not enable autocapture, session replay, surveys or
  feature flags in the PostHog project.
- The Swift backend owns `PostHogSDK.shared`: remove any other PostHog setup from your app when you adopt it.
- changed: dependencies. `CherKitAnalytics` depends on posthog-ios 3.71.4 on Apple platforms, and the `:analytics`
  Android artifact on `com.posthog:posthog-android` 3.61.1 (the JVM target does not); apps that already use PostHog
  must use a compatible version.

### Sunset

- added: hosted config format for version sunsetting (`schemaVersion` 1, one `ios` / `android` block with
  `minimumVersion`, `latestVersion`, `latestMinimumOS`, `storeURL`, `message`), documented in
  [kotlin/sunset/README.md](kotlin/sunset/README.md).
- added: `SunsetChecker`, the one call at launch, returns `upToDate`, `softUpdate` or `hardUpdate` with the store URL
  and the localized message (`de-DE` -> `de` -> `en`). It fetches the hosted config (default timeout 5 s), falls back
  to the last good cached config, and never throws. Swift: `await SunsetChecker(url:).check()` reads
  `CFBundleShortVersionString` and the OS version, or call `check(installedVersion:osVersion:locale:)`. Kotlin:
  `suspend check(installedVersion, osVersion, locale)`; on Android `AndroidSunset.checker(context) { yourHttpGet() }.check(context)`
  reads `versionName` and `Build.VERSION.SDK_INT`.
- Everything fails open: a missing, malformed, unreachable or unknown config, or a device whose OS is below
  `latestMinimumOS`, gets no prompt. Only a fetched config that parses is cached; a malformed or non-2xx response
  never replaces the last good one.
- The building blocks are public too: `SunsetConfig.parse`,
  `SunsetDecider.decide(config, platform, appVersion, osVersion, languages)`, and
  `SunsetConfigChoice.choose(fetched, cached)` (a fresh fetch, else the cached last good config, else none), with the
  `SunsetConfigSource` and `SunsetConfigCache` protocols.
- Swift sources and caches: `URLSessionSunsetConfigSource` (ignores the HTTP cache; only 2xx bodies count) and
  `UserDefaultsSunsetConfigCache` (injectable `UserDefaults` suite and key).
- Kotlin sources and caches: `FetchSunsetConfigSource` over your own `SunsetFetch` call (no HTTP client dependency),
  and `KeyValueSunsetConfigCache` over a `SunsetKeyValueStore`; Android ships `SharedPreferencesSunsetStore`.
  `:sunset` depends on `kotlinx-coroutines-core`.

### WhatsNew

- added: notes file format `whatsnew/<version>.json` (`version`, optional `date`, `show`, `notes` per locale), read
  and validated by `NotesFile.read` (Swift `CherKitWhatsNew`, Kotlin `:whatsnew`) into `ReleaseNotes` plus a list of
  readable `NotesFileError`s.
- added: `WhatsNewChecker`, the one call at launch: `onLaunch(installedVersion:locale:)` returns the notes to show
  (version, date, localized bullets; newest first) and updates the install history; call `markSeen()` after showing
  them, so a crash before display keeps the notes. `history(locale:)` lists every bundled release with an
  `isInstalled` flag.
- Swift: `BundleReleaseNotesSource` reads `whatsnew/*.json` from a `Bundle` (default `.main`),
  `UserDefaultsWhatsNewStore` keeps state (injectable suite and key prefix), and
  `WhatsNewChecker.installedVersion(bundle:)` reads `CFBundleShortVersionString`.
- Kotlin: `LoadedReleaseNotesSource` wraps an app-supplied (file name, text) loader, and `KeyValueWhatsNewStore` sits
  on a tiny `KeyValueStorage`; on Android `AndroidWhatsNew` reads `assets/whatsnew/`, SharedPreferences and
  `versionName`.
- `ReleaseNotesLoader.load` (and `ReleaseNotesSource.releaseNotes()`) skips invalid notes files and reports them to an
  optional `onSkip`. Corrupt stored values count as absent. Nothing throws to the app.
- The building blocks are public too: `LaunchPlan.make` (every `show: true` version newer than the last seen, newest
  first; nothing on first launch or downgrade), `InstallHistory.updated`, `LocaleFallback.resolve`
  (`pt-BR` -> `pt` -> `en`), and the `WhatsNewStore` and `ReleaseNotesSource` protocols.
- For the release step: `StoreText.make` (store "What's New" text, Play's 500-character limit, never truncated) and
  `SubmissionGate.check` (ok / missing / invalid).

### UI (SwiftUI, `CherKitUI`)

- added: `.sunsetPrompt(decision: $decision, latestVersion:)` shows a `SunsetDecision`: a dismissible "Update
  available" alert with "Update" and "Not now" for `softUpdate`, and a full-screen "Update required" block with no
  way to dismiss it for `hardUpdate` (on iOS a window above the app's own windows, so it also covers open sheets; an
  overlay on macOS). `upToDate` shows nothing. The views never fetch; pass in the decision from `SunsetChecker` or
  `SunsetDecider`.
- "Not now" is remembered per `latestVersion` in `UserDefaults` (`SunsetSoftPromptMemory(defaults:key:)`,
  injectable), so the soft prompt does not return on every launch for the same version. The rule is
  `SunsetSoftPromptRule.shouldShow(_:version:dismissedVersion:)`.
- `SunsetUpdateView(prompt:strings:appIcon:)` is the hard block on its own: optional app icon, the config's custom
  message or a default line, and an "Update" button that opens the store URL. It scrolls at large Dynamic Type sizes.
- added: `WhatsNewSheet(entries:strings:onContinue:)` shows the notes from `WhatsNewChecker.onLaunch` (version, date,
  bullets; newest first, one section per release) with a "Continue" button. Local only: no network, no spinner.
- `.whatsNew(checker:)` on your root view calls `onLaunch` on first appear, presents the sheet when there are notes,
  and calls `markSeen()` only once the sheet is dismissed. `.whatsNew(entries:onDismiss:)` presents entries you got
  yourself and calls `onDismiss` once on dismissal.
- `WhatsNewHistoryView(entries:strings:)` lists `WhatsNewChecker.history()` with a "You're on this version" badge on
  the installed release; push it from Settings (it sets its own navigation title).
- All text is English by default and overridable through `SunsetStrings` and `WhatsNewStrings(...)`. The views
  support Dynamic Type up to the accessibility sizes, VoiceOver, small screens and light/dark mode.
- Android UI is not included in this release.
