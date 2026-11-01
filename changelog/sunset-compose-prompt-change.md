sunset: added

- sunset-compose: `onPromptChange: (SunsetPromptKind?) -> Unit = {}` on both `SunsetPrompt` overloads reports which
  prompt is showing: `SOFT` (the "Update available" dialog), `HARD` (the "Update required" block) or `null`. Use it to
  report the hard block too, e.g. as `ReviewInterruption.SUNSET_BLOCK`. `onVisibilityChange` is unchanged (soft dialog
  only) and a trailing lambda still binds to it; code compiled against 0.3.0 still links.
