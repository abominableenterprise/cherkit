whatsnew: added

- `WhatsNewChecker` is the one call at launch: `onLaunch(installedVersion:locale:)` returns the notes to show (version,
  date, localized bullets; newest first) and updates the install history; call `markSeen()` after showing them, so a
  crash before display keeps the notes. `history(locale:)` lists every bundled release with an `isInstalled` flag.
- Swift: `BundleReleaseNotesSource` reads `whatsnew/*.json` from a `Bundle` (default `.main`),
  `UserDefaultsWhatsNewStore` keeps state (injectable suite and key prefix), and
  `WhatsNewChecker.installedVersion(bundle:)` reads `CFBundleShortVersionString`.
- Kotlin: `LoadedReleaseNotesSource` wraps an app-supplied (file name, text) loader, `KeyValueWhatsNewStore` sits on
  a tiny `KeyValueStorage`; on Android `AndroidWhatsNew` reads `assets/whatsnew/`, SharedPreferences and `versionName`.
- `ReleaseNotesLoader.load` (and `ReleaseNotesSource.releaseNotes()`) skips invalid notes files and reports them to an
  optional `onSkip`. Corrupt stored values count as absent. Nothing throws to the app.
