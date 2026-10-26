sunset: added

- `SunsetPrompt` has a new `latestVersion` (`AppVersion?` in Swift and Kotlin): the config's `latestVersion` for the
  platform, or `nil`/`null` when the block has none. `SunsetDecider` (and so `SunsetChecker`) fills it in. The
  initializer/constructor parameter defaults to `nil`, so existing code keeps compiling.
- CherKitUI: `.sunsetPrompt(decision:)` now remembers "Not now" for `prompt.latestVersion`, so you no longer need to
  pass `latestVersion:`. The parameter stays and, when given, still wins; with neither, the installed app version is
  used as before.
