sunset: added

- iOS: `SunsetChannel.current()` detects the install channel (`.testFlight` from the `sandboxReceipt` App Store
  receipt, `.other` in the simulator, `.store` otherwise). A library cannot detect debug builds: pass `.other` there.
- iOS: `URLSessionSunsetLookupSource(bundle:locale:)` opts in to the App Store lookup in one line (the app's bundle
  id and the user's region, `country(for:)`).
- CherKitUI: `.sunsetPrompt(checker:channel:)` passes the channel and `CFBundleVersion` to the check. A TestFlight
  test-build prompt is a soft alert titled "A new test build is available" whose **Update** opens TestFlight; each
  build prompts at most once ("Update" and "Not now" both remember it, under
  `SunsetTestBuildPromptRule.defaultMemoryKey`). Both the `checker:` and `decision:` forms apply it, and
  `onPromptChange` reports it as `.soft`.
- CherKitUI: new `SunsetStrings` texts `testBuildTitle`, `testBuildMessage`, `testBuildUpdateHint` (with
  `softTitle(for:)`, `softUpdateHint(for:)`); `SunsetSoftPromptMemory` gains `testBuildKey`, `rememberedTestBuild`
  and `remember(_:on:version:)`, and `reset()` now also forgets the remembered test build.
