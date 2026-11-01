package com.abominableenterprise.cherkit.sunset.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Calls [onFirstCheckComplete] once [settled] (the launch check's first attempt finished, or the checker is
 * disabled), at most once per [runner], i.e. once per process for the shared runner. Compose it after the prompt that
 * shows the decision: effects run in composition order, so that prompt's `onPromptChange` and `onVisibilityChange`
 * for the same decision come first. Without a callback nothing is marked, so a later composition that passes one still
 * gets it.
 */
@Composable
internal fun ReportFirstSunsetCheck(runner: SunsetCheckRunner, settled: Boolean, onFirstCheckComplete: (() -> Unit)?) {
    val currentOnFirstCheckComplete by rememberUpdatedState(onFirstCheckComplete)
    DisposableEffect(runner, settled, onFirstCheckComplete != null) {
        val callback = currentOnFirstCheckComplete
        if (settled && callback != null && !runner.firstCheckReported) {
            runner.firstCheckReported = true
            callback()
        }
        onDispose {}
    }
}
