package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Calls [onLaunchCheckComplete] with whether there are notes to show once [launch]'s `onLaunch` has returned, at most
 * once per [launch], i.e. once per process for the shared one. Compose it after the sheet: effects run in composition
 * order, so the sheet's `onVisibilityChange(true)` for those notes comes first. Without a callback nothing is marked,
 * so a later composition that passes one still gets it.
 */
@Composable
internal fun ReportWhatsNewLaunch(launch: WhatsNewLaunch, onLaunchCheckComplete: ((hasNotes: Boolean) -> Unit)?) {
    val currentOnLaunchCheckComplete by rememberUpdatedState(onLaunchCheckComplete)
    val hasNotes = launch.launchHadNotes
    DisposableEffect(launch, hasNotes, onLaunchCheckComplete != null) {
        val callback = currentOnLaunchCheckComplete
        if (hasNotes != null && callback != null && !launch.launchReported) {
            launch.launchReported = true
            callback(hasNotes)
        }
        onDispose {}
    }
}
