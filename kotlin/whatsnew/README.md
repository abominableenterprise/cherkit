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

Outside Android (e.g. a consumer app's shared Kotlin code) pass any loader:
`WhatsNewChecker(LoadedReleaseNotesSource { listOf("1.4.0.json" to text) }, KeyValueWhatsNewStore(storage))`.

- `onLaunch` appends the version to the install history and returns what to show. With nothing to show it marks the
  version seen at once; otherwise only `markSeen()` does, so a crash before the sheet appears shows it again next time.
- First launch shows nothing and marks the version seen; a downgrade shows nothing and keeps the higher last-seen.
- Invalid notes files are skipped (pass `onSkip` to log them); a corrupt stored value counts as absent. Nothing throws.

## App-side layout

- **iOS (Xcode):** keep the files in `whatsnew/` at the repo root and add that folder to the app target as a folder
  reference (blue folder, "Create folder references"), so it ships as `<App>.app/whatsnew/<version>.json`. Check it is
  listed under the target's Build Phases > Copy Bundle Resources.
- **Android:** put the files in `src/main/assets/whatsnew/<version>.json` (or point the assets source set at the
  repo's `whatsnew/` folder) so they ship as `assets/whatsnew/`.

Each app's bot writes the notes file for every release per the shared whatsnew-notes rules: user-facing changes only,
1 to 4 short bullets, and `"show": false` when nothing qualifies.

Swift: `Sources/CherKitWhatsNew`, tests in `Tests/CherKitWhatsNewTests`. Views live in `Sources/CherKitUI/WhatsNew`.
Kotlin: `kotlin/whatsnew` (`com.abominableenterprise.cherkit.whatsnew`). Shared test cases: `fixtures/whatsnew`.
Test with `scripts/test.sh whatsnew`.
