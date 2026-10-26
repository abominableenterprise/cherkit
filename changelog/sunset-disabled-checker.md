sunset: added

- `SunsetChecker.disabled` (Swift) and `SunsetChecker.disabled()` (Kotlin): a checker that always answers
  `upToDate` without touching the network or the cache. Use it to turn the check off in UI tests and on simulators
  or emulators, e.g. `let checker = isUITest ? .disabled : SunsetChecker(url: url)`.
