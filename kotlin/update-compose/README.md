# CherKit Update Compose

The Android "update available" adapter: Google Play In-App Updates (the **FLEXIBLE** flow: Play's sheet, a background
download, then "Restart to install") plus a Jetpack Compose effect. Available from 0.6.0.

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
val update = rememberPlayUpdateState()
var sunsetKind by remember { mutableStateOf<SunsetPromptKind?>(null) }

SunsetPrompt(checker = sunset, onPromptChange = { sunsetKind = it })
PlayUpdatePrompt(
    update,
    suppressed = sunsetKind != null || showWhatsNew,
    onVisibilityChange = { review.setInterruptionVisible(it, UpdateInterruption.SOURCE) },
)

// "Restart to install":
if (update.isDownloaded) {
    LaunchedEffect(Unit) {
        val answer = snackbar.showSnackbar("Update downloaded", actionLabel = "Restart", duration = SnackbarDuration.Indefinite)
        if (answer == SnackbarResult.ActionPerformed) update.completeUpdate()
    }
}
```

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
  any other prompt is up: the Sunset prompt (`sunsetKind != null`), What's New, the review prompt, your own modals.
- **"Not now":** cancelling Play's sheet is remembered in `store` for that `availableVersionCode`, so the same build is
  never offered again; a newer versionCode is. Default store: `rememberUpdatePromptStore()`, the SharedPreferences
  file `cherkit.update` (`AndroidUpdate.DEFAULT_PREFERENCES_NAME`), key `cherkit.update.declinedVersionCode`.
  Accepting, or a Play failure, remembers nothing.
- **Downloaded:** Play's install listener (registered while the prompt is composed) and the check on every
  `ON_RESUME` set `state.isDownloaded` and call `onDownloaded` once. So an update downloaded in an earlier session
  (the user left before installing) shows "Restart to install" again on the next resume, even when `suppressed` or
  the sheet was already shown this session. `state.completeUpdate()` installs it (Play restarts the app).
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

- **The Sunset hard block always wins.** Pass `suppressed = sunsetKind != null` (from `SunsetPrompt(onPromptChange =)`)
  so Play's sheet never opens over the Sunset prompt or block.
- **Config `latestVersion` soft prompt vs. Play:** on a Play install, prefer the Play flow and don't show both. Leave
  `android.latestVersion` out of the hosted config (keep `minimumVersion` for hard blocks): Play knows about the new
  release on its own, and the Play flow downloads it in place. If you keep `latestVersion` for sideloaded or
  other-store builds, the Sunset soft dialog shows first and `suppressed = sunsetKind != null` holds the Play sheet
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
