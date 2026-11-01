sunset-compose: added

- `SunsetPrompt(checker = ...)` takes an optional `onFirstCheckComplete: (() -> Unit)? = null`. It is called once per
  process when the launch check's first attempt has finished, whatever it found (up to date, soft, hard or a failed
  fetch, which fails open), and at once for `SunsetChecker.disabled()`. Any `onPromptChange` / `onVisibilityChange`
  call for that first decision runs before it. Later re-checks, recomposition and rotation never call it again.
  Use it to hold back the "What's new" sheet on a cold start until Sunset has answered:
  `suppressed = !sunsetSettled || sunsetKind != null` (recipe in the Sunset Compose README).
