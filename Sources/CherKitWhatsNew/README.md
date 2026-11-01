See [kotlin/whatsnew/README.md](../../kotlin/whatsnew/README.md), the module README shared by both platforms, for the
"use it at launch" example (`WhatsNewChecker().onLaunch()`, then `markSeen()` after showing the notes).

**Xcode layout:** add the repo's `whatsnew/` folder to the app target as a *folder reference* (blue folder), so it is
copied as `<App>.app/whatsnew/<version>.json`. A plain group copies the files flat into the bundle root, where
`BundleReleaseNotesSource` does not look. The notes are written by each app's bot per the shared whatsnew-notes rules:
user-facing changes only, 1 to 4 bullets, `"show": false` when nothing qualifies.

**`MemberImportVisibility`:** `WhatsNewEntry.version` and `ReleaseNotes.version` are CherKitCore `AppVersion`s. If
your target enables Swift's `MemberImportVisibility` upcoming feature, using their members (e.g.
`entry.version.description`) needs `import CherKitCore` in that file, even though CherKitWhatsNew brings the module.
Without the import, use `String(describing: entry.version)`.
