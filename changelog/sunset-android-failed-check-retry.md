sunset: fixed

- sunset-compose: `SunsetPrompt(checker = ...)` no longer misses a hard block until the next cold start when a check
  fails (fetch dropped or timed out, or a body that does not parse, with no usable cached config, e.g. on a fresh
  install). A failed check no longer counts toward `minimumInterval`; it is retried once about 3 seconds later
  (dropped if another check starts first), and if that fails too, the next return from the background (`ON_STOP`,
  then `ON_RESUME`) re-checks whatever the interval. After a success the interval applies again. The same behavior
  as CherKitUI's `.sunsetPrompt(checker:)`.
- `SunsetChecker.checkReportingFailure(installedVersion, osVersion, locale)` (Kotlin `:sunset`) returns a
  `SunsetCheckOutcome(decision, failed)`, where `failed` means no usable config at all. Most apps keep calling
  `check(...)`, which is unchanged and still fails open.
- `SunsetCheckScheduler` (Kotlin, new since 0.2.0): `checkFinished(failed: Boolean = false)` now returns whether to
  schedule the quick retry, plus `shouldRetry(now)`, `failuresInARow` and `isRetryPending`.
