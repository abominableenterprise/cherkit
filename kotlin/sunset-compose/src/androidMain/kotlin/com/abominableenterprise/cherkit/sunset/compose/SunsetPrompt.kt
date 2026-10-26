package com.abominableenterprise.cherkit.sunset.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * It only displays a decision; it never fetches. Get one from `SunsetChecker.check(context)`.
 *
 * @param decision The decision to show, `null` while unknown.
 * @param onDecisionHandled Called once the soft dialog is answered; set your decision to `null` there.
 * @param memory Where "Not now" is stored. Defaults to the Sunset SharedPreferences file under
 *   [SunsetSoftPromptMemory.DEFAULT_KEY].
 * @param strings The text; English defaults.
 * @param appIcon Optional icon above the "Update required" title. Decorative: TalkBack skips it.
 */
@Composable
public fun SunsetPrompt(
    decision: SunsetDecision?,
    onDecisionHandled: () -> Unit,
    memory: SunsetSoftPromptMemory = rememberSunsetSoftPromptMemory(),
    strings: SunsetStrings = SunsetStrings(),
    appIcon: Painter? = null,
) {
    when (decision) {
        is SunsetDecision.SoftUpdate -> {
            val context = LocalContext.current
            val version = remember(decision, context) {
                SunsetSoftPromptRule.version(decision.prompt, AndroidSunset.installedVersion(context))
            }
            // Answered for this decision: hidden even before the app clears it, so it shows once per check.
            var answered by remember(decision) { mutableStateOf(false) }
            if (answered || !memory.shouldShowSoftPrompt(decision, version)) return
            SunsetSoftPromptDialog(
                prompt = decision.prompt,
                strings = strings,
                onUpdate = {
                    answered = true
                    onDecisionHandled()
                },
                onNotNow = {
                    memory.rememberNotNow(version)
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
