# CherKit Sunset Compose

Jetpack Compose UI for [Sunset](../sunset/README.md) on Android, so apps don't each rebuild the update screens. It
mirrors CherKitUI's `.sunsetPrompt` on iOS: a dismissible "Update available" dialog, a full-screen "Update required"
block and the same "Not now" memory. Android only (the `jvm` artifact carries just the pure rule and strings).

```kotlin
dependencies {
    implementation("com.github.abominableenterprise.cherkit:sunset-compose:v0.8.0") // brings :sunset
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

It checks with the same app version, OS version and locales as `AndroidSunset.check(checker, context)`, through
`checker.checkReportingFailure(...)` so it knows when a check failed:

- Once at launch, on the first composition. Only once per process: recomposition, rebuilding the root or recreating
  the activity (rotation) neither fetches again nor drops a hard block.
- Again when the app comes back from the background (Lifecycle `ON_STOP`, then `ON_RESUME`), at most once per
  `minimumInterval` (1 hour by default; `Duration.ZERO` re-checks on every return). The first `ON_RESUME` and
  `ON_PAUSE` → `ON_RESUME` (a system dialog, a permission prompt) do not count.
- Never while a check is still running.
- After a failed check, i.e. the fetch failed, timed out or did not parse and no usable config is cached (e.g. a
  fresh install with a dropped connection), once more about 3 seconds later. A failed check does not count toward
  `minimumInterval`: if the retry fails too, the next return from the background (`ON_STOP`, then `ON_RESUME`)
  re-checks whatever the interval. After a successful check the interval applies again.

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

To know which prompt is showing, including the hard block, pass `onPromptChange: (SunsetPromptKind?) -> Unit` (both
overloads): `SunsetPromptKind.SOFT` for the soft dialog, `HARD` for the hard block, `null` when neither shows (also
when `SunsetPrompt` leaves the composition). It is called on every change (soft to hard directly, without a `null` in
between) and never for the initial `null`. For example, to hold back the store-review prompt
([review-compose](../review-compose/README.md)):

```kotlin
SunsetPrompt(checker = checker, onPromptChange = { kind ->
    review.setInterruptionVisible(kind == SunsetPromptKind.SOFT, ReviewInterruption.SUNSET_PROMPT)
    review.setInterruptionVisible(kind == SunsetPromptKind.HARD, ReviewInterruption.SUNSET_BLOCK)
})
```

### Know when the launch check is done

`onPromptChange` says nothing while the launch check is still fetching, so `null` can mean "no prompt" or "not known
yet". Pass `onFirstCheckComplete: (() -> Unit)?` (checker overload only) to learn when the launch check's first
attempt has finished, whatever it found: up to date, a soft or hard update, or a failed fetch (which fails open). It
is called at most once per process: recomposition, rebuilding the root, recreating the activity and later re-checks
(on return from the background, or the quick retry after a failure) never call it again. `SunsetChecker.disabled()`
calls it at once. Any `onPromptChange` and `onVisibilityChange` call for that first decision runs before it, so once
it runs the prompt kind you hold is the launch's answer, not a stale `null`. The fetch has a timeout
(`SunsetChecker.timeout`, 5 seconds by default), so it always comes and never holds the app back for long.

To show Sunset before the "What's new" sheet ([whatsnew-compose](../whatsnew-compose/README.md)) on a cold start:

```kotlin
// Top level, per process like onFirstCheckComplete: survives rotation, starts over after process death.
object LaunchGate { var sunsetSettled by mutableStateOf(false) }

// In the root composable:
var sunsetKind by remember { mutableStateOf<SunsetPromptKind?>(null) }

SunsetPrompt(checker = sunset, onPromptChange = { sunsetKind = it }, onFirstCheckComplete = { LaunchGate.sunsetSettled = true })
WhatsNewPrompt(
    checker = whatsNew,
    suppressed = !LaunchGate.sunsetSettled || sunsetKind != null,
    onVisibilityChange = { review.setInterruptionVisible(it, ReviewInterruption.WHATS_NEW) },
)
```

Keep `sunsetSettled` in process-scoped state (an `object` like `LaunchGate`, or your app's own singleton), the same
scope as `onFirstCheckComplete`, which runs once per process. Not `remember`: after a rotation (the activity and its
composition are recreated, the process is not) the callback is not called again, so a plain `remember` would start
`false` and hold the sheet back for good. Not `rememberSaveable` either: if the process is killed in the background
and restored, a saved `true` would come back before the new process's launch check has answered, so the sheet (or
Play's update sheet, see [update-compose](../update-compose/README.md)) could open ahead of a hard block. A
process-wide flag starts `false` in the new process and turns `true` when its own launch check finishes.
`sunsetKind` can stay in `remember`: the recreated `SunsetPrompt` reports the prompt still showing. Keep the root
composable alive for the activity (not inside a navigation destination that leaves the composition), or hoist
`sunsetKind` into your app's own state.

### Know when a check is running

`onFirstCheckComplete` covers the launch check only. Later checks (the re-check on return from the background and
the quick retry after a failed fetch) can also find a hard block, and `onPromptChange` stays quiet until they answer.
Pass `onCheckingChange: ((Boolean) -> Unit)?` (checker overload only) to know while any check runs: `true` when a
check starts (launch, re-check or retry), `false` when it has finished with any outcome (up to date, soft, hard, a
failed fetch or an exception). Any `onPromptChange` and `onVisibilityChange` call for that check's result (and
`onFirstCheckComplete` for the launch check) runs before the `false`, so once it comes the prompt kind you hold is
current. Each `true` gets one `false`, to the same callback, also when `SunsetPrompt` leaves the composition
mid-check; a check still running when it is composed again (e.g. after a rotation) is reported `true` again.
`SunsetChecker.disabled()` never reports `true`. An inline lambda is fine: a new instance on recomposition sends
nothing. `null` by default.

Use it to hold back something that cannot be taken away once shown, like Play's in-app update sheet
([update-compose](../update-compose/README.md)), which asks Play again on the same return from the background that
starts Sunset's re-check:

```kotlin
// Top level, per process: survives rotation, starts over after process death.
object LaunchGate {
    var sunsetSettled by mutableStateOf(false)
    var whatsNewSettled by mutableStateOf(false)
}

// In the root composable:
var sunsetKind by remember { mutableStateOf<SunsetPromptKind?>(null) }
var sunsetChecking by remember { mutableStateOf(false) }
var whatsNewVisible by remember { mutableStateOf(false) }

SunsetPrompt(
    checker = sunset,
    onPromptChange = { sunsetKind = it },
    onFirstCheckComplete = { LaunchGate.sunsetSettled = true },
    onCheckingChange = { sunsetChecking = it },
)
WhatsNewPrompt(
    checker = whatsNew,
    suppressed = !LaunchGate.sunsetSettled || sunsetKind != null,
    onLaunchCheckComplete = { LaunchGate.whatsNewSettled = true },
    onVisibilityChange = { whatsNewVisible = it },
)
PlayUpdatePrompt(
    update,
    suppressed = !LaunchGate.sunsetSettled || sunsetKind != null || sunsetChecking ||
        !LaunchGate.whatsNewSettled || whatsNewVisible,
)
```

`sunsetChecking` can stay in `remember`: a recreated `SunsetPrompt` reports a check that is still running.

Put `SunsetPrompt` inside your `MaterialTheme`: the dialogs use Material 3 and take the theme's colors and type.
The hard block's **Update** button is a Material `Button`, so its label uses the theme's `onPrimary` on `primary`;
there is no separate tint parameter (iOS's `tint:` exists because the block runs in its own window there).

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
- `onPromptChange: (SunsetPromptKind?) -> Unit`: which prompt is showing, soft, hard or none (see above).
- `minimumInterval: Duration` (checker overload only): the least time between re-checks, 1 hour by default.
- `onFirstCheckComplete: (() -> Unit)?` (checker overload only): called once per process when the launch check has
  finished (see above). `null` by default.
- `onCheckingChange: ((Boolean) -> Unit)?` (checker overload only): `true` while any check runs, `false` once its
  result is reported (see above). `null` by default.

## API

| Swift (CherKitUI) | Kotlin (`com.abominableenterprise.cherkit.sunset.compose`) |
| --- | --- |
| `.sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:)` | `SunsetPrompt(checker, minimumInterval, memory, strings, appIcon, onPromptChange, onFirstCheckComplete, onCheckingChange, onVisibilityChange)` |
| `.sunsetPrompt(decision:latestVersion:memory:strings:appIcon:)` | `SunsetPrompt(decision, onDecisionHandled, memory, strings, appIcon, onPromptChange, onVisibilityChange)` |
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
