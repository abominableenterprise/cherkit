# CherKit WhatsNew Compose

Jetpack Compose UI for [WhatsNew](../whatsnew/README.md) on Android, so apps don't each rebuild the "What's new"
sheet and the version history. It mirrors CherKitUI's `.whatsNew` and `WhatsNewHistoryView` on iOS, with the same
behavior and English text. Android only (the `jvm` artifact carries just the strings).

```kotlin
dependencies {
    implementation("com.github.abominableenterprise.cherkit:whatsnew-compose:v0.4.0") // brings :whatsnew
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
- `onVisibilityChange: ((Boolean) -> Unit)?`: `true` when the sheet appears, `false` when it goes away.

### Order with Sunset and the review prompt

Pass `suppressed = sunsetKind != null`, fed from `SunsetPrompt`'s `onPromptChange` (`SOFT`, `HARD` or `null`), so
the "What's new" sheet never stacks with either Sunset prompt: while the soft "Update available" dialog or the hard
"Update required" block shows, the sheet waits (or steps aside) and comes back afterwards. `onVisibilityChange`
reports the soft dialog only, so prefer `onPromptChange`.

`onPromptChange` says nothing while Sunset's first check is still fetching, so the sheet can appear first and step
aside when a prompt comes. To always show Sunset first, keep `suppressed` true until that first check finishes. Run
the check yourself with the `decision` overload, which tells you when it is done (it fails open, so it always
finishes):

```kotlin
var sunsetDecision by remember { mutableStateOf<SunsetDecision?>(null) }
var sunsetChecked by remember { mutableStateOf(false) }
var sunsetKind by remember { mutableStateOf<SunsetPromptKind?>(null) }
LaunchedEffect(Unit) {
    sunsetDecision = AndroidSunset.check(sunset, context)
    sunsetChecked = true
}

SunsetPrompt(sunsetDecision, onDecisionHandled = { sunsetDecision = null }, onPromptChange = { sunsetKind = it })
WhatsNewPrompt(checker = whatsNew, suppressed = !sunsetChecked || sunsetKind != null)
```

To keep a store-review prompt from appearing over the sheet, report it as an interruption:

```kotlin
WhatsNewPrompt(
    checker = whatsNew,
    suppressed = sunsetKind != null,
    onVisibilityChange = { review.setInterruptionVisible(it, ReviewInterruption.WHATS_NEW) },
)
```

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
list shows "No release notes yet.". It has no top bar: put it in your own `Scaffold`, with `strings.historyTitle`
("Version History") as the title if you like.

```kotlin
val strings = WhatsNewStrings()
val history = remember { whatsNew.history(AndroidWhatsNew.preferredLocales()) }
Scaffold(topBar = { TopAppBar(title = { Text(strings.historyTitle) }, navigationIcon = { BackButton() }) }) { padding ->
    WhatsNewHistoryScreen(history, strings = strings, contentPadding = padding)
}
```

## API

| Swift (CherKitUI) | Kotlin (`com.abominableenterprise.cherkit.whatsnew.compose`) |
| --- | --- |
| `.whatsNew(checker:installedVersion:locale:strings:)` | `WhatsNewPrompt(checker, installedVersion, locales, strings, suppressed, onVisibilityChange)` |
| `.whatsNew(entries:strings:onDismiss:)` | `WhatsNewPrompt(entries, onDismiss, strings, suppressed, onVisibilityChange)` |
| `WhatsNewSheet(entries:strings:onContinue:)` | `WhatsNewSheet(entries, strings, onContinue)` |
| `WhatsNewHistoryView(entries:strings:)` | `WhatsNewHistoryScreen(entries, modifier, strings, contentPadding)` |
| `WhatsNewStrings(...)` | `WhatsNewStrings(...)`: `title`, `continueButton`, `versionTitle`, `historyTitle`, `installedBadge`, `noNotes`, `emptyHistory` |

`suppressed` and `onVisibilityChange` are Android additions.

Accessibility: the sheet title and each version are headings for TalkBack, each bullet is its own node (the "•" is
not read), and both screens scroll, so nothing clips at the largest font scale. `@Preview`s (light, dark, largest
font) live in `WhatsNewPreviews.kt`.

## Versions

The same Compose floors as [Sunset Compose](../sunset-compose/README.md#versions): androidx Compose
`runtime`/`ui`/`foundation` 1.9.5 and `material3` 1.4.0, no BOM. Gradle picks the newer Compose your app already
uses. Needs `compileSdk` 35+ and the Kotlin Compose compiler plugin in your app.
