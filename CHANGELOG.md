# Changelog

Developer-facing notes on CherKit for the apps that use it. Versions follow semver; releases are git tags `vX.Y.Z`
(SwiftPM resolves the tag, JitPack publishes the Kotlin modules with the tag as the version). Between releases,
changes collect as fragments in [changelog/](changelog/README.md).

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
