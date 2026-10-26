sunset: added

- CherKitUI: `.sunsetPrompt(decision: $decision, latestVersion:)` shows a `SunsetDecision`: a dismissible "Update
  available" alert with "Update" and "Not now" for `softUpdate`, and a full-screen "Update required" block with no
  way to dismiss it for `hardUpdate` (on iOS a window above the app's own windows, so it also covers open sheets; an overlay on macOS). `upToDate` shows nothing. The
  views never fetch; pass in the decision from the Sunset checker or `SunsetDecider`.
- "Not now" is remembered per `latestVersion` in `UserDefaults` (`SunsetSoftPromptMemory(defaults:key:)`, injectable),
  so the soft prompt does not return on every launch for the same version. The rule is
  `SunsetSoftPromptRule.shouldShow(_:version:dismissedVersion:)`.
- `SunsetUpdateView(prompt:strings:appIcon:)` is the hard block on its own: optional app icon, the config's custom
  message or a default line, and an "Update" button that opens the store URL. It scrolls at large Dynamic Type sizes.
- All text is English by default and overridable through `SunsetStrings`.
- The scaffold's `SunsetPlaceholderView` is removed.
