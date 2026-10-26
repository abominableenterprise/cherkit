sunset: added

- sunset-compose: `SunsetPrompt(checker = ..., minimumInterval = 1.hours)` runs the Sunset check and shows the
  prompts, the Android twin of CherKitUI's `.sunsetPrompt(checker:)`. It checks with
  `AndroidSunset.check(checker, context)` once per process at first composition, again only on a real return from
  the background (Lifecycle `ON_STOP`, then `ON_RESUME`; not the first `ON_RESUME`, `ON_PAUSE` → `ON_RESUME` or a
  rotation), never while a check is running, and at most once per `minimumInterval`. The decision drives the same
  soft dialog ("Not now" remembered for `prompt.latestVersion`) and hard block. `SunsetChecker.disabled()` shows
  nothing. It needs `androidx.lifecycle:lifecycle-runtime-compose`, which Compose `ui` already brings: no new
  dependency.
- sunset-compose: both `SunsetPrompt` overloads take `onVisibilityChange: (Boolean) -> Unit = {}`, called with `true`
  when the soft "Update available" dialog appears and `false` when it goes away. Apps that run their own checks can
  skip them while it is open instead of copying `SunsetSoftPromptRule` and the stored "Not now" version. The hard
  block is not reported. The decision overload is otherwise unchanged (source and binary compatible).
- `SunsetCheckScheduler` (Kotlin `:sunset`, common code): the pure rule for when to check (launch, return from
  background, in-flight skip, `minimumInterval`), the same as CherKitUI's.
- Docs: the sunset-compose README and the `SunsetPrompt` KDoc now show `SunsetPrompt(checker = ...)` and
  `AndroidSunset.check(checker, context)` instead of the deprecated `checker.check(context)` extension.
