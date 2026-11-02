# CherKitReview (Swift)

See [kotlin/review/README.md](../../kotlin/review/README.md), the module README shared by both platforms: rules, API
table and the hookups.

```swift
import CherKitReview

let review = isDebugOrTest ? .disabled : ReviewCoordinator(isEligible: { !session.isKidDevice })
review.recordSuccess()                                        // after an entry is saved
review.noteInterruption()                                     // after a paywall or error
review.setInterruptionVisible(true, source: "paywall")        // while one of your modals is up
HomeView().reviewPrompt(coordinator: review)                  // CherKitUI: settles, then asks StoreKit
```

`isEligible` is a public `var`, read on every check: for state created later (e.g. a view model), pass a closure that
reads it lazily or assign it later (`review.isEligible = { model.isReviewEligible }`); keep it cheap and main-thread safe
([details](../../kotlin/review/README.md#eligibility-that-depends-on-later-state)).

The SwiftUI adapter (`.reviewPrompt`, `.reviewInterruption`, the Sunset and What's New hookups) lives in
[CherKitUI](../CherKitUI/README.md#review).

## Optional telemetry

Pass `telemetrySink: telemetry` to `ReviewCoordinator` to use an app's `CherKitCore.TelemetrySink`.
The default is `nil`, so existing apps emit nothing. Delivery is synchronous on the main actor after state is saved.

| Domain | Integer error codes | Swift behavior |
| --- | --- | --- |
| `cherkit.review` | None | The Foundation coordinator does not call a store API and has no store failures to report. |

| Breadcrumb | When | Attributes |
| --- | --- | --- |
| `cherkit.review.prompted` | `markPrompted()` returns `true`, once per version | None |

Failed/disabled/ineligible/interrupted calls and repeat `markPrompted()` calls emit nothing. No version, modal
source, message text or user data is sent. Platform review-flow errors are outside the Swift Foundation module.

Foundation only (no UIKit/StoreKit), so it builds and tests on Linux. State lives in `UserDefaults.standard` under
`cherkit.review.*` (`UserDefaultsReviewStore(defaults:keyPrefix:)`); `InMemoryReviewStore` is for tests.
