sunset: added

- `AndroidSunset.check(checker, context, locale = AndroidSunset.preferredLocales())` runs the launch check for the
  running app (its `versionName`, `Build.VERSION.SDK_INT` and the user's locales). It needs no import beyond
  `AndroidSunset`.
- The `SunsetChecker.check(context, locale)` extension is deprecated: its import
  (`com.abominableenterprise.cherkit.sunset.check`) was easy to confuse with Kotlin's `check(Boolean)`. It still works;
  the IDE's quick fix replaces `checker.check(context)` with `AndroidSunset.check(checker, context)`.
