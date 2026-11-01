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

Foundation only (no UIKit/StoreKit), so it builds and tests on Linux. State lives in `UserDefaults.standard` under
`cherkit.review.*` (`UserDefaultsReviewStore(defaults:keyPrefix:)`); `InMemoryReviewStore` is for tests.
