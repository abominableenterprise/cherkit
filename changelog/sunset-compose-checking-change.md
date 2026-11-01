sunset-compose: added

- `SunsetPrompt(checker = ...)` takes an optional `onCheckingChange: ((Boolean) -> Unit)? = null`. It reports `true`
  when any check starts (the launch check, the re-check on return from the background, the quick retry after a
  failed fetch) and `false` when it has finished, with any outcome. Any `onPromptChange` / `onVisibilityChange` for
  that result runs before the `false`. `SunsetChecker.disabled()` never reports `true`. Add `sunsetChecking` to the
  gate of Play's update sheet so it cannot open while a re-check may still find a hard block (see the README). The
  0.6.0 signature stays as a hidden overload, so code compiled against it keeps working.
