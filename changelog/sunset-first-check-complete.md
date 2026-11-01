sunset: added

- `.sunsetPrompt(checker:)` takes an optional `onFirstCheckComplete: (() -> Void)?`. It runs once per process when the
  launch check's first attempt resolves, whatever the outcome (up to date, soft, hard, or a failed fetch that fails
  open after the checker's timeout), and always after `onPromptChange` / `onVisibilityChange` reported that first
  decision. `SunsetChecker.disabled` calls it on the first appear; re-checks on a return from the background and the
  quick retry after a failed check never call it again. Hold What's New until Sunset has decided with
  `.whatsNew(checker: whatsNew, suppressed: !sunsetSettled || sunsetKind != nil)` (recipe in the CherKitUI README).
