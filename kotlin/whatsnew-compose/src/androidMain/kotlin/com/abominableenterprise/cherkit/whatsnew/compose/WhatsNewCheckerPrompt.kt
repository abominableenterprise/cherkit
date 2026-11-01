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
 *   fetches, so to always show Sunset first, also keep this `true` until that check finishes, with a process-wide
 *   flag set from `onFirstCheckComplete` (not `rememberSaveable`, which restores a stale `true` after process death).
 * @param onLaunchCheckComplete Called once the launch `onLaunch` has returned, with `hasNotes`: whether there are notes
 *   to show (`false` for nothing new, and when `onLaunch` failed open). Once per process, with the same guard as the
 *   `onLaunch` call: recomposition, rebuilding the root and recreating the activity (rotation) never call it again.
 *   [suppressed] does not hold it back: the launch check runs anyway and only the sheet waits. When `hasNotes` is
 *   `true` and the sheet is not suppressed, [onVisibilityChange]`(true)` for it runs first, in the same frame, so a
 *   flag set from [onVisibilityChange] is already `true` when this runs; while suppressed, the sheet's `true` comes
 *   later, when it shows. Use it like Sunset's `onFirstCheckComplete`, e.g. to hold back Play's update prompt until
 *   it runs (see the README). Keep the flag it sets per process too (an `object`), not in `remember` or
 *   `rememberSaveable`.
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
    onLaunchCheckComplete: ((hasNotes: Boolean) -> Unit)? = null,
    onVisibilityChange: ((Boolean) -> Unit)? = null,
) {
    WhatsNewCheckerPrompt(
        WhatsNewLaunch.shared, checker, installedVersion, locales, strings, suppressed,
        onLaunchCheckComplete = onLaunchCheckComplete,
        onVisibilityChange = onVisibilityChange,
    )
}

/** The 0.6.0 signature, kept so code compiled against it still links. Source calls resolve to the one above. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
public fun WhatsNewPrompt(
    checker: WhatsNewChecker,
    installedVersion: String = rememberInstalledVersion(),
    locales: List<String> = AndroidWhatsNew.preferredLocales(),
    strings: WhatsNewStrings = WhatsNewStrings(),
    suppressed: Boolean = false,
    onVisibilityChange: ((Boolean) -> Unit)? = null,
) {
    WhatsNewPrompt(
        checker, installedVersion, locales, strings, suppressed,
        onLaunchCheckComplete = null,
        onVisibilityChange = onVisibilityChange,
    )
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
    onLaunchCheckComplete: ((hasNotes: Boolean) -> Unit)? = null,
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
    // After the sheet above, so its onVisibilityChange(true) for the same notes runs first.
    ReportWhatsNewLaunch(launch, onLaunchCheckComplete)
}

/** [AndroidWhatsNew.installedVersion], read once per context rather than on every recomposition. */
@Composable
internal fun rememberInstalledVersion(): String {
    val context = LocalContext.current
    return remember(context) { AndroidWhatsNew.installedVersion(context) }
}
