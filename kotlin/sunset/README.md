# CherKit Sunset

Version sunsetting: apps read a small hosted min-version config and show a soft "update available" prompt or a hard
"update required" block. It **fails open**: a missing, malformed, tampered or unreachable config never blocks the app.

Swift: `Sources/CherKitSunset`, tests in `Tests/CherKitSunsetTests`. Views live in `Sources/CherKitUI/Sunset`. Kotlin:
`kotlin/sunset`. Shared test cases: `fixtures/sunset`. Test with `scripts/test.sh sunset`.

## Hosted config

One public JSON file per app (e.g. on a CDN). Unknown fields are ignored; any `schemaVersion` other than `1` fails open.

```json
{
  "schemaVersion": 1,
  "ios": {
    "minimumVersion": "1.2.0",
    "latestVersion": "1.4.0",
    "latestMinimumOS": "17.0",
    "storeURL": "https://apps.apple.com/app/id123456789",
    "message": { "en": "Please update.", "de": "Bitte aktualisieren." }
  },
  "android": {
    "minimumVersion": "1.2.0",
    "latestVersion": "1.4.0",
    "latestMinimumOS": "26",
    "storeURL": "https://play.google.com/store/apps/details?id=com.example.app",
    "message": { "en": "Please update." }
  }
}
```

- `minimumVersion`: below it, **hard** update. `latestVersion`: below it, **soft** update. Both optional.
- `latestMinimumOS`: lowest iOS version / Android API level that can install `latestVersion`. A device below it (or
  whose OS version does not parse) gets **no prompt at all, not even a hard block**: it cannot install the update, so
  blocking it would lock the user out. Raise `minimumVersion` only together with an OS floor users can actually reach.
- `storeURL`: required, plain `https` only (see `fixtures/sunset/store-url.json`).
- `message`: optional, by language tag. Resolution: each preferred tag, then trailing subtags removed (`de-DE` -> `de`),
  then `en`, then none (the UI shows its default text).
- A platform block with a bad field (version that is not a valid `AppVersion` string, missing/invalid `storeURL`) is
  ignored as a whole: that platform never prompts.

## API (same names on both platforms)

| Swift | Kotlin |
| --- | --- |
| `SunsetConfig.parse(Data) -> SunsetConfig?` | `SunsetConfig.parse(String): SunsetConfig?` |
| `SunsetDecider.decide(config:platform:appVersion:osVersion:languages:)` | `SunsetDecider.decide(config, platform, appVersion, osVersion, languages)` |
| `SunsetDecision.upToDate / .softUpdate(p) / .hardUpdate(p)` | `SunsetDecision.UpToDate / SoftUpdate(p) / HardUpdate(p)` |
| `SunsetPrompt(storeURL: URL, message: String?, latestVersion: AppVersion?)` | `SunsetPrompt(storeUrl: String, message: String?, latestVersion: AppVersion?)` |
| `SunsetMessages.resolve(_:languages:)` | `SunsetMessages.resolve(messages, languages)` |
| `SunsetConfigChoice.choose(fetched:cached:)` | `SunsetConfigChoice.choose(fetched, cached)` |
| `protocol SunsetConfigSource` (`fetch(timeout:) async -> Data?`) | `fun interface SunsetConfigSource` (`suspend fetch(timeout): String?`) |
| `protocol SunsetConfigCache` (`load`, `save`) | `interface SunsetConfigCache` (`load`, `save`) |
| `SunsetChecker(source:cache:timeout:platform:)`, `check(installedVersion:osVersion:locale:) async` | `SunsetChecker(source, cache, timeout, platform)`, `suspend check(installedVersion, osVersion, locale)` |
| `SunsetChecker.disabled` | `SunsetChecker.disabled()` |
| `URLSessionSunsetConfigSource(url:session:)` | `FetchSunsetConfigSource(fetch: SunsetFetch)` |
| `UserDefaultsSunsetConfigCache(defaults:key:)` | `KeyValueSunsetConfigCache(store: SunsetKeyValueStore, key)`; Android: `SharedPreferencesSunsetStore` |

## Use it at launch

`SunsetChecker.check` fetches the config (default timeout 5 s, HTTP cache ignored), falls back to the last good cached
config, decides, and saves the fetched body only when it parsed. It never throws: every failure ends in the cached
config or `upToDate`.

Swift (iOS): reads `CFBundleShortVersionString`, the `ProcessInfo` OS version and `Locale.preferredLanguages`.

```swift
import CherKitSunset

let checker = SunsetChecker(url: URL(string: "https://example.github.io/myapp/sunset.json")!)
switch await checker.check() {
case .upToDate: break
case .softUpdate(let prompt): showUpdateAvailable(prompt)  // dismissible, links prompt.storeURL
case .hardUpdate(let prompt): showUpdateRequired(prompt)   // blocking
}
```

Kotlin (Android): CherKit has no HTTP client, so pass your own GET. Return the body for a 2xx response, else `null`
(`SunsetFetch.bodyIfSuccess(code, body)` does that check); turn off HTTP caching for this request. The call is wrapped in
`withTimeoutOrNull`. `AndroidSunset.check(checker, context)` reads `versionName`, `Build.VERSION.SDK_INT` and the user's
locales (pass `locale` to override them). It needs no import beyond `AndroidSunset`; the older
`checker.check(context)` extension still works but is deprecated, because its import
(`com.abominableenterprise.cherkit.sunset.check`) is easy to confuse with Kotlin's `check(Boolean)`.

```kotlin
val checker = AndroidSunset.checker(context) {
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(SUNSET_URL).cacheControl(CacheControl.FORCE_NETWORK).build()
        okHttp.newCall(request).execute().use { SunsetFetch.bodyIfSuccess(it.code, it.body?.string()) }
    }
}
when (val decision = AndroidSunset.check(checker, context)) {
    SunsetDecision.UpToDate -> Unit
    is SunsetDecision.SoftUpdate -> showUpdateAvailable(decision.prompt)
    is SunsetDecision.HardUpdate -> showUpdateRequired(decision.prompt)
}
```

Elsewhere (JVM, tests, an app's own storage): `SunsetChecker(FetchSunsetConfigSource { ... },
KeyValueSunsetConfigCache(myStore)).check(installedVersion, osVersion, locale)`.

A prompt carries `storeUrl`, the resolved `message` and the config's `latestVersion` for the platform (`null` when the
block has none), e.g. to remember "Not now" for that version.

## Turn it off in UI tests and on emulators or simulators

`SunsetChecker.disabled` (Swift) / `SunsetChecker.disabled()` (Kotlin) always answers `upToDate` without touching the
network or the cache. Use it instead of a flag of your own:

```swift
let checker = isUITest ? .disabled : SunsetChecker(url: URL(string: "https://example.github.io/myapp/sunset.json")!)
```

```kotlin
val checker = if (isUiTest) SunsetChecker.disabled() else AndroidSunset.checker(context) { /* your GET */ }
```

## Hosting the config

Put the JSON on any static HTTPS host; no server code is needed.

- **GitHub Pages:** commit `sunset.json` to a Pages repo, e.g. `https://<user>.github.io/<app>/sunset.json`.
- **Firebase Hosting:** put `sunset.json` in `public/` and run `firebase deploy --only hosting`; serve it with
  `Cache-Control: no-cache` (a `headers` entry in `firebase.json`) so updates are not held by the CDN.

Minimal file (only a soft prompt on iOS; add `minimumVersion` for a hard block):

```json
{
  "schemaVersion": 1,
  "ios": {
    "latestVersion": "1.4.0",
    "storeURL": "https://apps.apple.com/app/id123456789"
  }
}
```

Validate the JSON before publishing: a malformed file never blocks anyone, but apps then keep the last good config.

- iOS: the SwiftUI screens are in CherKitUI (`.sunsetPrompt`).
- Android: the Compose screens are in the `sunset-compose` module; `SunsetPrompt(checker = ...)` there also runs the
  checks.
- `SunsetCheckScheduler` (Kotlin, common code) holds the rule both use for when to re-check: once at launch, again
  only on a return from the background, never two at once, at most once per `minimumInterval`. Use it if you run the
  checks yourself.
