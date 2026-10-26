sunset: added

- Hosted config format for version sunsetting (`schemaVersion` 1, one `ios` / `android` block with `minimumVersion`,
  `latestVersion`, `latestMinimumOS`, `storeURL`, `message`), documented in `kotlin/sunset/README.md`.
- `SunsetConfig.parse` and `SunsetDecider.decide(config, platform, appVersion, osVersion, languages)` return
  `upToDate`, `softUpdate` or `hardUpdate` with the store URL and the localized message (`de-DE` -> `de` -> `en`).
  Same API in Swift (`CherKitSunset`) and Kotlin (`:sunset`). Everything fails open: a missing, malformed or unknown
  config, or a device whose OS is below `latestMinimumOS`, gets no prompt.
- `SunsetConfigChoice.choose(fetched, cached)` picks a fresh fetch, else the cached last good config, else none.
  `SunsetConfigSource` and `SunsetConfigCache` are protocols only; implementations and the update screens come later.
