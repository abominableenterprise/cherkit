whatsnew-compose: fixed

- `WhatsNewPrompt`'s `onVisibilityChange` no longer gets a false `false` then `true` when the caller passes a new
  lambda instance on recomposition while the sheet is showing (e.g. an inline
  `{ review.setInterruptionVisible(it, ReviewInterruption.WHATS_NEW) }`), which restarted the review cooldown. The
  `false` still goes to the callback that got the `true`.
