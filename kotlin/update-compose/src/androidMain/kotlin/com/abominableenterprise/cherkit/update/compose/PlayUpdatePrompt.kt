package com.abominableenterprise.cherkit.update.compose

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.InstallStatus

/**
 * Offers Play's FLEXIBLE in-app update when Play has one, at most once per session, never over your other prompts.
 * Put it once in your root composable:
 *
 * ```kotlin
 * val update = rememberPlayUpdateState()
 * PlayUpdatePrompt(
 *     update,
 *     suppressed = sunsetKind != null || showWhatsNew,
 *     onVisibilityChange = { review.setInterruptionVisible(it, UpdateInterruption.SOURCE) },
 * )
 * if (update.isDownloaded) { /* "Restart to install" -> update.completeUpdate() */ }
 * ```
 *
 * - Runs on every `ON_RESUME` and every time [suppressed] changes, while the lifecycle is `RESUMED`.
 * - Only on Play installs with [requirePlayInstall] (the default): production and Play testing tracks pass;
 *   sideloads, IDE/emulator installs and other stores get nothing. Off in debuggable builds unless [enabled].
 * - Starts the flow ([UpdateLauncher.launch]) when Play reports `UPDATE_AVAILABLE` with FLEXIBLE allowed, the user has
 *   not said "Not now" to that `availableVersionCode`, and [suppressed] is false. At most once per process.
 * - "Not now" (cancelling Play's sheet) is remembered in [store] per versionCode; a newer build is offered again.
 * - A downloaded update (Play's listener, or `appUpdateInfo` on a later resume) sets [PlayUpdateState.isDownloaded]
 *   and calls [onDownloaded] once, whatever [suppressed] says.
 * - Every Play error is quiet: nothing shows and [onResult] gets the reason.
 *
 * @param onVisibilityChange `true` when Play's sheet opens, `false` when it closes or this leaves the composition.
 * @param onResult The result of each attempt, e.g. for logging.
 */
@Composable
public fun PlayUpdatePrompt(
    state: PlayUpdateState = rememberPlayUpdateState(),
    suppressed: Boolean = false,
    enabled: Boolean = AndroidUpdate.isEnabledByDefault(LocalContext.current),
    requirePlayInstall: Boolean = true,
    store: UpdatePromptStore = rememberUpdatePromptStore(),
    onVisibilityChange: (Boolean) -> Unit = {},
    onDownloaded: () -> Unit = {},
    onResult: (UpdateLaunchResult) -> Unit = {},
) {
    val isPlayInstall: (Context) -> Boolean = if (requirePlayInstall) PlayInstall::isPlayInstall else { _ -> true }
    PlayUpdatePrompt(
        state, suppressed, enabled, store, onVisibilityChange, onDownloaded, onResult,
        UpdateSession.shared, isPlayInstall,
    )
}

/** [PlayUpdatePrompt] with its session and installer check, so host tests can run it fresh. */
@Composable
internal fun PlayUpdatePrompt(
    state: PlayUpdateState,
    suppressed: Boolean,
    enabled: Boolean,
    store: UpdatePromptStore,
    onVisibilityChange: (Boolean) -> Unit,
    onDownloaded: () -> Unit,
    onResult: (UpdateLaunchResult) -> Unit,
    session: UpdateSession,
    isPlayInstall: (Context) -> Boolean,
) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val currentSuppressed by rememberUpdatedState(suppressed)
    val currentResumed by rememberUpdatedState(resumed)
    val currentStore by rememberUpdatedState(store)
    val currentOnVisibilityChange by rememberUpdatedState(onVisibilityChange)
    val currentOnDownloaded by rememberUpdatedState(onDownloaded)
    val currentOnResult by rememberUpdatedState(onResult)
    // Without an ActivityResultRegistryOwner (a preview, a plain Activity) there is no way to show Play's sheet.
    val sheet = if (LocalActivityResultRegistryOwner.current != null) {
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            state.onFlowResult(it.resultCode, session, currentStore)
        }
    } else {
        null
    }
    val manager = state.manager

    DisposableEffect(manager, enabled) {
        if (manager == null || !enabled) return@DisposableEffect onDispose {}
        val listener = InstallStateUpdatedListener { installState ->
            if (installState.installStatus() == InstallStatus.DOWNLOADED && state.markDownloaded()) currentOnDownloaded()
        }
        val registered = try {
            manager.registerListener(listener)
            true
        } catch (_: Exception) {
            false
        }
        onDispose { if (registered) runCatching { manager.unregisterListener(listener) } }
    }

    LaunchedEffect(state, enabled, resumed, suppressed, sheet) {
        if (!resumed) return@LaunchedEffect
        val result = when {
            !enabled -> UpdateLaunchResult.DISABLED
            !isPlayInstall(context) -> UpdateLaunchResult.NOT_PLAY_INSTALL
            manager == null || sheet == null -> UpdateLaunchResult.CHECK_FAILED
            else -> UpdateLauncher.launch(PlayUpdateFlow(manager, sheet), currentStore, session) {
                !currentSuppressed && currentResumed
            }
        }
        when (result) {
            UpdateLaunchResult.STARTED -> state.isVisible = true
            UpdateLaunchResult.DOWNLOADED -> if (state.markDownloaded()) currentOnDownloaded()
            else -> Unit
        }
        currentOnResult(result)
    }

    val visible = state.isVisible
    DisposableEffect(visible) {
        if (visible) currentOnVisibilityChange(true)
        onDispose { if (visible) currentOnVisibilityChange(false) }
    }
}
