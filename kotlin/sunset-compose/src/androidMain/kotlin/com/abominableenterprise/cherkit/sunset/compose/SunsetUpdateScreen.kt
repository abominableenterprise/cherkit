package com.abominableenterprise.cherkit.sunset.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abominableenterprise.cherkit.sunset.SunsetPrompt

private val maxContentWidth = 480.dp
private val iconSize = 88.dp

/**
 * The hard "Update required" screen: optional app icon, title, message and an "Update" button that opens the store.
 *
 * It has no way to dismiss it. [SunsetPrompt] shows it in a full-screen, non-dismissible dialog for
 * `SunsetDecision.HardUpdate`. The text scrolls when it does not fit, so nothing clips on small screens or at the
 * largest font scale, while the "Update" button stays pinned at the bottom, always reachable.
 *
 * @param prompt From `SunsetDecision.HardUpdate`: the store URL and the optional custom message.
 * @param strings The text; English defaults.
 * @param appIcon Shown above the title when set. Decorative: TalkBack skips it.
 */
@Composable
public fun SunsetUpdateScreen(
    prompt: SunsetPrompt,
    modifier: Modifier = Modifier,
    strings: SunsetStrings = SunsetStrings(),
    appIcon: Painter? = null,
) {
    SunsetUpdateScreen(prompt, modifier, strings, appIcon, rememberStoreOpener())
}

/** The screen with [openStore] resolved by the caller, e.g. outside a dialog whose window has its own locals. */
@Composable
internal fun SunsetUpdateScreen(
    prompt: SunsetPrompt,
    modifier: Modifier,
    strings: SunsetStrings,
    appIcon: Painter?,
    openStore: (String) -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Centered when it fits, scrolling when it does not.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Content(prompt, strings, appIcon)
                }
            }
            Button(
                onClick = { openStore(prompt.storeUrl) },
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth(),
            ) {
                Text(strings.updateButton, Modifier.padding(vertical = 4.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Content(prompt: SunsetPrompt, strings: SunsetStrings, appIcon: Painter?) {
    Column(Modifier.widthIn(max = maxContentWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        if (appIcon != null) {
            Image(
                painter = appIcon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(iconSize).clip(RoundedCornerShape(percent = 22)),
            )
            Spacer(Modifier.height(24.dp))
        }
        Text(
            strings.hardTitle,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            strings.hardText(prompt),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
