sunset: added

- CherKitUI: `.sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:)` runs the
  Sunset check for you. It checks once at launch (once per process), again only when the app returns from the
  background (`.background` then `.active`, not `.inactive` then `.active`), never while a check is running, and at
  most once per `minimumInterval` (default 1 hour). Pass `SunsetChecker.disabled` in UI tests. Replace your own
  `scenePhase`/`didBecomeActive` re-check with it; it never fetches twice at cold launch.
  `.sunsetPrompt(decision:)` is unchanged.
