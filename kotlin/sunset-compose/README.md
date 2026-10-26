# CherKit Sunset Compose

Jetpack Compose UI for [Sunset](../sunset/README.md) on Android, so apps don't each rebuild the update screens. It
mirrors CherKitUI's `.sunsetPrompt` on iOS: a dismissible "Update available" dialog, a full-screen "Update required"
block and the same "Not now" memory. Android only (the `jvm` artifact carries just the pure rule and strings).

```kotlin
dependencies {
    implementation("com.github.abominableenterprise.cherkit:sunset-compose:v0.2.0") // brings :sunset
}
```

Code: `kotlin/sunset-compose`. Shared test cases: `fixtures/sunset/soft-prompt.json`. Test with
`scripts/test.sh sunset-compose`.

## Use it

The prompt only displays a decision; get one from `AndroidSunset.checker(...)` (see the Sunset README for the fetch):

```kotlin
@Composable
fun Root(okHttp: OkHttpClient) {
    val context = LocalContext.current
    var sunset by remember { mutableStateOf<SunsetDecision?>(null) }
    LaunchedEffect(Unit) {
        // Fails open: a missing or broken config gives UpToDate and nothing is shown.
        val checker = if (isUiTest) SunsetChecker.disabled() else AndroidSunset.checker(context) {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(SUNSET_URL).cacheControl(CacheControl.FORCE_NETWORK).build()
                okHttp.newCall(request).execute().use { SunsetFetch.bodyIfSuccess(it.code, it.body?.string()) }
            }
        }
        sunset = checker.check(context)
    }

    AppContent()
    SunsetPrompt(decision = sunset, onDecisionHandled = { sunset = null })
}
```

Put `SunsetPrompt` inside your `MaterialTheme`: the dialogs use Material 3 and take the theme's colors and type.

- `SoftUpdate`: an `AlertDialog`, "Update available", with **Update** (opens `prompt.storeUrl` through
  `LocalUriHandler`) and **Not now**. "Not now", back and a tap outside are remembered for `prompt.latestVersion`
  (the config's `latestVersion`), so the dialog does not come back for that version; a newer `latestVersion` shows it
  again. Without one, the installed `versionName` is used, so "Not now" lasts until the app is updated. Either answer
  calls `onDecisionHandled`.
- `HardUpdate`: `SunsetUpdateScreen` ("Update required") in a full-screen `Dialog` that back and outside taps
  cannot dismiss. The text scrolls at large font scales and the **Update** button stays pinned at the bottom.
  "Update" opens the store and the block stays; `onDecisionHandled` is not called.
- `UpToDate` or `null`: nothing.

Options, all with defaults:

- `memory: SunsetSoftPromptMemory`: where "Not now" is stored. Default: `rememberSunsetSoftPromptMemory()`, the
  Sunset SharedPreferences file (`AndroidSunset.DEFAULT_PREFERENCES_NAME`) under
  `SunsetSoftPromptMemory.DEFAULT_KEY`. Pass `SunsetSoftPromptMemory(prefs, key)` for your own file or key; `reset()`
  forgets the answer (e.g. from a debug menu).
- `strings: SunsetStrings(...)`: every text, English by default (the same as iOS). Pass already localized text to
  override any of it. The config's custom message, when present, replaces the default body text.
- `appIcon: Painter?`: shown above the "Update required" title. Decorative: TalkBack skips it.

## API

| Swift (CherKitUI) | Kotlin (`com.abominableenterprise.cherkit.sunset.compose`) |
| --- | --- |
| `.sunsetPrompt(decision:latestVersion:memory:strings:appIcon:)` | `SunsetPrompt(decision, onDecisionHandled, memory, strings, appIcon)` |
| `SunsetUpdateView(prompt:strings:appIcon:)` | `SunsetUpdateScreen(prompt, modifier, strings, appIcon)` |
| system `.alert` | `SunsetSoftPromptDialog(prompt, onUpdate, onNotNow, strings)` |
| `SunsetSoftPromptRule.shouldShow(_:version:dismissedVersion:)` | `SunsetSoftPromptRule.shouldShow(decision, version, dismissedVersion)`, `version(prompt, installedVersion)` |
| `SunsetSoftPromptMemory(defaults:key:)` | `SunsetSoftPromptMemory(prefs, key)`, `SunsetSoftPromptMemory.from(context)` |
| `SunsetStrings(...)` | `SunsetStrings(...)` (no `updateHint`: TalkBack has no hints) |

Accessibility: both titles are headings for TalkBack, the icon has no content description, and the hard block's
button never scrolls off screen. `@Preview`s for both prompts (light, dark, largest font) live in `SunsetPreviews.kt`.

## Versions

It depends on androidx Compose `runtime`/`ui`/`foundation` 1.9.5 and `material3` 1.4.0 (no BOM, no activity-compose).
These are floors: Gradle picks the newer Compose your app already uses, including the androidx Compose that Compose
Multiplatform 1.11.x brings. Needs `compileSdk` 35+ and the Kotlin Compose compiler plugin in your app.
