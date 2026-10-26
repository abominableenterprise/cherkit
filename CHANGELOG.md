# Changelog

Developer-facing notes on CherKit for the apps that use it. Versions follow semver; releases are git tags `vX.Y.Z`
(SwiftPM resolves the tag, JitPack publishes the Kotlin modules with the tag as the version). Between releases,
changes collect as fragments in [changelog/](changelog/README.md).

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
