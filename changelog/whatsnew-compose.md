whatsnew-compose: added

- New Android module `whatsnew-compose` (`com.github.abominableenterprise.cherkit:whatsnew-compose`), the Compose
  counterpart of CherKitUI's WhatsNew views. `WhatsNewPrompt(checker = ...)` at the root calls `onLaunch` once per
  process, shows the "What's new" bottom sheet when there are notes and calls `markSeen()` only when the user
  dismisses it (Continue, back or swipe).
- `suppressed = true` makes the sheet wait (pass `suppressed = sunsetVisible` so it never stacks with Sunset's
  soft dialog; see the module README for showing Sunset first);
  `onVisibilityChange` reports when it appears and goes away, e.g. to a review coordinator.
- `WhatsNewPrompt(entries, onDismiss)` shows entries you got yourself, like iOS's `.whatsNew(entries:onDismiss:)`.
- `WhatsNewHistoryScreen(entries)` lists every release for Settings with the "You're on this version" badge;
  `WhatsNewStrings` has the same English defaults as iOS.
