package com.abominableenterprise.cherkit.sunset.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Calls [onPromptChange] whenever [kind] changes (`SOFT` → `HARD` directly, no `null` in between), never for the
 * initial `null`, and with `null` when this leaves the composition while a prompt was showing.
 */
@Composable
internal fun ReportSunsetPromptKind(kind: SunsetPromptKind?, onPromptChange: (SunsetPromptKind?) -> Unit) {
    val currentOnPromptChange by rememberUpdatedState(onPromptChange)
    val reported = remember { arrayOfNulls<SunsetPromptKind>(1) }
    DisposableEffect(kind) {
        if (reported[0] != kind) {
            reported[0] = kind
            currentOnPromptChange(kind)
        }
        onDispose {}
    }
    DisposableEffect(Unit) {
        onDispose {
            if (reported[0] != null) {
                reported[0] = null
                currentOnPromptChange(null)
            }
        }
    }
}
