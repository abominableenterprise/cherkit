sunset-compose: added

- New Android-only module `com.github.abominableenterprise.cherkit:sunset-compose` (Jetpack Compose, Material 3):
  `SunsetPrompt(decision, onDecisionHandled)` shows a dismissible "Update available" dialog for `SoftUpdate` and a
  full-screen "Update required" block that back cannot dismiss for `HardUpdate`, like CherKitUI's `.sunsetPrompt`.
- "Not now" is remembered in SharedPreferences for `prompt.latestVersion` (else the installed version), by
  `SunsetSoftPromptMemory`; `SunsetSoftPromptRule` is the same rule as on iOS (`fixtures/sunset/soft-prompt.json`).
- `SunsetStrings` has the iOS English defaults, all overridable. `SunsetUpdateScreen` and `SunsetSoftPromptDialog`
  can be used on their own. Needs androidx Compose 1.9.5+ / material3 1.4.0+ (newer versions in the app win).
