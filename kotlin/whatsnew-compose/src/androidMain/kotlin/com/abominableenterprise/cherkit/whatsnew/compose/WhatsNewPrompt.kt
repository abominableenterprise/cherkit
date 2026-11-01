package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.abominableenterprise.cherkit.whatsnew.WhatsNewEntry

/**
 * Shows a [WhatsNewSheet] for [entries] you got yourself (e.g. from `checker.onLaunch` on your own schedule), like
 * iOS's `.whatsNew(entries:onDismiss:)`. It shows whenever [entries] becomes a new non-empty list (a list equal to
 * the last one is not shown again while this stays composed) and calls [onDismiss] once per dismissal (Continue,
 * back, swipe or a tap outside). Empty entries show nothing. Call `checker.markSeen()` from [onDismiss] if you use a
 * `WhatsNewChecker`, and clear your entries there, so a recreated activity does not show them again.
 *
 * @param entries The releases to show, newest first; empty shows nothing.
 * @param onDismiss Called once when the user dismisses the sheet.
 * @param strings The text; English defaults.
 * @param suppressed While `true`, the sheet is not shown (or is taken away, without counting as a dismissal), e.g.
 *   while a Sunset prompt is visible. It shows once this turns `false` again.
 * @param onVisibilityChange Called with `true` when the sheet appears and `false` when it goes away, e.g. to report it
 *   to a review coordinator as an interruption.
 */
@Composable
public fun WhatsNewPrompt(
    entries: List<WhatsNewEntry>,
    onDismiss: () -> Unit,
    strings: WhatsNewStrings = WhatsNewStrings(),
    suppressed: Boolean = false,
    onVisibilityChange: ((Boolean) -> Unit)? = null,
) {
    // Keyed on the list's contents: a new non-empty list shows again, an equal one does not.
    var pending by remember(entries) { mutableStateOf(entries.isNotEmpty()) }
    WhatsNewSheetHost(
        entries = entries,
        visible = pending && !suppressed,
        strings = strings,
        onVisibilityChange = onVisibilityChange,
        onDismiss = {
            pending = false
            onDismiss()
        },
    )
}

/**
 * Shows [WhatsNewSheet] while [visible] and reports it to [onVisibilityChange]: `true` when it appears, `false` when it
 * goes away (dismissed, hidden again, or this composable leaves the composition).
 */
@Composable
internal fun WhatsNewSheetHost(
    entries: List<WhatsNewEntry>,
    visible: Boolean,
    strings: WhatsNewStrings,
    onVisibilityChange: ((Boolean) -> Unit)?,
    onDismiss: () -> Unit,
) {
    // Each `true` goes to the recipient that later gets the matching `false`: a new recipient while shown moves the
    // registration, and new entries (even supplied from onDismiss) get a fresh sheet and a fresh true/false pair.
    DisposableEffect(visible, entries, onVisibilityChange) {
        if (visible) onVisibilityChange?.invoke(true)
        onDispose { if (visible) onVisibilityChange?.invoke(false) }
    }
    if (visible) key(entries) { WhatsNewSheet(entries, strings, onDismiss) }
}
