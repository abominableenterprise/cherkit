whatsnew-compose: added

- `WhatsNewPrompt(checker = ...)` takes an optional `onLaunchCheckComplete: ((hasNotes: Boolean) -> Unit)? = null`.
  It is called once per process, right after the launch `onLaunch` has returned (same once-per-process guard), with
  whether there are notes to show (`false` when nothing is new or `onLaunch` failed open). It still comes while
  `suppressed = true`: the check runs, only the sheet waits. With notes and the sheet not suppressed, the sheet's
  `onVisibilityChange(true)` runs first, then this, in the same frame. Recomposition and rotation never call it again.
  Use it like Sunset's `onFirstCheckComplete`, e.g. to keep Play's update sheet back with
  `suppressed = ... || !LaunchGate.whatsNewSettled || whatsNewVisible` (see the README). The 0.6.0 signature stays
  as a hidden overload, so code compiled against it keeps working.
