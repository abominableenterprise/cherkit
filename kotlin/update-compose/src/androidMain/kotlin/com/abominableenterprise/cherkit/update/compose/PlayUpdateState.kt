package com.abominableenterprise.cherkit.update.compose

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory

/**
 * What `PlayUpdatePrompt` knows, for your UI: whether Play's sheet is up and whether a flexible update has
 * downloaded, plus [completeUpdate] for "Restart to install". Create it with [rememberPlayUpdateState].
 */
public class PlayUpdateState internal constructor(internal val manager: AppUpdateManager?) {
    /** A flexible update finished downloading and waits for [completeUpdate]: show "Restart to install". */
    public var isDownloaded: Boolean by mutableStateOf(false)
        internal set

    /** Sets [isDownloaded]; `true` only on the change, so callers report each download once. */
    internal fun markDownloaded(): Boolean {
        if (isDownloaded) return false
        isDownloaded = true
        return true
    }

    /** Play's update sheet is showing. */
    public var isVisible: Boolean by mutableStateOf(false)
        internal set

    /**
     * Installs the downloaded update: Play restarts the app. Returns `false` (doing nothing) when nothing is
     * downloaded, and when Play throws; a later Play failure is ignored. Call it from the main thread.
     *
     * The app process is killed and restarted right away, with no chance to finish work in progress. If the app
     * queues local writes (e.g. an offline database or a sync queue), flush or confirm them before calling this, or
     * disable or delay the "Restart to install" button until nothing is pending.
     */
    public fun completeUpdate(): Boolean {
        val manager = manager ?: return false
        if (!isDownloaded) return false
        return try {
            manager.completeUpdate().addOnFailureListener { }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** The sheet closed with [resultCode]: "Not now" is remembered for the versionCode [session] started it for. */
    internal fun onFlowResult(resultCode: Int, session: UpdateSession, store: UpdatePromptStore): UpdateFlowResult {
        isVisible = false
        val result = when (resultCode) {
            Activity.RESULT_OK -> UpdateFlowResult.ACCEPTED
            Activity.RESULT_CANCELED -> UpdateFlowResult.CANCELLED
            // ActivityResult.RESULT_IN_APP_UPDATE_FAILED and anything unknown.
            else -> UpdateFlowResult.FAILED
        }
        session.promptedVersionCode?.let { UpdateLauncher.finish(result, it, store) }
        return result
    }
}

/**
 * A [PlayUpdateState] over Play's `AppUpdateManager`, kept across recompositions. Pass [manager] to fake Play
 * (`FakeAppUpdateManager(context)`). If creating the manager throws, the prompt does nothing.
 */
@Composable
public fun rememberPlayUpdateState(
    manager: (Context) -> AppUpdateManager = { AppUpdateManagerFactory.create(it) },
): PlayUpdateState {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        PlayUpdateState(
            try {
                manager(context)
            } catch (_: Exception) {
                null
            },
        )
    }
}
