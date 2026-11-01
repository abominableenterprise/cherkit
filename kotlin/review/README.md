# CherKit Review

The store-review ("rate this app") rules: when to ask, never too often, never over a modal. This module is the
**contract and core rules only**: no platform review API, no UI. The SwiftUI adapter (`AppStore.requestReview`) and the
Android adapter (Play In-App Review plus a Compose effect) build on it.

Swift: `Sources/CherKitReview` ([Swift usage](../../Sources/CherKitReview/README.md)), Foundation only. Kotlin:
`kotlin/review` (no runtime dependencies). Shared cases: `fixtures/review/coordinator.json`. Test with
`scripts/test.sh review`.

## Usage

An app needs about three calls; the adapter does the rest.

```kotlin
val review = if (BuildConfig.DEBUG) ReviewCoordinator.disabled()
             else AndroidReview.coordinator(context, isEligible = { !session.isKidDevice })
review.recordSuccess()                                              // e.g. after an entry is saved
review.noteInterruption()                                           // after a paywall, error or failed write
review.setInterruptionVisible(true, source = "paywall")             // while one of your modals is up (false when gone)
// Adapter (later): on a settled screen, if (review.shouldPrompt(isIdle) && review.markPrompted()) launchReviewFlow()
```

`AndroidReview.coordinator` is disabled by default in debuggable builds. Swift: `ReviewCoordinator(isEligible:)`
(state in `UserDefaults.standard`) or `.disabled`; pass `enabled: false` for debug builds and test hosts.

## Rules

- **Due** at whichever comes first: the Nth `recordSuccess()` (N = `successThreshold`, default 5, at least 1) or an
  `arm()` (e.g. a milestone). The count and the due flag persist across launches. The count runs across versions until
  the next prompt; a prompt resets it to 0.
- **At most once per app version** (`appVersion`, default the installed version string). `markPrompted()` stores the
  version. In that same version further successes and `arm()` are ignored. A later version prompts again once it is
  re-armed: by `arm()` or by N fresh successes. A due prompt that never showed (e.g. held by eligibility) carries over
  to the next version.
- **Eligibility** (`isEligible`, read on every call, e.g. not a child's device): while false, successes don't count,
  `arm()` is ignored and nothing prompts; a due prompt is held, not dropped.
- **Interruptions**:
  - `noteInterruption()` holds the prompt for `cooldown` (default 2 minutes) after the last one.
  - `setInterruptionVisible(visible, source)` tracks a **set** of visible sources: while any source is visible the
    prompt never shows, however long it stays up. Sources are independent, so hiding one never clears another; showing
    the same source twice needs one hide.
  - Hiding a visible source starts the cooldown, so clearing a modal never fires the review at once: it becomes
    eligible again on the next normal check after the cooldown (the adapter's next settle).
  - Interruptions are in memory; the prompt itself stays due through all of them.
- **Decision**: `shouldPrompt(isIdle)` is true only when due, eligible, `isIdle` (the adapter's calm, settled screen),
  no source visible and the cooldown over. The adapter calls `markPrompted()` **right before** launching the platform
  flow; it rechecks `shouldPrompt` and returns `false` (changing nothing) if it no longer holds, so a prompt that is
  never launched never consumes the version.
- **Disabled** (`disabled` / `enabled = false`, `isDisabled` is public): nothing counts, arms or prompts, interruptions
  are ignored and the store is never read or written.
- Settle delay and "is the screen calm" belong to the adapters; the core only takes `isIdle`.

## Sunset counts as an interruption

`:review` does not depend on `:sunset` (and `CherKitReview` not on `CherKitSunset`/`CherKitUI`); the automatic bridge
belongs in the platform adapters. Until then, hook it up in one line per prompt, with the well-known sources
`ReviewInterruption.SUNSET_PROMPT` / `.sunsetPrompt` (soft dialog) and `SUNSET_BLOCK` / `.sunsetBlock` (hard block):

```kotlin
SunsetPrompt(checker, onVisibilityChange = { review.setInterruptionVisible(it, ReviewInterruption.SUNSET_PROMPT) })
// Decision-based UI: you hold the decision, so report the hard block from it:
review.setInterruptionVisible(decision is SunsetDecision.HardUpdate, ReviewInterruption.SUNSET_BLOCK)
```

```swift
// Decision-based `.sunsetPrompt(decision:)`: report the hard block from the decision you hold.
.onChange(of: decision) { decision in
    if case .hardUpdate? = decision { review.setInterruptionVisible(true, source: ReviewInterruption.sunsetBlock) }
    else { review.setInterruptionVisible(false, source: ReviewInterruption.sunsetBlock) }
}
```

The SwiftUI `.sunsetPrompt` modifiers have no visibility callback yet; the iOS adapter PR wires both Sunset sources
automatically inside `CherKitUI`, which already depends on Sunset.

## Storage

Keys `cherkit.review.successCount` (Int), `cherkit.review.isDue` (Boolean) and `cherkit.review.lastPromptedVersion`
(String); a value of the wrong type counts as absent.

- Android: `AndroidReview.store(context, name = "cherkit.review", keyPrefix = "cherkit.review.")` over
  `SharedPreferencesReviewStorage`. Any other storage: implement `ReviewKeyValueStorage` and use `KeyValueReviewStore`.
- Swift: `UserDefaultsReviewStore(defaults:keyPrefix:)`, the default.
- Tests: `InMemoryReviewStore` on both platforms; or implement `ReviewStore` (`load()` / `save(state)`).

The clock is injectable (`clock: () -> Long` epoch millis in Kotlin, `now: () -> Date` in Swift).
