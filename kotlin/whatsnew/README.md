# CherKit WhatsNew

"What's new" notes after an update, plus the history of app versions installed on the device. Offline only: the notes
are bundled in the app, never fetched. Versions use CherKitCore `AppVersion`; anything unreadable fails open.

## Notes file

Each app repo has one file per release, `whatsnew/<version>.json`:

```json
{ "version": "1.4.0", "date": "2026-11-02", "show": true,
  "notes": { "en": ["You can now export to CSV.", "Fixed a crash on launch."], "de": ["Export als CSV."] } }
```

- `version` (required) must match the file name (`1.4.json` may hold `1.4.0`). `date` is optional and shown as-is.
- `show` defaults to `true`; `false` marks a fix-only release: no popup, store text "Bug fixes and improvements.".
- `notes`: 1 to 4 non-empty bullets per locale; `en` is required when `show` is true. Translations are optional
  (missing ones fall back `pt-BR` -> `pt` -> `en`). Each locale's store text must fit Play's 500 characters.
- Unknown fields are ignored.

## API (same names on both platforms)

| Purpose | Swift (`CherKitWhatsNew`) | Kotlin (`:whatsnew`) |
| --- | --- | --- |
| Parse + validate a file | `NotesFile.read(data, fileName:) -> NotesFileResult` | `NotesFile.read(data, fileName): NotesFileResult` |
| Parsed notes | `ReleaseNotes`, `.bullets(preferredLocales:)` | `ReleaseNotes`, `.bullets(preferredLocales)` |
| Errors | `NotesFileError(code: NotesFileErrorCode, message:)` | `NotesFileError(code: NotesFileErrorCode, message)` |
| On launch | `LaunchPlan.make(notes:installed:lastSeen:)` | `LaunchPlan.make(notes, installed, lastSeen)` |
| Install history | `InstallHistory.updated(_:current:cap:)` | `InstallHistory.updated(history, current, cap)` |
| Locale fallback | `LocaleFallback.resolve(preferred:available:)` | `LocaleFallback.resolve(preferred, available)` |
| Store text | `StoreText.make(for:locale:limit:) -> StoreTextResult` | `StoreText.make(notes, locale, limit): StoreTextResult` |
| Submission gate | `SubmissionGate.check(version:files:)` | `SubmissionGate.check(version, files)` |
| Launch + history | `WhatsNewChecker(source:store:)`, `.onLaunch(installedVersion:locale:)`, `.markSeen()`, `.history(locale:)` | `WhatsNewChecker(source, store)`, same methods |
| Bundled notes | `BundleReleaseNotesSource(bundle: = .main, subdirectory: = "whatsnew", onSkip:)` | `LoadedReleaseNotesSource(onSkip) { pairs }`, Android `AndroidWhatsNew.assetsSource(assets)` |
| State | `UserDefaultsWhatsNewStore(defaults: = .standard, keyPrefix:)` | `KeyValueWhatsNewStore(storage, keyPrefix)`, Android `AndroidWhatsNew.store(context)` |
| History only (read-only) | `WhatsNewChecker().history(locale:)` | Android `AndroidWhatsNew.history(context, locales)` |
| Installed version | `WhatsNewChecker.installedVersion(bundle:)` (`CFBundleShortVersionString`) | Android `AndroidWhatsNew.installedVersion(context)` (`versionName`) |
| Plug-in points | `WhatsNewStore`, `ReleaseNotesSource` protocols | `WhatsNewStore`, `ReleaseNotesSource`, `KeyValueStorage` interfaces |

## Use it at launch

```swift
import CherKitWhatsNew

let whatsNew = WhatsNewChecker()      // bundle's whatsnew/ folder + UserDefaults.standard
let entries = whatsNew.onLaunch()     // installed CFBundleShortVersionString, Locale.preferredLanguages
if !entries.isEmpty {
    showWhatsNew(entries)             // each entry: version, date, bullets; newest first
    whatsNew.markSeen()               // after the sheet was shown
}
let all = whatsNew.history()          // every bundled release, newest first; isInstalled = "you are here"
```

```kotlin
val whatsNew = AndroidWhatsNew.checker(context)   // assets/whatsnew/ + SharedPreferences
val entries = whatsNew.onLaunch(AndroidWhatsNew.installedVersion(context), AndroidWhatsNew.preferredLocales())
if (entries.isNotEmpty()) {
    showWhatsNew(entries)
    whatsNew.markSeen()
}
val all = whatsNew.history(AndroidWhatsNew.preferredLocales())
```

For a Settings screen that has no checker of its own, `AndroidWhatsNew.history(context)` returns the same list as
`AndroidWhatsNew.checker(context).history(locales)` (locales default to `AndroidWhatsNew.preferredLocales()`). It is
read-only: it never appends to the install history or marks anything seen, so it cannot change what the next launch
shows. It reads the bundled assets, so call it off the main thread:

```kotlin
val all = withContext(Dispatchers.IO) { AndroidWhatsNew.history(context) }
```

Outside Android (e.g. a consumer app's shared Kotlin code) pass any loader:
`WhatsNewChecker(LoadedReleaseNotesSource { listOf("1.4.0.json" to text) }, KeyValueWhatsNewStore(storage))`.

- `onLaunch` appends the version to the install history and returns what to show. With nothing to show it marks the
  version seen at once; otherwise only `markSeen()` does, so a crash before the sheet appears shows it again next time.
- First launch shows nothing and marks the version seen; a downgrade shows nothing and keeps the higher last-seen.
- Invalid notes files are skipped (pass `onSkip` to log them); a corrupt stored value counts as absent. Nothing throws.

## Optional telemetry (Kotlin)

`WhatsNewChecker`, `LoadedReleaseNotesSource`, `ReleaseNotesLoader.load`, `AndroidWhatsNew.checker`,
`AndroidWhatsNew.assetsSource` and `AndroidWhatsNew.history` accept `telemetrySink: TelemetrySink? = null`.
For example, use `AndroidWhatsNew.checker(context, telemetrySink = sink)`. Trailing loader/skip callbacks are
preserved, and old JVM signatures and default-argument bridges remain as hidden deprecated overloads.

| Domain | Code | Meaning |
| --- | --- | --- |
| `cherkit.whatsnew` | 1 | Notes files absent, unreadable, or loader failed |
| `cherkit.whatsnew` | 2 | A notes file could not be decoded/validated |
| `cherkit.whatsnew` | 3 | Locale fallback used |

File errors are reported once per file per read; an empty/missing directory or a failed loader reports code 1 once,
not once per expected release. An Android asset open failure reports only code 1, never an additional code 2.
A loaded source's own sink takes precedence over its checker's sink, avoiding duplicate reports. Loaders are not
cached: subsequent reads are separate operations. A custom file source that throws or has no files reports code 1;
invalid files report code 2 even when no valid notes remain.

Android reports are delivered on the main looper, including checks run by WhatsNew Compose on IO. JVM hosts
must call the checker on the telemetry owner's thread, or supply a thread-dispatching sink. A custom source that
overrides parsed `releaseNotes()` should also override the sink-aware `releaseNotes(telemetrySink)` to retain its
custom loading behavior when a sink is supplied; file-based sources use the instrumented default decoder.

Fallback reports code 3 at most once per `onLaunch`/`history` call when any returned release uses a locale other
than the first preference (case, surrounding ASCII whitespace and underscore/hyphen spelling are ignored).
Parent-language, later-preference and English fallback count; no preferences means English is the intended default.
Notes not selected for display do not produce a launch fallback report.

`cherkit.whatsnew.shown` is emitted without attributes by `markSeen()` only when notes were pending, once even if
`markSeen()` is called repeatedly. Call it after presentation as usual; fetching notes or history alone never emits
this breadcrumb. All errors also have no attributes: no file names, locale tags, versions, note text, URLs or exception
messages are sent. Null sinks send nothing; sink exceptions cannot change loading or presentation behavior.

## App-side layout

- **iOS (Xcode):** keep the files in `whatsnew/` at the repo root and add that folder to the app target as a folder
  reference (blue folder, "Create folder references"), so it ships as `<App>.app/whatsnew/<version>.json`. Check it is
  listed under the target's Build Phases > Copy Bundle Resources.
- **Android:** the files must ship as `assets/whatsnew/<version>.json`. Either copy them to
  `src/main/assets/whatsnew/<version>.json`, or keep the one repo-root `whatsnew/` folder shared with iOS and sync it
  in the app module's `build.gradle.kts`. Do not add `whatsnew/` itself as an assets `srcDir`: its files would land at
  the root of `assets/`, and `AndroidWhatsNew.checker(context)` / `assetsSource(assets)` would find nothing. Add the
  generated *parent* folder as a plain path and make `preBuild` depend on the sync (AGP 9 rejects wiring the task's
  output provider into `sourceSets`):

  ```kotlin
  val syncWhatsNew by tasks.registering(Sync::class) {
      from(rootProject.file("whatsnew")) { include("*.json") }
      into(layout.buildDirectory.dir("generated/whatsnew-assets/whatsnew"))
  }
  android { sourceSets["main"].assets.srcDir("build/generated/whatsnew-assets") }
  tasks.named("preBuild") { dependsOn(syncWhatsNew) }
  ```

Each app's bot writes the notes file for every release per the shared whatsnew-notes rules: user-facing changes only,
1 to 4 short bullets, and `"show": false` when nothing qualifies.

Swift: `Sources/CherKitWhatsNew`, tests in `Tests/CherKitWhatsNewTests`. Views live in `Sources/CherKitUI/WhatsNew`.
Kotlin: `kotlin/whatsnew` (`com.abominableenterprise.cherkit.whatsnew`). Shared test cases: `fixtures/whatsnew`.
Test with `scripts/test.sh whatsnew`.
