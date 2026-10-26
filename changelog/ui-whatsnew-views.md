ui: added

- `WhatsNewSheet(entries:strings:onContinue:)` shows the notes from `WhatsNewChecker.onLaunch` (version, date,
  bullets; newest first, one section per release) with a "Continue" button. Local only: no network, no spinner.
- `.whatsNew(checker:)` on your root view calls `onLaunch` on first appear, presents the sheet when there are notes,
  and calls `markSeen()` only once the sheet is dismissed. `.whatsNew(entries:onDismiss:)` presents entries you got
  yourself and calls `onDismiss` once on dismissal.
- `WhatsNewHistoryView(entries:strings:)` lists `WhatsNewChecker.history()` with a "You're on this version" badge on
  the installed release; push it from Settings (it sets its own navigation title).
- English text by default; pass `WhatsNewStrings(...)` to localize or reword any string. Supports Dynamic Type up to
  the accessibility sizes, VoiceOver, small screens and light/dark mode; iOS 16+ and macOS 13+.
- `WhatsNewPlaceholderView` (scaffold placeholder) is removed.
