# CherKitSunset (Swift)

See [kotlin/sunset/README.md](../../kotlin/sunset/README.md), the module README shared by both platforms: config
format, API table and the Kotlin setup.

## Use it at launch

```swift
import CherKitSunset

let checker = SunsetChecker(url: URL(string: "https://example.github.io/myapp/sunset.json")!)
switch await checker.check() {  // CFBundleShortVersionString, ProcessInfo OS version, Locale.preferredLanguages
case .upToDate: break
case .softUpdate(let prompt): showUpdateAvailable(prompt)
case .hardUpdate(let prompt): showUpdateRequired(prompt)
}
```

It never throws. The fetch times out after 5 s by default (`timeout:`) and ignores the HTTP cache; only a 2xx body
counts. The last good config is kept in `UserDefaults.standard` (pass
`UserDefaultsSunsetConfigCache(defaults: UserDefaults(suiteName: "group.x")!, key: "...")` to change it), and a
malformed fetch is never cached. Offline or with no usable config at all, the answer is `upToDate`.

The prompt carries `storeURL`, the resolved `message` and the config's `latestVersion` (`AppVersion?`, `nil` when the
platform block has none).

`.sunsetPrompt(checker:)` in CherKitUI runs this check at launch and on returns from the background. When a check
has no usable config at all (fetch failed and nothing cached), it retries once a few seconds later and, if that fails
too, re-checks on the next return from the background regardless of `minimumInterval` (see the CherKitUI README).

## Turn it off in UI tests and on the simulator

`SunsetChecker.disabled` always answers `upToDate` without touching the network or the cache:

```swift
let checker = isUITest ? .disabled : SunsetChecker(url: URL(string: "https://example.github.io/myapp/sunset.json")!)
```

`isDisabled` is public and read-only, so app tests can assert they wired the switched-off checker:
`XCTAssertTrue(AppEnvironment.uiTest.sunsetChecker.isDisabled)`.

## Hosting

Put the JSON on any static HTTPS host, e.g. GitHub Pages (`https://<user>.github.io/<app>/sunset.json`) or Firebase
Hosting (`public/sunset.json`, `firebase deploy --only hosting`). Minimal file:

```json
{
  "schemaVersion": 1,
  "ios": {
    "minimumVersion": "1.2.0",
    "latestVersion": "1.4.0",
    "storeURL": "https://apps.apple.com/app/id123456789"
  }
}
```
