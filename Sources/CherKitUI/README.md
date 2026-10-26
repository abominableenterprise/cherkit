# CherKitUI

SwiftUI views for CherKit modules, one folder per module (`Sunset/`, `WhatsNew/`). The only target that may import SwiftUI.
Every file is wrapped in `#if canImport(SwiftUI)` so Linux builds stay green; rendering is verified on macOS only.
A module PR edits only its own folder here.

## Sunset

The views only display a `SunsetDecision` (`upToDate`, `softUpdate`, `hardUpdate`); they never fetch anything. Get the
decision from `SunsetChecker` in `CherKitSunset` (or `SunsetDecider.decide`), then attach `.sunsetPrompt` to your
root view:

```swift
import CherKitSunset
import CherKitUI
import SwiftUI

struct RootView: View {
    private let checker = SunsetChecker(url: URL(string: "https://example.github.io/myapp/sunset.json")!)
    @State private var sunset: SunsetDecision?

    var body: some View {
        ContentView()
            .sunsetPrompt(decision: $sunset)
            .task {
                // Fetches with a timeout, falls back to the cached config, decides.
                // It fails open, so a missing or broken config gives `.upToDate` and nothing is shown.
                sunset = await checker.check()
            }
    }
}
```

If you know the config's `latestVersion`, pass it (`.sunsetPrompt(decision: $sunset, latestVersion: "1.4.0")`) so
"Not now" is remembered for that version exactly.

- `softUpdate`: a dismissible alert, "Update available", with **Update** (opens the store URL) and **Not now**.
  "Not now" is remembered for `latestVersion` (the config's `latestVersion`, as a string), so it is not shown again on
  every launch; a newer `latestVersion` shows it again. Without `latestVersion` the installed app version is used, so
  "Not now" lasts until the app is updated.
- `hardUpdate`: `SunsetUpdateView` ("Update required") over everything, with no way to dismiss it: a
  window above the app's windows on iOS (so it also covers sheets and covers the app is presenting), an overlay on
  macOS (the content below is disabled).
- `upToDate` or `nil`: nothing.

Options, all with defaults:

- `memory: SunsetSoftPromptMemory(defaults:key:)`: where "Not now" is stored (`UserDefaults.standard` under
  `SunsetSoftPromptMemory.defaultKey`). Pass an app group suite or your own key.
- `strings: SunsetStrings(...)`: every text, English by default. Pass already localized text to override any of it.
  The config's custom message, when present, replaces the default body text.
- `appIcon: Image?`: shown above the "Update required" title.

`SunsetUpdateView(prompt:strings:appIcon:)` can also be used on its own. It scrolls, so it does not clip on small
screens or at the largest Dynamic Type sizes. `SunsetSoftPromptRule.shouldShow(_:version:dismissedVersion:)` is the
pure "show the soft prompt?" rule the modifier uses.

## WhatsNew

The views only display what `WhatsNewChecker` (`CherKitWhatsNew`) returns; everything is bundled, so there is no
network and no loading state. Keep one checker for the app's lifetime and attach `.whatsNew(checker:)` to your root
view:

```swift
import CherKitUI
import CherKitWhatsNew
import SwiftUI

@main
struct ExampleApp: App {
    private let whatsNew = WhatsNewChecker()   // bundle's whatsnew/ folder + UserDefaults.standard

    var body: some Scene {
        WindowGroup {
            ContentView().whatsNew(checker: whatsNew)
        }
    }
}
```

- On first appear it calls `onLaunch()` once. If that returns notes, it presents `WhatsNewSheet`: each release newest
  first with its date and bullets (one section per release when there are several) and a **Continue** button.
- `markSeen()` runs when the sheet is dismissed (Continue or swipe), never before, so notes the user never saw come
  back next launch.
- Optional parameters: `installedVersion:` (default `CFBundleShortVersionString`), `locale:` (default
  `Locale.preferredLanguages`) and `strings:`.

To drive it yourself, pass the entries and a dismiss action; it presents whenever `entries` becomes a new non-empty
list and calls `onDismiss` once per dismissal:

```swift
ContentView().whatsNew(entries: entries) { whatsNew.markSeen() }
```

The full history, for Settings (it sets its own navigation title; push it inside your `NavigationStack`):

```swift
NavigationLink("Version History") {
    WhatsNewHistoryView(entries: whatsNew.history())
}
```

The installed release carries a "You're on this version" badge; a release without bullets shows "Bug fixes and
improvements.".

- `strings: WhatsNewStrings(...)`: every text, English by default (`title`, `continueButton`, `versionTitle`,
  `historyTitle`, `installedBadge`, `noNotes`, `emptyHistory`). Pass already localized text to override any of it.
- `WhatsNewSheet(entries:strings:onContinue:)` can be presented on its own; without `onContinue`, Continue calls the
  environment's `dismiss`.
- Both views scroll, so nothing clips on small screens or at the largest Dynamic Type sizes; headings and bullets are
  separate VoiceOver elements.
