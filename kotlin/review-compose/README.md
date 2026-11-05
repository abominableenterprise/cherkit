# CherKit Review Compose

The Android adapter for [Review](../review/README.md): Play In-App Review plus Jetpack Compose effects. It decides
nothing itself: `ReviewCoordinator` holds the rules, this module finds a calm moment and hands off to Play.

```kotlin
dependencies {
    implementation("com.github.abominableenterprise.cherkit:review-compose:v0.9.0") // brings :review
}
```

Code: `kotlin/review-compose`. Test with `scripts/test.sh review-compose`.

**Why a module of its own:** `:review` depends only on the tiny Core contract, so apps without Play Services (other stores,
AOSP builds, the JVM) can use the rules alone. This module adds `com.google.android.play:review-ktx` and Compose,
the same way `sunset-compose` sits on top of `sunset`. Its `jvm` artifact carries only the platform-free launch order
(`ReviewLauncher`, `ReviewFlow`) and the settle loop (`ReviewSettle`).

## Use it

```kotlin
val review = remember { AndroidReview.coordinator(context.applicationContext) } // off in debuggable builds
// Eligibility from state created later (e.g. a ViewModel): read lazily on every check, cheap and main-thread safe.
review.isEligible = { viewModel.isReviewEligible }

// On your main screens, not over a modal:
ReviewPromptEffect(review, isIdle = !isEditing)

// For each of your own dialogs, sheets and banners:
ReviewInterruptionEffect(review, source = "paywall", visible = showPaywall)
ReviewInterruptionEffect(review, source = ReviewInterruption.WHATS_NEW, visible = showWhatsNew)
```

### `ReviewPromptEffect(coordinator, isIdle = true, settleDelay = 1.5.seconds, requirePlayInstall = true, telemetrySink = null, onResult = {})`

- Runs only while the lifecycle is `RESUMED` and `isIdle` is true. It waits `settleDelay` first, then checks
  `shouldPrompt()`; while that is false it checks again every `settleDelay` (after any interruption cooldown), so a
  prompt that becomes due on this screen still shows.
- Starts over on every `ON_RESUME` and every time `isIdle` turns true; leaving the app, a system dialog or a busy
  screen cancels the wait.
- Finds the Activity from `LocalContext` (through `ContextWrapper`s); without one (a preview) it does nothing.
- A disabled coordinator does nothing.
- `onResult` gets the `ReviewLaunchResult` that ended each run (for logging).

### `ReviewInterruptionEffect(coordinator, source, visible)`

Calls `setInterruptionVisible(true, source)` while `visible`, and `setInterruptionVisible(false, source)` when it
turns false or the effect leaves the composition. Sources that flicker (an error banner toggling quickly) are safe:
the source never stays stuck visible, and every hide restarts the cooldown, so the review waits a full cooldown after
the last one. Each source is tracked separately; give each modal its own string.

### `PlayReview.launch(activity, coordinator, requirePlayInstall = true, isIdle = { true }, telemetrySink = null, manager = { ... })`

What `ReviewPromptEffect` calls; a suspend function for apps that pick the moment themselves. In order:

1. `coordinator.shouldPrompt(isIdle())`, else `SKIPPED`.
2. With `requirePlayInstall` (the default), the installer check: `PlayInstall.installerPackageName(context)`
   (`getInstallSourceInfo(...).installingPackageName` on API 30+, `getInstallerPackageName` before) must be
   `com.android.vending`. Otherwise `NOT_PLAY_INSTALL`, consuming nothing: Play never shows its review UI to a build
   it did not install (sideloaded with adb or a file, an emulator install from the IDE, another store).
   **Play testing tracks (internal, closed and open testing) install through `com.android.vending` and pass.**
3. `requestReviewFlow()`. If it fails (no Play Store, offline, any Play error): `REQUEST_FAILED`, and the version is
   **not** consumed, so the due prompt stays due for the next check.
4. `markPrompted()` (it rechecks `shouldPrompt`; a modal that opened during the request gives `SKIPPED`), right
   before `launchReviewFlow()`: `LAUNCHED`, or `LAUNCH_FAILED` if Play throws.

It never throws (except cancellation). **Quota:** `launchReviewFlow` does not say whether Play showed anything. On a
Play-installed build where Play still shows nothing because of its quota, the version counts as prompted all the same;
the API gives no way to tell.

Testing: pass `manager = { FakeReviewManager(context) }` (from Play), or implement `ReviewFlow<T>` and call
`ReviewLauncher.launch(coordinator, flow, isIdle = isIdle)`, which is plain Kotlin.

## Optional telemetry

Pass `telemetrySink: TelemetrySink? = null` to `ReviewPromptEffect`, `PlayReview.launch` or
`ReviewLauncher.launch`. It defaults to no diagnostics. Pass the same sink to the coordinator to also capture its
prompt breadcrumb:

```kotlin
val review = remember { AndroidReview.coordinator(context.applicationContext, telemetrySink = telemetry) }
ReviewPromptEffect(review, isIdle = !isEditing, telemetrySink = telemetry)
```

| Domain | Code | Failure |
| --- | --- | --- |
| `cherkit.review` | `1` | `requestReviewFlow` failed (including failure to create the Play manager) |
| `cherkit.review` | `2` | `launchReviewFlow` failed |

Each failed attempt reports exactly once, with **no attributes**. The launcher owns request/launch failures;
`PlayReview` reports only manager creation failure, so the Compose, Play and launcher layers never duplicate errors.
Cancellation and skipped/non-Play installs are not failures and emit no error. A request failure leaves the prompt
due; a launch failure has already consumed the version. Sink exceptions are ignored and cannot replace the review
result or prevent the launch. The final callback parameters remain last for trailing-lambda calls.

| Breadcrumb | When | Attributes |
| --- | --- | --- |
| `cherkit.review.prompted` | The coordinator's `markPrompted()` succeeds, even if Play later fails to launch | None |

Only the coordinator emits the breadcrumb, never the adapters. It is absent if `markPrompted()` fails or returns
`false`; it does not guarantee that Play displayed a UI. No exception message, URL, review info, version,
interruption source or user data is sent. Hidden deprecated overloads preserve all previous public function and
composable signatures for compiled callers; new source calls use the optional sink signatures.

## Sunset counts as an interruption

`SunsetPrompt(onPromptChange = ...)` (sunset-compose) reports which prompt is showing, so both Sunset sources hook up
in one line:

```kotlin
SunsetPrompt(checker = checker, onPromptChange = { kind ->
    review.setInterruptionVisible(kind == SunsetPromptKind.SOFT, ReviewInterruption.SUNSET_PROMPT)
    review.setInterruptionVisible(kind == SunsetPromptKind.HARD, ReviewInterruption.SUNSET_BLOCK)
})
```

`onVisibilityChange` is unchanged and still reports the soft dialog only; use `onPromptChange` for the hard block.

## API

| Kotlin (`com.abominableenterprise.cherkit.review.compose`) | What |
| --- | --- |
| `ReviewPromptEffect(coordinator, isIdle, settleDelay, requirePlayInstall, telemetrySink, onResult)` | settle, check, launch Play |
| `ReviewInterruptionEffect(coordinator, source, visible)` | report a modal |
| `PlayReview.launch(activity, coordinator, requirePlayInstall, isIdle, telemetrySink, manager)` | one launch attempt |
| `PlayInstall.installerPackageName(context)`, `isPlayInstall(context)`, `PLAY_STORE_PACKAGE` | the installer check |
| `ReviewLaunchResult` | `SKIPPED`, `NOT_PLAY_INSTALL`, `REQUEST_FAILED`, `LAUNCHED`, `LAUNCH_FAILED` |
| `ReviewFlow<T>`, `ReviewLauncher.launch(coordinator, flow, telemetrySink, isIdle)` | the launch order over any store API |
| `ReviewSettle.awaitThenLaunch(coordinator, delay, launch)`, `DEFAULT_DELAY` | the settle loop |

## Versions

`com.google.android.play:review-ktx` 2.0.2, androidx Compose `runtime`/`ui` 1.9.5 (floors; your app's newer Compose
wins) and `kotlinx-coroutines-core`. `LocalLifecycleOwner` comes from `lifecycle-runtime-compose`, which Compose `ui`
already brings. Needs the Kotlin Compose compiler plugin in your app.
