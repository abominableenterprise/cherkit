# CherKit WhatsNew Compose

Jetpack Compose UI for [WhatsNew](../whatsnew/README.md) on Android, so apps don't each rebuild the "What's new"
sheet and the version history. It mirrors CherKitUI's `.whatsNew` and `WhatsNewHistoryView` on iOS, with the same
behavior and English text. Android only (the `jvm` artifact carries just the strings).

```kotlin
dependencies {
    implementation("com.github.abominableenterprise.cherkit:whatsnew-compose:v0.9.0") // brings :whatsnew
}
```

Code: `kotlin/whatsnew-compose`. Test with `scripts/test.sh whatsnew-compose`.

## Use it

Keep one checker for the app's lifetime and put `WhatsNewPrompt` in your root composable, inside your
`MaterialTheme`, like iOS's `.whatsNew(checker:)`:

```kotlin
@Composable
fun Root() {
    val context = LocalContext.current
    val whatsNew = remember { AndroidWhatsNew.checker(context.applicationContext) } // assets/whatsnew/ + SharedPreferences
    var sunsetKind by remember { mutableStateOf<SunsetPromptKind?>(null) }

    AppContent()
    SunsetPrompt(checker = sunset, onPromptChange = { sunsetKind = it })
    WhatsNewPrompt(checker = whatsNew, suppressed = sunsetKind != null)
}
```

- On the first composition it calls `checker.onLaunch(installedVersion, locales)` (off the main thread), once per
  process: recomposition, rebuilding the root and recreating the activity (rotation) neither ask again nor drop
  notes the user has not dismissed yet.
- If that returns notes, it shows `WhatsNewSheet`: each release newest first with its version, date and bullets (one
  section per release when there are several) and a **Continue** button. The releases scroll; the button stays below.
- `checker.markSeen()` runs when the user dismisses the sheet (Continue, back, swipe down or a tap outside), never
  before, so notes the user never saw come back next launch.

Options, all with defaults:

- `installedVersion: String`: the installed `versionName` (`AndroidWhatsNew.installedVersion(context)`).
- `locales: List<String>`: `AndroidWhatsNew.preferredLocales()`.
- `strings: WhatsNewStrings(...)`: every text, English by default (the same as iOS). Pass already localized text to
  override any of it.
- `suppressed: Boolean`: while `true` the sheet waits; it shows once this turns `false`. A sheet already showing is
  taken away while suppressed (not a dismissal: nothing is marked seen) and comes back afterwards.
- `onLaunchCheckComplete: ((hasNotes: Boolean) -> Unit)?`: called once the launch check has answered (see below).
  `null` by default.
- `onVisibilityChange: ((Boolean) -> Unit)?`: `true` when the sheet appears, `false` when it goes away. An inline
  lambda is fine: a new instance on recomposition sends nothing, and the `false` goes to the callback that got the
  `true`.

### Know when the launch check is done

`onVisibilityChange` says nothing until the sheet shows, so "not visible" can mean "no notes" or "not known yet".
Pass `onLaunchCheckComplete: ((hasNotes: Boolean) -> Unit)?` (checker overload only) to learn when the launch
`onLaunch` has returned, with `hasNotes`: whether there are notes to show (`false` when there is nothing new, and when
`onLaunch` failed open). It runs once per process, with the same guard as the `onLaunch` call itself:
recomposition, rebuilding the root and recreating the activity (rotation) never call it again. A composition that
passes the callback only after the check returned still gets it once.

- **Not held by `suppressed`:** the launch check runs while the sheet is suppressed (e.g. behind Sunset); only the
  sheet waits. So the signal comes even while Sunset holds the sheet back.
- **Order:** with notes and `suppressed = false`, the sheet's `onVisibilityChange(true)` runs first and
  `onLaunchCheckComplete(true)` right after, in the same frame, so a `whatsNewVisible` flag set from
  `onVisibilityChange` is already `true` when this runs (a gate reading both never opens in between). While
  suppressed, `onLaunchCheckComplete(true)` comes first and the sheet's `onVisibilityChange(true)` later, when it
  shows.

Use it like Sunset's `onFirstCheckComplete`, e.g. to keep Play's in-app update sheet
([update-compose](../update-compose/README.md)) back until "What's new" has answered and its sheet is gone (see the
recipe below). Keep the flag it sets per process (an `object`), not in `remember` (it starts `false` again after a
rotation, and this is not called again) or `rememberSaveable` (a stale `true` after process death).

### Order with Sunset and the review prompt

Pass `suppressed = sunsetKind != null`, fed from `SunsetPrompt`'s `onPromptChange` (`SOFT`, `HARD` or `null`), so
the "What's new" sheet never stacks with either Sunset prompt: while the soft "Update available" dialog or the hard
"Update required" block shows, the sheet waits (or steps aside) and comes back afterwards. `onVisibilityChange`
reports the soft dialog only, so prefer `onPromptChange`.

`onPromptChange` says nothing while Sunset's first check is still fetching, so on a cold start the sheet can appear
first and then step aside when a prompt comes. To always show Sunset first, also keep `suppressed` true until Sunset's
`onFirstCheckComplete` runs: once per process, when the launch check has finished with any outcome (up to date, soft,
hard or a failed fetch), and after the `onPromptChange` call for that decision, so `sunsetKind` is already up to date.
This is the recommended root setup:

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

The Sunset fetch has a timeout (`SunsetChecker.timeout`, 5 seconds by default) and a failed or timed-out fetch still
completes the check, so the sheet is never held back for good. `SunsetChecker.disabled()` (UI tests) completes it at
once. Keep `sunsetSettled` in process-scoped state (an `object` like `LaunchGate`, or your app's own singleton):
`onFirstCheckComplete` runs once per process, so a plain `remember` would hold the sheet back after a rotation, and
`rememberSaveable` would restore a stale `true` after process death, before the new launch check has answered. See
[Sunset Compose](../sunset-compose/README.md#know-when-the-launch-check-is-done) for the details.

To keep a store-review prompt from appearing over the sheet, report it as an interruption:

```kotlin
WhatsNewPrompt(
    checker = whatsNew,
    suppressed = !LaunchGate.sunsetSettled || sunsetKind != null,
    onVisibilityChange = { review.setInterruptionVisible(it, ReviewInterruption.WHATS_NEW) },
)
```

### Order with Play's in-app update

To show Play's update sheet ([update-compose](../update-compose/README.md)) only after Sunset and "What's new", gate
it on both launch checks, Sunset's running checks and the sheet:

```kotlin
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
    onVisibilityChange = {
        whatsNewVisible = it
        review.setInterruptionVisible(it, ReviewInterruption.WHATS_NEW)
    },
)
PlayUpdatePrompt(
    update,
    suppressed = !LaunchGate.sunsetSettled || sunsetKind != null || sunsetChecking ||
        !LaunchGate.whatsNewSettled || whatsNewVisible,
)
```

While Sunset holds the "What's new" sheet, `whatsNewVisible` is still `false`, but the Sunset terms of the gate hold
Play's sheet too. Once they clear, the "What's new" sheet shows in the same frame (its `suppressed` turns `false`),
before `PlayUpdatePrompt` asks Play again. `whatsNewVisible` can stay in `remember`: a recreated `WhatsNewPrompt`
reports the sheet still showing.

This module does not depend on `:review`; use whichever interruption your Review version offers.

### Entries you got yourself

To call `onLaunch` on your own schedule, pass the entries and a dismiss action, like iOS's
`.whatsNew(entries:onDismiss:)`. It shows whenever `entries` becomes a new non-empty list (an equal list is not shown
again while it stays composed) and calls `onDismiss` once per dismissal. Empty entries show nothing.

```kotlin
var entries by remember { mutableStateOf(emptyList<WhatsNewEntry>()) }
LaunchedEffect(Unit) {
    entries = withContext(Dispatchers.IO) {
        whatsNew.onLaunch(AndroidWhatsNew.installedVersion(context), AndroidWhatsNew.preferredLocales())
    }
}

WhatsNewPrompt(entries, onDismiss = { whatsNew.markSeen(); entries = emptyList() })
```

It takes the same `strings`, `suppressed` and `onVisibilityChange`.

### Why a bottom sheet

`WhatsNewSheet` is a Material 3 `ModalBottomSheet`, fully expanded (no half-height stop): it slides up over the app
and can be swiped down, the closest Android match to the iOS sheet. A `Dialog` would need its own dismiss gestures
and look like an alert. Use `WhatsNewSheet(entries, strings, onContinue)` directly only to present it yourself:
`onContinue` runs once, after the sheet has slid away, for Continue, back, swipe or a tap outside.

## Version history

`WhatsNewHistoryScreen` lists every release for Settings, like iOS's `WhatsNewHistoryView`. The installed release
carries a "You're on this version" badge, a release without bullets shows "Bug fixes and improvements." and an empty
list shows "No release notes yet.". Get the list with `AndroidWhatsNew.history(context)`: read-only (it never appends
to the install history or marks notes seen), so Settings needs no checker of its own. It reads the bundled assets, so
load it off the main thread.

It has no top bar: put it in your own `Scaffold`, with `strings.historyTitle` ("Version History") as the title if you
like:

```kotlin
val context = LocalContext.current
val strings = WhatsNewStrings()
val history by produceState(emptyList<WhatsNewHistoryEntry>()) {
    value = withContext(Dispatchers.IO) { AndroidWhatsNew.history(context.applicationContext) }
}
Scaffold(topBar = { TopAppBar(title = { Text(strings.historyTitle) }, navigationIcon = { BackButton() }) }) { padding ->
    WhatsNewHistoryScreen(history, strings = strings, contentPadding = padding)
}
```

When your screen has no title of its own, pass `showTitle = true`: `strings.historyTitle` then shows as a heading
(for TalkBack too) at the top of the list and scrolls with it. It is off by default. There is still no top bar or
back button; navigation stays with the app.

```kotlin
WhatsNewHistoryScreen(history, strings = strings, showTitle = true)
```

## API

| Swift (CherKitUI) | Kotlin (`com.abominableenterprise.cherkit.whatsnew.compose`) |
| --- | --- |
| `.whatsNew(checker:installedVersion:locale:strings:)` | `WhatsNewPrompt(checker, installedVersion, locales, strings, suppressed, onLaunchCheckComplete, onVisibilityChange)` |
| `.whatsNew(entries:strings:onDismiss:)` | `WhatsNewPrompt(entries, onDismiss, strings, suppressed, onVisibilityChange)` |
| `WhatsNewSheet(entries:strings:onContinue:)` | `WhatsNewSheet(entries, strings, onContinue)` |
| `WhatsNewHistoryView(entries:strings:)` | `WhatsNewHistoryScreen(entries, modifier, strings, contentPadding, showTitle)` |
| `WhatsNewStrings(...)` | `WhatsNewStrings(...)`: `title`, `continueButton`, `versionTitle`, `historyTitle`, `installedBadge`, `noNotes`, `emptyHistory` |

`suppressed`, `onLaunchCheckComplete`, `onVisibilityChange` and `showTitle` are Android additions (iOS shows `historyTitle` as the navigation
title).

Accessibility: the sheet title and each version are headings for TalkBack, each bullet is its own node (the "•" is
not read), and both screens scroll, so nothing clips at the largest font scale. `@Preview`s (light, dark, largest
font) live in `WhatsNewPreviews.kt`.

## Versions

The same Compose floors as [Sunset Compose](../sunset-compose/README.md#versions): androidx Compose
`runtime`/`ui`/`foundation` 1.9.5 and `material3` 1.4.0, no BOM. Gradle picks the newer Compose your app already
uses. Needs `compileSdk` 35+ and the Kotlin Compose compiler plugin in your app.
