whatsnew: added

- WhatsNew notes file format `whatsnew/<version>.json` (`version`, optional `date`, `show`, `notes` per locale),
  read and validated by `NotesFile.read` (Swift `CherKitWhatsNew`, Kotlin `:whatsnew`) into `ReleaseNotes` plus a
  list of readable `NotesFileError`s.
- `LaunchPlan.make` decides which notes to show after an update (every `show: true` version newer than the last seen,
  newest first; nothing on first launch or downgrade). `InstallHistory.updated` keeps the device's version history.
- `LocaleFallback.resolve` (`pt-BR` -> `pt` -> `en`), `StoreText.make` (store "What's New" text, Play's 500-character
  limit, never truncated) and `SubmissionGate.check` (ok / missing / invalid) for the release step.
- `WhatsNewStore` and `ReleaseNotesSource` protocols; storage and bundle loaders come later.
- `WhatsNewModuleInfo` stays; the scaffold placeholder fixture and tests are gone.
