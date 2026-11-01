package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.abominableenterprise.cherkit.whatsnew.AndroidWhatsNew
import com.abominableenterprise.cherkit.whatsnew.WhatsNewChecker

/**
 * Shows the "What's new" sheet after an update, like iOS's `.whatsNew(checker:)`. Put it once in your root
 * composable, inside your `MaterialTheme`:
 *
 * ```kotlin
 * val whatsNew = remember { AndroidWhatsNew.checker(context.applicationContext) }
 * WhatsNewPrompt(checker = whatsNew, suppressed = sunsetKind != null)
 * ```
 *
 * - On the first composition it calls `checker.onLaunch(installedVersion, locales)` (off the main thread), once per
 *   process: recomposition, rebuilding the root and recreating the activity (rotation) do not ask again.
 * - If that returns notes, it shows [WhatsNewSheet]: each release newest first, with its date and bullets.
 * - `markSeen()` runs when the user dismisses the sheet (Continue, back, swipe or a tap outside), never before, so
 *   notes the user never saw come back next launch. It runs on the checker `onLaunch` ran on.
 *
 * @param checker The checker, e.g. `AndroidWhatsNew.checker(context)`.
 * @param installedVersion The app's version; the installed `versionName` by default.
 * @param locales The user's locales, most preferred first.
 * @param strings The text; English defaults.
 * @param suppressed While `true`, the sheet waits (or is taken away again, without counting as a dismissal), e.g.
 *   `suppressed = sunsetKind != null`, fed from `SunsetPrompt`'s `onPromptChange`, so it never stacks with Sunset's
 *   soft dialog or hard block. It shows once this turns `false`. `sunsetKind` is `null` while Sunset's first check
 *   fetches, so to always show Sunset first, also keep this `true` until that check finishes.
 * @param onVisibilityChange Called with `true` when the sheet appears and `false` when it goes away, e.g.
 *   `{ review.setInterruptionVisible(it, ReviewInterruption.WHATS_NEW) }`.
 */
@Composable
public fun WhatsNewPrompt(
    checker: WhatsNewChecker,
    installedVersion: String = rememberInstalledVersion(),
    locales: List<String> = AndroidWhatsNew.preferredLocales(),
    strings: WhatsNewStrings = WhatsNewStrings(),
    suppressed: Boolean = false,
    onVisibilityChange: ((Boolean) -> Unit)? = null,
) {
    WhatsNewCheckerPrompt(WhatsNewLaunch.shared, checker, installedVersion, locales, strings, suppressed, onVisibilityChange)
}

/** [WhatsNewPrompt] (checker) with its [launch] state, so host tests can pass their own. */
@Composable
internal fun WhatsNewCheckerPrompt(
    launch: WhatsNewLaunch,
    checker: WhatsNewChecker,
    installedVersion: String,
    locales: List<String>,
    strings: WhatsNewStrings,
    suppressed: Boolean,
    onVisibilityChange: ((Boolean) -> Unit)?,
) {
    SideEffect { launch.launched(checker, installedVersion, locales) }
    val entries = launch.entries
    WhatsNewSheetHost(
        entries = entries,
        visible = entries.isNotEmpty() && !suppressed,
        strings = strings,
        onVisibilityChange = onVisibilityChange,
        onDismiss = launch::dismissed,
    )
}

/** [AndroidWhatsNew.installedVersion], read once per context rather than on every recomposition. */
@Composable
internal fun rememberInstalledVersion(): String {
    val context = LocalContext.current
    return remember(context) { AndroidWhatsNew.installedVersion(context) }
}
