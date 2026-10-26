sunset: added

- CherKitUI: `tint: Color?` on both `.sunsetPrompt` modifiers and on `SunsetUpdateView`. It colors the hard
  "Update required" button and accents. On iOS the block runs in its own window, which does not inherit the app's
  tint, so it showed system blue until now; that window's `tintColor` is set too. Defaults to `nil` (unchanged).
  The soft alert keeps the app's tint.
