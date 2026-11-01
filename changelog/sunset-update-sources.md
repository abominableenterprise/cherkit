sunset: added

- Update sources: besides the config's `latestVersion`, a soft prompt can now come from the App Store lookup (iOS
  release builds, opt in with `SunsetChecker(..., lookup:)` and `URLSessionSunsetLookupSource`) and from a new optional
  `ios.testFlight` config block (`latestBuild`, `url`, `notBefore`, `message`) for TestFlight installs. The lookup
  offers the higher of the two versions, respects the store's `minimumOsVersion`, and links the config's `storeURL`
  (else the lookup's `trackViewUrl`). Hard blocks still come from the config's `minimumVersion` only.
- New inputs with defaults that keep today's behaviour: `channel` (`SunsetChannel.store` / `.testFlight` / `.other`;
  Kotlin `STORE` / `TEST_FLIGHT` / `OTHER`) and `installedBuild` on `SunsetChecker.check` and `SunsetDecider.decide`.
- `SunsetPrompt` gains `kind` (`SunsetUpdateKind.update` / `.testBuild`) and `latestBuild`; `SunsetTestBuildPromptRule`
  shows a test-build prompt at most once per build ("Not now" and "Update" both remember it, under its own key).
- New types: `SunsetBuildNumber`, `SunsetTestFlightConfig`, `SunsetLookupResult`, `SunsetLookupSource`,
  `SunsetPromptAction`. Kotlin keeps the old `SunsetPrompt`, `SunsetPlatformConfig`, `SunsetChecker` and
  `SunsetDecider` signatures as hidden deprecated overloads, so existing binaries still link.
