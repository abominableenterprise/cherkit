sunset: added

- `SunsetChecker`: one call at launch returns `upToDate`, `softUpdate` or `hardUpdate`. It fetches the hosted config
  (default timeout 5 s), falls back to the last good cached config, and never throws: every failure fails open.
  Swift: `await SunsetChecker(url:).check()` reads `CFBundleShortVersionString` and the OS version, or call
  `check(installedVersion:osVersion:locale:)`. Kotlin: `suspend check(installedVersion, osVersion, locale)`; on Android
  `AndroidSunset.checker(context) { yourHttpGet() }.check(context)` reads `versionName` and `Build.VERSION.SDK_INT`.
- Swift `URLSessionSunsetConfigSource` (ignores the HTTP cache; only 2xx bodies count) and
  `UserDefaultsSunsetConfigCache` (injectable `UserDefaults` suite and key).
- Kotlin `FetchSunsetConfigSource` over your own `SunsetFetch` call (no HTTP client dependency), and
  `KeyValueSunsetConfigCache` over a `SunsetKeyValueStore`; Android ships `SharedPreferencesSunsetStore`.
  `:sunset` now depends on `kotlinx-coroutines-core`.
- Only a fetched config that parses is cached; a malformed or non-2xx response never replaces the last good one.
