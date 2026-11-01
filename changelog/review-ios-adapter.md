review: added

- CherKitUI now has the iOS review prompt (it links `CherKitReview`). `.reviewPrompt(coordinator:isIdle:settleDelay:)`
  on a main screen waits until the screen has been idle for a settle delay (1.5 s by default), then calls
  `markPrompted()` right before StoreKit's `requestReview`. It runs only while the scene is active and starts over
  when the app comes back. The version counts as prompted even if StoreKit's quota shows nothing. Pass
  `ReviewCoordinator.disabled` in debug builds and test harnesses.
- `.reviewInterruption(isPresented:source:coordinator:)` reports any of your sheets or alerts, so the review never
  shows over them and waits a cooldown after them.
- `.sunsetPrompt` (both forms) has two new optional callbacks: `onVisibilityChange` (the soft alert) and
  `onPromptChange` (`SunsetPromptKind.soft`, `.hard` or `nil`, like Android). Report both prompts with
  `review.setSunsetPromptKind($0)`.
- `.whatsNew` (both forms) has a new optional `onVisibilityChange` (the sheet) and `suppressed`, which holds the sheet
  back while it is `true`, e.g. while a Sunset prompt is up. Report the sheet with the new
  `ReviewInterruption.whatsNew` (`"whatsNew"`, the same string as Kotlin's `WHATS_NEW`).
- The new parameters all default to the old behavior, so existing code needs no change.
