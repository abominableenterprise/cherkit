# CherKitReview (Swift)

See [kotlin/review/README.md](../../kotlin/review/README.md), the module README shared by both platforms: rules, API
table and the hookups.

```swift
import CherKitReview

let review = isDebugOrTest ? .disabled : ReviewCoordinator(isEligible: { !session.isKidDevice })
review.recordSuccess()                                        // after an entry is saved
review.noteInterruption()                                     // after a paywall or error
review.setInterruptionVisible(true, source: "paywall")        // while one of your modals is up
// The SwiftUI adapter (a later release) checks shouldPrompt(isIdle:) on a settled screen, then calls
// markPrompted() right before AppStore.requestReview.
```

Foundation only (no UIKit/StoreKit), so it builds and tests on Linux. State lives in `UserDefaults.standard` under
`cherkit.review.*` (`UserDefaultsReviewStore(defaults:keyPrefix:)`); `InMemoryReviewStore` is for tests.
