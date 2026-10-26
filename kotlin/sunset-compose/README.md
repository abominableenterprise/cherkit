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

Give `SunsetPrompt` a checker (see the Sunset README for the fetch) and it runs the checks and shows the prompts, like
iOS's `.sunsetPrompt(checker:)`:

```kotlin
@Composable
fun Root(okHttp: OkHttpClient) {
    val context = LocalContext.current
    val checker = remember {
        // Fails open: a missing or broken config gives UpToDate and nothing is shown.
        if (isUiTest) SunsetChecker.disabled() else AndroidSunset.checker(context.applicationContext) {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(SUNSET_URL).cacheControl(CacheControl.FORCE_NETWORK).build()
                okHttp.newCall(request).execute().use { SunsetFetch.bodyIfSuccess(it.code, it.body?.string()) }
            }
        }
    }

    AppContent()
    SunsetPrompt(checker = checker)
}
```

It checks with `AndroidSunset.check(checker, context)`:

- Once at launch, on the first composition. Only once per process: recomposition, rebuilding the root or recreating
  the activity (rotation) neither fetches again nor drops a hard block.
- Again when the app comes back from the background (Lifecycle `ON_STOP`, then `ON_RESUME`), at most once per
  `minimumInterval` (1 hour by default; `Duration.ZERO` re-checks on every return). The first `ON_RESUME` and
  `ON_PAUSE` → `ON_RESUME` (a system dialog, a permission prompt) do not count.
- Never while a check is still running.

A re-check replaces the decision: a hard block goes away once the config no longer requires the update, and a soft
dialog answered with "Not now" stays hidden for that `latestVersion`. `SunsetChecker.disabled()` never shows anything.

### Check yourself

To decide when to check, pass the decision instead; this overload only displays it:

```kotlin
var sunset by remember { mutableStateOf<SunsetDecision?>(null) }
LaunchedEffect(Unit) { sunset = AndroidSunset.check(checker, context) }

SunsetPrompt(decision = sunset, onDecisionHandled = { sunset = null })
```

Both overloads take `onVisibilityChange: (Boolean) -> Unit`: `true` when the soft "Update available" dialog appears,
`false` when it goes away. An app that runs its own checks (say on every resume) can skip them while it is `true`
instead of copying the "Not now" rule. The hard block is not reported: keep checking behind it, so it goes away once
the config no longer requires the update.

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

Options, all with defaults (both overloads):

- `memory: SunsetSoftPromptMemory`: where "Not now" is stored. Default: `rememberSunsetSoftPromptMemory()`, the
  Sunset SharedPreferences file (`AndroidSunset.DEFAULT_PREFERENCES_NAME`) under
  `SunsetSoftPromptMemory.DEFAULT_KEY`. Pass `SunsetSoftPromptMemory(prefs, key)` for your own file or key; `reset()`
  forgets the answer (e.g. from a debug menu).
- `strings: SunsetStrings(...)`: every text, English by default (the same as iOS). Pass already localized text to
  override any of it. The config's custom message, when present, replaces the default body text.
- `appIcon: Painter?`: shown above the "Update required" title. Decorative: TalkBack skips it.
- `onVisibilityChange: (Boolean) -> Unit`: whether the soft dialog is showing (see above).
- `minimumInterval: Duration` (checker overload only): the least time between re-checks, 1 hour by default.

## API

| Swift (CherKitUI) | Kotlin (`com.abominableenterprise.cherkit.sunset.compose`) |
| --- | --- |
| `.sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:)` | `SunsetPrompt(checker, minimumInterval, memory, strings, appIcon, onVisibilityChange)` |
| `.sunsetPrompt(decision:latestVersion:memory:strings:appIcon:)` | `SunsetPrompt(decision, onDecisionHandled, memory, strings, appIcon, onVisibilityChange)` |
| `SunsetCheckScheduler` (internal) | `SunsetCheckScheduler` (in `:sunset`, common code) |
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
`SunsetPrompt(checker = ...)` uses `LocalLifecycleOwner` from `androidx.lifecycle:lifecycle-runtime-compose`, which
Compose `ui` 1.9.5 already brings (2.8.7 or newer); no extra dependency.
