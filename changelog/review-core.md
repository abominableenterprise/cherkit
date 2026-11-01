review: added

- New module Review (`CherKitReview`, `:review`, JitPack artifact `review`): the store-review ("rate this app") rules.
  Contract and core only: no platform review API or UI yet (adapters follow).
- `ReviewCoordinator`: due on the 5th `recordSuccess()` (configurable) or an `arm()`; at most once per app version;
  held while the session is not eligible; `noteInterruption()` holds it for a 2-minute cooldown.
- `setInterruptionVisible(_:source:)`: while any of your modals (a set of sources) is visible the review never shows;
  hiding one starts the cooldown, so it never fires at once. Sunset's prompts hook in with
  `ReviewInterruption.sunsetPrompt` / `.sunsetBlock` (see the module README).
- `shouldPrompt(isIdle:)`, then `markPrompted()` right before launching the platform review; `disabled` and
  `enabled: false` for debug and test builds.
- Stores: `UserDefaultsReviewStore` (Swift), `AndroidReview.store` over SharedPreferences (Kotlin) and
  `InMemoryReviewStore`, keys under `cherkit.review.*`.
