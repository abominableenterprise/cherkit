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

## Optional Swift telemetry

Pass `telemetrySink: TelemetrySink? = nil` to `WhatsNewChecker` to report through CherKitCore without depending on
CherKitTelemetry. The same optional argument is available on `BundleReleaseNotesSource`, `ReleaseNotesLoader.load`,
`ReleaseNotes.bullets` and `LocaleFallback.resolve` for standalone use. Existing synchronous APIs remain synchronous
and nonisolated: reports are queued asynchronously on `MainActor`, in order per reporter, without blocking the calling
thread. Delivery may occur after the method returns. A nil sink queues no work and preserves existing behavior.

| Domain | Code | Meaning |
| --- | --- | --- |
| `cherkit.whatsnew` | 1 | Notes directory missing/unreadable, or a notes file could not be read. |
| `cherkit.whatsnew` | 2 | Notes JSON undecodable **or invalid under notes validation rules**. |
| `cherkit.whatsnew` | 3 | Locale fallback used for a resolved notes entry. |

Locale fallback means the selected locale is **not the first preferred locale**, after the resolver's ASCII whitespace,
case and underscore/hyphen normalization. Parent locales, later preferences and default English all count. Exact
normalized first-preference matches do not; neither does resolving no locale. The checker reports once per resolved
entry on `onLaunch` and `history`, only for entries it actually resolves.

Each read reports once per failed file (not once per validation problem), or once for an unavailable directory.
An existing empty directory is not a failure. Unreadable files report code 1 only, never also code 2; `onSkip` still
receives the existing validation errors. If both a bundle source and its checker have sinks, the checker sink takes
precedence for that read, avoiding duplicate errors. Without a checker sink, the source's sink remains active.
Custom `ReleaseNotesSource` implementations own their failure reporting, including those using the default
`releaseNotes()` implementation; the checker cannot infer failures from an empty result. To report custom file
validation failures, call `ReleaseNotesLoader.load(_:onSkip:telemetrySink:)` explicitly with the desired sink.

The breadcrumb `cherkit.whatsnew.shown` is emitted once when `markSeen()` consumes a pending nonempty launch plan,
after storing its last-seen version. Call it only after display. Merely planning with `onLaunch`, reading history,
first launch, an empty plan, or repeating `markSeen()` emits no breadcrumb.

**Privacy:** every report has empty attributes (`[:]`). No file names, paths, locales, versions, bullet text, error
messages or other dynamic strings are sent. The shared contract is
[`fixtures/whatsnew/telemetry.json`](../../fixtures/whatsnew/telemetry.json); Swift tests
await queued delivery explicitly rather than relying on task scheduling guesses.
