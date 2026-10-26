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
