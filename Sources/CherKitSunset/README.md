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

## iOS: App Store lookup and TestFlight builds

Opt in to the two extra update sources (rules: [Update sources](https://github.com/abominableenterprise/cherkit/blob/main/kotlin/sunset/README.md#update-sources)):

```swift
#if DEBUG
let channel = SunsetChannel.other   // a library cannot detect debug builds; they get only the config's versions
#else
let channel = SunsetChannel.current()
#endif

let checker = SunsetChecker(
    url: URL(string: "https://example.github.io/myapp/sunset.json")!,
    lookup: URLSessionSunsetLookupSource()   // Bundle.main's id, Locale.current's region; nil (no lookup) without them
)
let decision = await checker.check(channel: channel)   // reads CFBundleShortVersionString and CFBundleVersion
```

- `SunsetChannel.current(bundle:)`: `.other` in the simulator, `.testFlight` when `bundle.appStoreReceiptURL` ends in
  `sandboxReceipt`, else `.store`. Outside iOS it answers `.store`. Xcode, ad hoc and enterprise installs also have a
  `sandboxReceipt`, which is why debug builds pass `.other` themselves. The pure rule is
  `SunsetChannel.current(receiptURL:isSimulator:)`.
- `URLSessionSunsetLookupSource(bundle:locale:session:)` builds the lookup from the bundle id and
  `URLSessionSunsetLookupSource.country(for:)` (the locale's two-letter region, lowercased). The region usually
  matches the App Store storefront; if you have the storefront, pass its country to `init?(bundleID:country:)`.
- The TestFlight block (`ios.testFlight.latestBuild`, `url`, `notBefore`) is written by your release pipeline: see
  [Release pipeline: publish the TestFlight build](https://github.com/abominableenterprise/cherkit/blob/main/kotlin/sunset/README.md#release-pipeline-publish-the-testflight-build).
- Show the prompt with CherKitUI's `.sunsetPrompt(checker:channel:)`: it titles a test-build prompt "A new test build
  is available", opens TestFlight on **Update**, and remembers both answers per build with
  `SunsetTestBuildPromptRule` (see the CherKitUI README). If you show it yourself, apply that rule too.

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
