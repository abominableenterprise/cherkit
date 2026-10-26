package com.abominableenterprise.cherkit.sunset.compose

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.abominableenterprise.cherkit.sunset.SunsetPrompt

/**
 * The soft "Update available" dialog: title, the config's message (or [SunsetStrings.softMessage]), "Update" (opens
 * the store, then [onUpdate]) and "Not now" ([onNotNow], also on back or a tap outside). The text scrolls at large
 * font scales so both buttons stay reachable. [SunsetPrompt] shows it with the "Not now" memory; use it directly only
 * to present it yourself.
 */
@Composable
public fun SunsetSoftPromptDialog(
    prompt: SunsetPrompt,
    onUpdate: () -> Unit,
    onNotNow: () -> Unit,
    strings: SunsetStrings = SunsetStrings(),
) {
    val openStore = rememberStoreOpener()
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(strings.softTitle, Modifier.semantics { heading() }) },
        text = { Text(strings.softText(prompt), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = {
                openStore(prompt.storeUrl)
                onUpdate()
            }) { Text(strings.updateButton) }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text(strings.notNowButton) }
        },
    )
}

/**
 * Opens a store URL with [LocalUriHandler]. A device with nothing to open it (no store, no browser) must not crash the
 * app, so that failure is ignored: the prompt simply stays or closes as it would.
 */
@Composable
internal fun rememberStoreOpener(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return { url ->
        try {
            uriHandler.openUri(url)
        } catch (_: RuntimeException) {
            // ActivityNotFoundException (wrapped in IllegalArgumentException by the platform UriHandler).
        }
    }
}
