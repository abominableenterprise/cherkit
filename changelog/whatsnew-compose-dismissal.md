whatsnew-compose: fixed

- Checker-based `WhatsNewPrompt` accepts optional `onDismiss`, called exactly once immediately after `markSeen()` on user dismissal. Use it to clear pending-notes gates; `onVisibilityChange` is presentation-only and also reports suppression and leaving composition without consuming notes.
- Retain both previous compiled checker-based signatures as hidden compatibility overloads.
