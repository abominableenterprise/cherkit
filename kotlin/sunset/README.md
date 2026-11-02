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
    "message": { "en": "Please update.", "de": "Bitte aktualisieren." },
    "testFlight": {
      "latestBuild": "124",
      "url": "itms-beta://beta.itunes.apple.com/v1/app/123456789",
      "notBefore": "2026-11-01T12:30:00Z",
      "message": { "en": "A new test build is ready." }
    }
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
- `testFlight` (iOS only, optional): the newest TestFlight build, for TestFlight installs (see
  [Update sources](#update-sources)).
  - `latestBuild`: required string, a build number (`"124"` or dot-separated like `"1.2.10"`, each part 1 to 9
    digits). TestFlight installs whose `CFBundleVersion` is lower get a soft test-build prompt.
  - `url`: optional. `itms-beta://...` or `https://...` (any host, e.g. `https://testflight.apple.com/join/<code>`);
    any other scheme or a malformed value counts as absent. Absent: the app opens plain `itms-beta://` (the TestFlight
    app). For existing testers, `itms-beta://beta.itunes.apple.com/v1/app/<appleId>` (the app's TestFlight page) is
    the recommended value; the library passes it on unchanged.
  - `notBefore`: optional UTC time `YYYY-MM-DDTHH:MM:SSZ` (optional `.fraction`); no prompt while the device clock is
    before it. A malformed value is ignored (no delay).
  - `message`: optional, same language rules as above.
  - A bad `testFlight` object (missing or invalid `latestBuild`, not an object) is ignored on its own; the rest of the
    `ios` block still applies.

## Update sources

A soft "newer build available" prompt can come from three sources. A hard block always comes from the config's
`minimumVersion` alone, on every channel, and always wins.

| Source | Applies to | Enabled |
| --- | --- | --- |
| Config `latestVersion` | every channel | always |
| App Store lookup (`https://itunes.apple.com/lookup?bundleId=<id>&country=<cc>`) | `store` channel (iOS release builds) | opt in: pass a `lookup` source to `SunsetChecker` |
| Config `ios.testFlight` | `testFlight` channel | whenever the app reports `testFlight` and the block exists |

The app passes its install channel (`store`, the default, `testFlight` or `other`) and its build number to `check`;
the iOS adapter detects them. Rules (contract: `fixtures/sunset/update-sources.json`, `lookup.json` and
`test-build-prompt.json`):

- **Lookup**: the offered version is the higher of the config's `latestVersion` and the lookup's `version`. The
  lookup's `minimumOsVersion` gates its version like `latestMinimumOS` does the config's: a device below it gets no
  prompt from the lookup. The prompt links the config's `storeURL`, else the lookup's `trackViewUrl` (plain `https`
  only). A malformed, empty (`resultCount` 0) or failed lookup is ignored. The lookup never hard-blocks and is never
  used on the `testFlight` or `other` channels.
- **TestFlight**: on the `testFlight` channel, an installed build lower than `testFlight.latestBuild` gets a soft
  prompt with `kind` test build and `latestBuild` set, ahead of the config's soft release prompt. Equal or higher, an
  unknown or unparsable installed build, or a `notBefore` still in the future: no test-build prompt. Not gated by OS.
- **Prompt memory**: release prompts keep their "Not now" per version. A test-build prompt shows **at most once per
  offered build**: both "Not now" and "Update" record that build (`SunsetTestBuildPromptRule`, suggested key
  `cherkit.sunset.testBuildRememberedBuild`, separate from the release key), and a different `latestBuild` prompts
  again.

### Release pipeline: publish the TestFlight build

After each TestFlight upload, write the new build number into the hosted config and publish it:

```sh
jq --arg build "$BUILD_NUMBER" '.ios.testFlight.latestBuild = $build' sunset.json > sunset.json.tmp && mv sunset.json.tmp sunset.json
```

Publish only once the build is installable, or testers get a prompt for a build TestFlight does not offer yet. Best:
wait until App Store Connect reports the build's `processingState` as `VALID` (App Store Connect API, `GET /v1/builds`
filtered by app and version), then publish. If the pipeline cannot wait, also set `notBefore` to a time after
processing usually ends:

```sh
jq --arg build "$BUILD_NUMBER" --arg at "$(date -u -d '+30 minutes' +%Y-%m-%dT%H:%M:%SZ)" '.ios.testFlight.latestBuild = $build | .ios.testFlight.notBefore = $at' sunset.json > sunset.json.tmp && mv sunset.json.tmp sunset.json
```

(`date -d` is GNU date; on macOS use `date -u -v+30M +%Y-%m-%dT%H:%M:%SZ`.)

## API (same names on both platforms)

| Swift | Kotlin |
| --- | --- |
| `SunsetConfig.parse(Data) -> SunsetConfig?` | `SunsetConfig.parse(String): SunsetConfig?` |
| `SunsetDecider.decide(config:platform:appVersion:osVersion:languages:channel:installedBuild:lookup:now:)` | `SunsetDecider.decide(config, platform, appVersion, osVersion, languages, channel, installedBuild, lookup, now)` |
| `SunsetDecision.upToDate / .softUpdate(p) / .hardUpdate(p)` | `SunsetDecision.UpToDate / SoftUpdate(p) / HardUpdate(p)` |
| `SunsetPrompt(storeURL: URL, message: String?, latestVersion: AppVersion?, kind:, latestBuild:)` | `SunsetPrompt(storeUrl: String, message: String?, latestVersion: AppVersion?, kind, latestBuild)` |
| `SunsetChannel.store / .testFlight / .other` | `SunsetChannel.STORE / TEST_FLIGHT / OTHER` |
| `SunsetUpdateKind.update / .testBuild` | `SunsetUpdateKind.UPDATE / TEST_BUILD` |
| `SunsetBuildNumber(String)` (failable), `components` | `SunsetBuildNumber.parse(String): SunsetBuildNumber?`, `components` |
| `SunsetTestFlightConfig(latestBuild:url:notBefore:messages:)`, `defaultURL`; `SunsetPlatformConfig.testFlight` | `SunsetTestFlightConfig(latestBuild, url, notBefore, messages)`, `DEFAULT_URL`; `SunsetPlatformConfig.testFlight` |
| `SunsetLookupResult.parse(Data) -> SunsetLookupResult?` (`version`, `minimumOSVersion`, `trackViewURL`) | `SunsetLookupResult.parse(String): SunsetLookupResult?` (`version`, `minimumOsVersion`, `trackViewUrl`) |
| `protocol SunsetLookupSource` (`fetch(timeout:) async -> Data?`) | `fun interface SunsetLookupSource` (`suspend fetch(timeout): String?`) |
| `URLSessionSunsetLookupSource(url:session:)`, `init?(bundleID:country:session:)`, `init?(bundle:locale:session:)`, `country(for:)` | (none: Android does not use the lookup) |
| `SunsetChannel.current(bundle:)`, `current(receiptURL:isSimulator:)` (iOS: TestFlight from `sandboxReceipt`, simulator `other`) | |
| `SunsetTestBuildPromptRule.shouldShow(_:rememberedBuild:)`, `buildToRemember(after:on:)`, `defaultMemoryKey` | `SunsetTestBuildPromptRule.shouldShow(decision, rememberedBuild)`, `buildToRemember(action, prompt)`, `DEFAULT_MEMORY_KEY` |
| `SunsetPromptAction.notNow / .update` | `SunsetPromptAction.NOT_NOW / UPDATE` |
| `SunsetMessages.resolve(_:languages:)` | `SunsetMessages.resolve(messages, languages)` |
| `SunsetConfigChoice.choose(fetched:cached:)` | `SunsetConfigChoice.choose(fetched, cached)` |
| `protocol SunsetConfigSource` (`fetch(timeout:) async -> Data?`) | `fun interface SunsetConfigSource` (`suspend fetch(timeout): String?`) |
| `protocol SunsetConfigCache` (`load`, `save`) | `interface SunsetConfigCache` (`load`, `save`) |
| `SunsetChecker(source:cache:timeout:platform:lookup:)`, `check(installedVersion:osVersion:locale:channel:installedBuild:) async` | `SunsetChecker(source, cache, timeout, platform, lookup)`, `suspend check(installedVersion, osVersion, locale, channel, installedBuild)` |
| `SunsetChecker.disabled`, `isDisabled` | `SunsetChecker.disabled()`, `isDisabled` |
| `checkReportingFailure(...)` (`package`, for CherKitUI) | `suspend checkReportingFailure(...): SunsetCheckOutcome(decision, failed)` |
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

`isDisabled` is public and read-only on both platforms, so app tests can assert they wired the switched-off checker:
`assertTrue(testEnvironment.sunsetChecker.isDisabled)` (Kotlin), `XCTAssertTrue(...sunsetChecker.isDisabled)` (Swift).

## Optional telemetry (Kotlin)

Pass `telemetrySink: TelemetrySink? = null` to `SunsetChecker` or
`AndroidSunset.checker(context, telemetrySink = sink) { /* GET */ }`. The sink contract is in Core; no telemetry
provider dependency is added. Existing JVM signatures (including default-argument bridges) remain as hidden
deprecated overloads. Omitting the sink preserves fail-open behavior and sends nothing.

| Domain | Code | Meaning |
| --- | --- | --- |
| `cherkit.sunset` | 1 | Config fetch failed (null, empty through the fetch adapter, or exception) |
| `cherkit.sunset` | 2 | Config fetch timed out |
| `cherkit.sunset` | 3 | Fetched config could not be decoded/accepted |
| `cherkit.sunset` | 4 | Store lookup failed (null or exception) |
| `cherkit.sunset` | 5 | Store lookup timed out |
| `cherkit.sunset` | 6 | Lookup response could not be decoded/accepted (including no result) |

Each failed operation reports once per check, even when cached config supplies a successful decision. Cached data
and cache read/write errors do not generate additional reports. A custom source that hides a timeout by returning
null can only be classified as a fetch failure; the built-in fetch adapter preserves timeout classification.
Lookup is not called or reported outside the store channel. Caller cancellation and the disabled checker emit nothing.

Every completed enabled check emits `cherkit.sunset.decision` with only `kind`: `none`, `soft`, `hard`, or
`test_build`. Errors have no attributes. No URLs, versions, locale tags, exception messages or config contents are
sent. Sink exceptions are ignored, so diagnostics cannot change the decision.

## Failed checks

A check *fails* when there is no usable config at all: the fetch failed, timed out or did not parse, and nothing
usable is cached (e.g. a fresh install whose connection dropped). `check` still fails open and answers `upToDate`.
Kotlin's `checker.checkReportingFailure(installedVersion, osVersion, locale)` returns the same decision as a
`SunsetCheckOutcome(decision, failed)`, for code that schedules checks; Swift's twin is `package`-only.
`SunsetCheckScheduler.checkFinished(failed)` does not count a failed check toward `minimumInterval`, asks for one quick
retry after the first failure in a row (`shouldRetry(now)`), and while the last check failed lets the next return from
the background re-check whatever the interval. `SunsetPrompt(checker = ...)` (sunset-compose) and
`.sunsetPrompt(checker:)` (CherKitUI) do this for you, retrying after about 3 seconds.

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
  only on a return from the background, never two at once, at most once per `minimumInterval`, and a failed check is
  retried once and does not count (see Failed checks). Use it if you run the checks yourself.
