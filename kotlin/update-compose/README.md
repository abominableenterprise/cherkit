# CherKit Update Compose

The Android "update available" adapter: Google Play In-App Updates (the **FLEXIBLE** flow: Play's sheet, a background
download, then "Restart to install") plus a Jetpack Compose effect.

```kotlin
dependencies {
    implementation("com.github.abominableenterprise.cherkit:update-compose:v0.6.0")
}
```

Code: `kotlin/update-compose`. Test with `scripts/test.sh update-compose`.

**Why a module of its own:** it adds `com.google.android.play:app-update-ktx` (Play Services) and
`androidx.activity:activity-compose`, so `:sunset` and `:sunset-compose` stay free of Play Services, the same way
`review-compose` sits next to `review`. It does not depend on `:sunset` or `:review`. Its `jvm` artifact carries only
the platform-free launch order (`UpdateLauncher`, `UpdateFlow`), the "Not now" store interface and the session gate.

## Use it

Once, in your root composable (it needs an `ActivityResultRegistryOwner`, i.e. a `ComponentActivity`):

```kotlin
// Top level, per process like the launch callbacks: survives rotation, starts over after process death.
object LaunchGate {
    var sunsetSettled by mutableStateOf(false)
    var whatsNewSettled by mutableStateOf(false)
}

// In the root composable:
val update = rememberPlayUpdateState()
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
    onVisibilityChange = { review.setInterruptionVisible(it, UpdateInterruption.SOURCE) },
)

// "Restart to install" (it restarts the app at once: flush pending local writes first, see below):
if (update.isDownloaded) {
    LaunchedEffect(Unit) {
        val answer = snackbar.showSnackbar("Update downloaded", actionLabel = "Restart", duration = SnackbarDuration.Indefinite)
        if (answer == SnackbarResult.ActionPerformed) update.completeUpdate()
    }
}
```

Each part of the gate holds Play's sheet back for one reason: Sunset's launch check has not answered yet
(`!LaunchGate.sunsetSettled`), a Sunset prompt or block is up (`sunsetKind != null`), a Sunset re-check is in flight
and may still find a hard block (`sunsetChecking`, from `onCheckingChange`), the "What's new" launch check has not
answered yet (`!LaunchGate.whatsNewSettled`, from `onLaunchCheckComplete`), or the "What's new" sheet is up
(`whatsNewVisible`). Add your own modals and the review prompt as needed. The two `LaunchGate` flags live in a
process-wide `object`, not `rememberSaveable` (see "Precedence with Sunset" below).

### `PlayUpdatePrompt(state, suppressed, enabled, requirePlayInstall, store, onVisibilityChange, onDownloaded, onResult)`

- **When:** on every `ON_RESUME` and every time `suppressed` changes, while the lifecycle is `RESUMED`.
- **Who:** with `requirePlayInstall` (the default), only builds the Play Store installed
  (`getInstallSourceInfo(...).installingPackageName` on API 30+, `getInstallerPackageName` before, must be
  `com.android.vending`). **Play testing tracks (internal, closed and open testing) install through
  `com.android.vending` and pass.** Sideloads (adb, a file), IDE and emulator installs and other stores get nothing,
  without asking Play. `enabled` defaults to `AndroidUpdate.isEnabledByDefault(context)`: off in debuggable builds.
- **What:** asks Play `appUpdateInfo`. When `updateAvailability` is `UPDATE_AVAILABLE` and `FLEXIBLE` is allowed, it
  starts `startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(FLEXIBLE))` through a
  `rememberLauncherForActivityResult` launcher.
- **At most once per session** (per process): once the sheet started, later resumes, recompositions and rotations
  never show it again.
- **`suppressed`:** while `true` the sheet waits (nothing is consumed) and shows once it turns `false`. Pass it while
  any other prompt is up or may still come: the Sunset prompt and its checks
  (`!LaunchGate.sunsetSettled || sunsetKind != null || sunsetChecking`, see below), What's New and its launch check
  (`!LaunchGate.whatsNewSettled || whatsNewVisible`), the review prompt, your own modals.
- **"Not now":** cancelling Play's sheet is remembered in `store` for that `availableVersionCode`, so the same build is
  never offered again; a newer versionCode is. Default store: `rememberUpdatePromptStore()`, the SharedPreferences
  file `cherkit.update` (`AndroidUpdate.DEFAULT_PREFERENCES_NAME`), key `cherkit.update.declinedVersionCode`.
  Accepting, or a Play failure, remembers nothing.
- **Downloaded:** Play's install listener (registered while the prompt is composed) and the check on every
  `ON_RESUME` set `state.isDownloaded` and call `onDownloaded` once. So an update downloaded in an earlier session
  (the user left before installing) shows "Restart to install" again on the next resume, even when `suppressed` or
  the sheet was already shown this session. `state.completeUpdate()` installs it (Play restarts the app).
- **Restart to install:** `state.completeUpdate()` kills and restarts the app process right away; nothing in progress
  gets to finish. If your app queues local writes (e.g. an offline database or a sync queue), flush or confirm them
  before calling it, or disable or delay the "Restart to install" button until nothing is pending. The download
  stays ready meanwhile: `isDownloaded` remains `true`, also on later resumes.
- **Fails quietly:** a missing Play Store, offline, any Play exception, or no `ActivityResultRegistryOwner` (a
  preview): nothing shows, no exception. `onResult` gets an `UpdateLaunchResult` for each attempt, e.g. for logging.
- **`onVisibilityChange`:** `true` when Play's sheet opens, `false` when it closes (or the prompt leaves the
  composition).

### Review interruptions

Report the sheet to the Review coordinator ([review-compose](../review-compose/README.md)) so the store-review prompt
never shows over it. The source string is `UpdateInterruption.SOURCE` (`"update"`):

```kotlin
PlayUpdatePrompt(update, onVisibilityChange = { review.setInterruptionVisible(it, UpdateInterruption.SOURCE) })
```

(With review-compose: `ReviewInterruptionEffect(review, UpdateInterruption.SOURCE, visible = update.isVisible)` does
the same.)

### Precedence with Sunset

- **The Sunset hard block always wins.** Pass
  `suppressed = !LaunchGate.sunsetSettled || sunsetKind != null || sunsetChecking` (from
  `SunsetPrompt(onPromptChange =, onFirstCheckComplete =, onCheckingChange =)`, as in the recipe above) so Play's
  sheet never opens over the Sunset prompt or block. `sunsetKind != null` alone is not enough: it stays `null` while
  a Sunset check runs (up to `SunsetChecker.timeout`, 5 s), and Play's `appUpdateInfo` is a local call that usually
  answers first, so the sheet would open and stay above a hard block that arrives later (the prompt cannot close
  Play's activity). `!LaunchGate.sunsetSettled` covers the launch check; `sunsetChecking` covers the re-check on
  return from the background (which is also when `PlayUpdatePrompt` asks Play again) and the quick retry after a
  failed fetch: `onCheckingChange` reports `true` while any check runs and `false` after its `onPromptChange`, so a
  hard block found by the re-check is already in `sunsetKind` when the gate opens.
  `onFirstCheckComplete` also runs after a failed fetch, so an unreachable config still releases the sheet. Keep
  `sunsetSettled` in process-scoped state (an `object`, or your app's own singleton), not `rememberSaveable`:
  `onFirstCheckComplete` runs once per process, so `remember` would hold the sheet back for good after a rotation,
  and `rememberSaveable` would restore a stale `true` after process death, before the new process's launch check
  has answered (see [sunset-compose](../sunset-compose/README.md#know-when-the-launch-check-is-done)).
- **Config `latestVersion` soft prompt vs. Play:** on a Play install, prefer the Play flow and don't show both. Leave
  `android.latestVersion` out of the hosted config (keep `minimumVersion` for hard blocks): Play knows about the new
  release on its own, and the Play flow downloads it in place. If you keep `latestVersion` for sideloaded or
  other-store builds, the Sunset soft dialog shows first and the `suppressed` gate above holds the Play sheet
  until it closes; a "Not now" there still leaves the Play sheet free to ask once for the same release. To have only
  one ask, hide the Sunset soft prompt on Play installs yourself (e.g. pass `SunsetDecision`s through only when the
  installer is not Play).

## Testing

`rememberPlayUpdateState(manager = { FakeAppUpdateManager(it) })` runs everything against Play's fake
(`setUpdateAvailable(code)`, `userAcceptsUpdate()`, `downloadCompletes()`, ...); pass `requirePlayInstall = false` and
`enabled = true` on test builds. Or implement `UpdateFlow<T>` and call `UpdateLauncher.launch(flow, store, session)`,
which is plain Kotlin.

## API

| Kotlin (`com.abominableenterprise.cherkit.update.compose`) | What |
| --- | --- |
| `PlayUpdatePrompt(state, suppressed, enabled, requirePlayInstall, store, onVisibilityChange, onDownloaded, onResult)` | check and start the flexible flow |
| `rememberPlayUpdateState(manager)`, `PlayUpdateState` (`isDownloaded`, `isVisible`, `completeUpdate()`) | state for your UI, "Restart to install" |
| `UpdateInterruption.SOURCE` | `"update"`, for `ReviewCoordinator.setInterruptionVisible` |
| `UpdatePromptStore`, `SharedPreferencesUpdatePromptStore(prefs, key)`, `InMemoryUpdatePromptStore`, `rememberUpdatePromptStore()` | "Not now" per versionCode |
| `AndroidUpdate.promptStore(context, name, key)`, `isEnabledByDefault(context)`, `DEFAULT_PREFERENCES_NAME` | Android wiring |
| `UpdateLaunchResult` | `DISABLED`, `NOT_PLAY_INSTALL`, `CHECK_FAILED`, `DOWNLOADED`, `ALREADY_PROMPTED`, `NO_UPDATE`, `DECLINED_BEFORE`, `SUPPRESSED`, `START_FAILED`, `STARTED` |
| `UpdateFlowResult` | `ACCEPTED`, `CANCELLED`, `FAILED` |
| `UpdateFlow<T>`, `UpdateStatus`, `UpdateLauncher.launch(flow, store, session, isAllowed)`, `finish(result, versionCode, store)` | the launch order over any store API |
| `UpdateSession` (`shared`) | the once-per-session gate |

## Versions

`com.google.android.play:app-update-ktx` 2.1.0, `androidx.activity:activity-compose` 1.7.0 and androidx Compose
`runtime`/`ui` 1.9.5 (floors; your app's newer versions win) and `kotlinx-coroutines-core`. Needs the Kotlin Compose
compiler plugin in your app.
