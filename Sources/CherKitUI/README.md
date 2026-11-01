# CherKitUI

SwiftUI views for CherKit modules, one folder per module (`Sunset/`, `WhatsNew/`, `Review/`; `Shared/` holds helpers
they share). The only target that may import SwiftUI.
Every file is wrapped in `#if canImport(SwiftUI)` so Linux builds stay green; rendering is verified on macOS only.
A module PR edits only its own folder here.

## Sunset

Attach `.sunsetPrompt(checker:)` to your root view. It runs the checks and shows the prompt:

```swift
import CherKitSunset
import CherKitUI
import SwiftUI

@main
struct ExampleApp: App {
    private let sunset = ProcessInfo.processInfo.arguments.contains("-uiTesting")
        ? SunsetChecker.disabled   // UI tests: always `upToDate`, no network
        : SunsetChecker(url: URL(string: "https://example.github.io/myapp/sunset.json")!)

    var body: some Scene {
        WindowGroup {
            ContentView()
                .sunsetPrompt(checker: sunset, tint: .accentColor)
        }
    }
}
```

When it checks:

- Once at launch, on the first appear. Only once per process, even if the root view appears again or is rebuilt.
- Again when the app returns from the background (scene phase `.background`, then `.active`), at most once per
  `minimumInterval` (default 1 hour; `0` re-checks on every return). The initial `.active`, and `.inactive` →
  `.active` (Control Center, a system alert), do not check, so the launch fetch never runs twice.
- Never while a check is still running.
- After a failed check, i.e. the fetch failed, timed out or did not parse and no usable config is cached (e.g. a
  fresh install with a dropped connection), once more about 3 seconds later. A failed check does not count toward
  `minimumInterval`: if the retry fails too, the next return from the background re-checks whatever the interval.

The checker fetches with a timeout and falls back to the cached config. It fails open, so a missing or broken config
gives `.upToDate` and nothing is shown. A re-check replaces the decision, so a hard block goes away once the config
stops requiring the update. Optional parameters: `installedVersion:` (default `CFBundleShortVersionString`),
`locale:` (default `Locale.preferredLanguages`), `minimumInterval:`, plus `memory:`, `strings:`, `appIcon:`, `tint:`
and `tintLabel:` below.

To fetch on your own schedule, pass the decision instead. This modifier only displays it:

```swift
@State private var sunset: SunsetDecision?

ContentView()
    .sunsetPrompt(decision: $sunset, tint: .accentColor)
    .task { sunset = await checker.check() }
```

The decision's prompt carries the config's `latestVersion`, so there is nothing else to pass: "Not now" is remembered
for that version exactly. In UI tests and on the simulator, use `SunsetChecker.disabled` (see the Sunset module
README); it always answers `upToDate`, so nothing is shown.

- `softUpdate`: a dismissible alert, "Update available", with **Update** (opens the store URL) and **Not now**.
  "Not now" is remembered for `prompt.latestVersion` (the config's `latestVersion`), so it is not shown again on
  every launch; a newer `latestVersion` shows it again. If the config has no `latestVersion`, the installed app
  version is used, so "Not now" lasts until the app is updated.
- `hardUpdate`: `SunsetUpdateView` ("Update required") over everything, with no way to dismiss it: a
  window above the app's windows on iOS (so it also covers sheets and covers the app is presenting), an overlay on
  macOS (the content below is disabled).
- `upToDate` or `nil`: nothing.

Options, all with defaults:

- `latestVersion: String?` (`decision:` form only): overrides the version "Not now" is remembered for. Defaults to
  `nil`, which uses `prompt.latestVersion`; you rarely need it.
- `memory: SunsetSoftPromptMemory(defaults:key:)`: where "Not now" is stored (`UserDefaults.standard` under
  `SunsetSoftPromptMemory.defaultKey`). Pass an app group suite or your own key.
- `strings: SunsetStrings(...)`: every text, English by default. Pass already localized text to override any of it.
  The config's custom message, when present, replaces the default body text.
- `appIcon: Image?`: shown above the "Update required" title.
- `tint: Color?`: the "Update required" button and accents, e.g. `.accentColor`. On iOS the block runs in its own
  window, which does not inherit the app's tint, so without it the button is system blue. The soft alert always uses
  the app's tint.
- `tintLabel: Color?`: the "Update required" button's label color on the `tint` fill. `nil` (the default) picks
  black or white from the tint's WCAG relative luminance, resolved in the current light or dark mode: white while it
  keeps at least 3:1 contrast (the bold-text minimum, so e.g. system blue keeps its white label), black on brighter
  tints such as a light dark-mode accent. Ignored without `tint`, which keeps the system look.

- `onVisibilityChange: ((Bool) -> Void)?`: `true` when the soft alert appears, `false` when it goes away (answered,
  cleared, or the view goes away). Not called for the hard block.
- `onPromptChange: ((SunsetPromptKind?) -> Void)?`: which prompt is showing, `.soft`, `.hard` or `nil`, whenever that
  changes (never for the initial `nil`). Like Android's `SunsetPrompt(onPromptChange:)`. Use it to report both prompts
  to Review (see [Review](#review)).
- `onFirstCheckComplete: (() -> Void)?` (`checker:` form only): called once per process when the launch check's first
  attempt has resolved, whatever the outcome (up to date, soft, hard, or a failed fetch that fails open). The
  `onPromptChange` / `onVisibilityChange` calls for that first decision always run before it, so the kind you stored
  is never stale. `SunsetChecker.disabled` calls it on the first appear; re-checks on a return from the background and
  the quick retry after a failed check never call it again. Use it to show Sunset before What's New (see
  [Review](#review)).

`SunsetUpdateView(prompt:strings:appIcon:tint:tintLabel:)` can also be used on its own. It scrolls, so it does not clip on small
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
  `Locale.preferredLanguages`), `strings:`, plus `suppressed:` and `onVisibilityChange:` below.
- `suppressed: Bool`: while `true` the sheet waits, or is taken away again without counting as a dismissal (so
  `markSeen()` does not run); it shows once this turns `false`. Pass `suppressed: sunsetKind != nil`, fed from
  `.sunsetPrompt(onPromptChange:)`, so neither Sunset prompt (soft alert or hard block) stacks with the sheet; keep
  it `true` until `.sunsetPrompt(onFirstCheckComplete:)` fires to always show Sunset first (see [Review](#review)).
- `onVisibilityChange: ((Bool) -> Void)?`: `true` when the sheet is presented, `false` when it goes away, e.g. to
  report it to Review as `ReviewInterruption.whatsNew`.

To drive it yourself, pass the entries and a dismiss action; it presents whenever `entries` becomes a new non-empty
list and calls `onDismiss` once per dismissal:

```swift
ContentView().whatsNew(entries: entries) { whatsNew.markSeen() }
```

It takes the same `suppressed:` and `onVisibilityChange:` parameters.

`WhatsNewEntry.version` is an `AppVersion` from `CherKitCore`. With Swift's `MemberImportVisibility` upcoming feature
on, add `import CherKitCore` to use its members such as `entry.version.description`, or use
`String(describing: entry.version)`.

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

## Review

The App Store review prompt for the `ReviewCoordinator` rules in `CherKitReview` (see the
[Review module README](../../kotlin/review/README.md)). Keep one coordinator for the app's lifetime, inject it, and
attach `.reviewPrompt(coordinator:)` to your main screens (not to a sheet):

```swift
import CherKitReview
import CherKitUI

let review = isTestHarness ? .disabled : ReviewCoordinator(enabled: !isDebugBuild)

HomeView()
    .reviewPrompt(coordinator: review, isIdle: !isEditing)
```

- It runs only while the view is on screen, its scene is `.active` and `isIdle` is true. It waits `settleDelay`
  (1.5 s by default) first, then checks `shouldPrompt(isIdle:)`, and checks again every `settleDelay` (after any
  interruption cooldown) while the screen stays idle. Returning to the app or `isIdle` turning true starts over.
- `markPrompted()` runs right before StoreKit's `requestReview`. **The version counts as prompted even if StoreKit's
  quota shows nothing**: StoreKit gives no signal either way. `requestReview` never throws, so failures stay quiet.
- `ReviewCoordinator.disabled` (or `enabled: false`) does nothing. Pass it in debug builds and under every test
  harness, not only XCTest: UI-test launch arguments, fixtures and previews too. `AnalyticsTestHost.isRunningTests`
  (`CherKitAnalytics`) detects XCTest if you already link it:

```swift
let isTestHarness = AnalyticsTestHost.isRunningTests || ProcessInfo.processInfo.arguments.contains("-uiTesting")
let review = isTestHarness ? .disabled : ReviewCoordinator(isEligible: { !session.isKidDevice })
```

Report your own sheets, alerts and banners, so the review never shows over them and waits a cooldown after them:

```swift
.sheet(isPresented: $showPaywall) { PaywallView() }
.reviewInterruption(isPresented: showPaywall, source: "paywall", coordinator: review)
```

Sunset and What's New hook in through their callbacks. Show Sunset first on a cold start: hold What's New until
Sunset's launch check has decided, then while either Sunset prompt is up.

```swift
@State private var sunsetKind: SunsetPromptKind? = nil
@State private var sunsetSettled = false

ContentView()
    .sunsetPrompt(
        checker: sunset,
        onPromptChange: {
            sunsetKind = $0
            review.setSunsetPromptKind($0)   // .soft → ReviewInterruption.sunsetPrompt, .hard → .sunsetBlock
        },
        onFirstCheckComplete: { sunsetSettled = true }
    )
    .whatsNew(checker: whatsNew, suppressed: !sunsetSettled || sunsetKind != nil, onVisibilityChange: {
        review.setInterruptionVisible($0, source: ReviewInterruption.whatsNew)
    })
    .reviewPrompt(coordinator: review)
```

While the first fetch runs, `onPromptChange` says nothing, so `suppressed: sunsetKind != nil` alone would let the sheet
appear first and step aside when a prompt comes. `onFirstCheckComplete` runs after `onPromptChange` has reported that
first decision, so `sunsetKind` is already set when `sunsetSettled` turns true. The Sunset fetch has a timeout (5 s by
default, `SunsetChecker(url:timeout:)`) and fails open, so What's New is never held back forever; with
`SunsetChecker.disabled` it is not held at all.

With the `decision:` form you run the check yourself, so set the flag after it:

```swift
@State private var sunsetDecision: SunsetDecision?
@State private var sunsetChecked = false
@State private var sunsetKind: SunsetPromptKind?

ContentView()
    .sunsetPrompt(decision: $sunsetDecision, onPromptChange: { sunsetKind = $0 })
    .whatsNew(checker: whatsNew, suppressed: !sunsetChecked || sunsetKind != nil)
    .task {
        sunsetDecision = await sunset.check()
        sunsetChecked = true
    }
```

This form checks once; re-check on a return from the background yourself if you rely on that.

The soft alert alone, in one line:
`.sunsetPrompt(checker: c, onVisibilityChange: { review.setInterruptionVisible($0, source: ReviewInterruption.sunsetPrompt) })`.
