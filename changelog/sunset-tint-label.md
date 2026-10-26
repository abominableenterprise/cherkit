sunset: added

- CherKitUI: `tintLabel: Color?` on both `.sunsetPrompt` modifiers and on `SunsetUpdateView`: the hard
  "Update required" button's label color on the `tint` fill. With `nil` (the default) the label is now black or
  white from the tint's WCAG relative luminance in the current light or dark mode (white while it keeps 3:1 contrast,
  black on brighter tints), so a bright tint such as a light dark-mode accent no longer gets an unreadable white
  label. Without `tint` nothing changes.
- `SunsetChecker.isDisabled` is now public (read-only), so app tests can assert they use `SunsetChecker.disabled`.
