package com.abominableenterprise.cherkit.sunset.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abominableenterprise.cherkit.sunset.AndroidSunset
import com.abominableenterprise.cherkit.sunset.SunsetDecision

/**
 * Shows the Sunset prompt for [decision], like iOS's `.sunsetPrompt(decision:)`:
 *
 * - [SunsetDecision.SoftUpdate]: a dismissible [SunsetSoftPromptDialog] with "Update" and "Not now". "Not now" (and
 *   dismissing it with back or a tap outside) is remembered in [memory] for the prompt's `latestVersion`, so the
 *   dialog does not come back for that version (see [SunsetSoftPromptRule]); without a `latestVersion`, for the
 *   installed version. Either answer calls [onDecisionHandled]: clear your decision there.
 * - [SunsetDecision.HardUpdate]: [SunsetUpdateScreen] in a full-screen [Dialog] that back and outside taps cannot
 *   dismiss. "Update" opens the store and the block stays, so [onDecisionHandled] is never called for it.
 * - [SunsetDecision.UpToDate] or `null`: nothing.
 *
 * This overload only displays a decision; it never fetches. Most apps use `SunsetPrompt(checker = ...)` instead,
 * which runs the checks too (at launch and on return from the background). To check yourself, get a decision from
 * `AndroidSunset.check(checker, context)`.
 *
 * @param decision The decision to show, `null` while unknown.
 * @param onDecisionHandled Called once the soft dialog is answered; set your decision to `null` there.
 * @param memory Where "Not now" is stored. Defaults to the Sunset SharedPreferences file under
 *   [SunsetSoftPromptMemory.DEFAULT_KEY].
 * @param strings The text; English defaults.
 * @param appIcon Optional icon above the "Update required" title. Decorative: TalkBack skips it.
 * @param onVisibilityChange Called with `true` when the soft "Update available" dialog appears and `false` when it
 *   goes away (answered, cleared, or this composable leaves the composition), so an app that runs its own checks can
 *   skip them while the dialog is open. Not called for the hard block: keep checking behind it, so it goes away
 *   once the config no longer requires the update.
 */
@Composable
public fun SunsetPrompt(
    decision: SunsetDecision?,
    onDecisionHandled: () -> Unit,
    memory: SunsetSoftPromptMemory = rememberSunsetSoftPromptMemory(),
    strings: SunsetStrings = SunsetStrings(),
    appIcon: Painter? = null,
    onVisibilityChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val softVersion = (decision as? SunsetDecision.SoftUpdate)?.let { soft ->
        remember(soft, context) { SunsetSoftPromptRule.version(soft.prompt, AndroidSunset.installedVersion(context)) }
    }
    // Answered for this decision: hidden even before the app clears it, so it shows once per check.
    var answered by remember(decision) { mutableStateOf(false) }
    val showsSoft = softVersion != null && !answered && memory.shouldShowSoftPrompt(decision, softVersion)
    val currentOnVisibilityChange by rememberUpdatedState(onVisibilityChange)
    DisposableEffect(showsSoft) {
        if (showsSoft) currentOnVisibilityChange(true)
        onDispose { if (showsSoft) currentOnVisibilityChange(false) }
    }

    when (decision) {
        is SunsetDecision.SoftUpdate -> if (showsSoft && softVersion != null) {
            SunsetSoftPromptDialog(
                prompt = decision.prompt,
                strings = strings,
                onUpdate = {
                    answered = true
                    onDecisionHandled()
                },
                onNotNow = {
                    memory.rememberNotNow(softVersion)
                    answered = true
                    onDecisionHandled()
                },
            )
        }
        is SunsetDecision.HardUpdate -> {
            // Resolved here: the dialog's window provides its own LocalUriHandler, hiding one the app provides.
            val openStore = rememberStoreOpener()
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                SunsetUpdateScreen(decision.prompt, Modifier, strings, appIcon, openStore)
            }
        }
        SunsetDecision.UpToDate, null -> Unit
    }
}

/** The 0.2.0 signature, kept so code compiled against it still links. Source calls resolve to the one above. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
public fun SunsetPrompt(
    decision: SunsetDecision?,
    onDecisionHandled: () -> Unit,
    memory: SunsetSoftPromptMemory = rememberSunsetSoftPromptMemory(),
    strings: SunsetStrings = SunsetStrings(),
    appIcon: Painter? = null,
) {
    SunsetPrompt(decision, onDecisionHandled, memory, strings, appIcon, onVisibilityChange = {})
}
