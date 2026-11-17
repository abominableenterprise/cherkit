package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.runtime.Composable
import com.abominableenterprise.cherkit.whatsnew.WhatsNewChecker

/** Compile-time coverage of existing positional, named and trailing visibility callbacks and the new callback. */
@Suppress("unused")
@Composable
private fun compatibleCalls(checker: WhatsNewChecker, visibility: (Boolean) -> Unit) {
    WhatsNewPrompt(checker, "1.1.0", listOf("en"), WhatsNewStrings(), false, null, visibility)
    WhatsNewPrompt(checker, onVisibilityChange = visibility)
    WhatsNewPrompt(checker) { visible: Boolean -> visibility(visible) }
    WhatsNewPrompt(checker, onDismiss = {}, onVisibilityChange = visibility)
}
