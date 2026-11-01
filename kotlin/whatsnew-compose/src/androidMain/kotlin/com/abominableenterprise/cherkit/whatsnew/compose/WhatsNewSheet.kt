package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abominableenterprise.cherkit.whatsnew.WhatsNewEntry
import kotlinx.coroutines.launch

private val maxContentWidth = 560.dp
private val maxButtonWidth = 512.dp

/**
 * The "What's new" sheet, like iOS's `WhatsNewSheet` in a `.sheet`: a Material 3 [ModalBottomSheet], fully expanded,
 * with every release in [entries] (newest first, as `WhatsNewChecker.onLaunch` returns them), each with its version,
 * date and bullets (one section per release), and a **Continue** button pinned at the bottom. The releases scroll.
 *
 * A bottom sheet is the closest match to the iOS sheet: it slides up over the app and can be swiped down, as well as
 * closed with back, a tap on the scrim or **Continue**. [onContinue] runs once for any of these, after the sheet has
 * slid away; remove the sheet from the composition there. `WhatsNewPrompt` shows it for you; use this directly only
 * to present it yourself.
 *
 * @param entries The releases to show, newest first.
 * @param strings The text; English defaults.
 * @param onContinue Called once when the user dismisses the sheet (Continue, back, swipe or a tap outside).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun WhatsNewSheet(
    entries: List<WhatsNewEntry>,
    strings: WhatsNewStrings = WhatsNewStrings(),
    onContinue: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val currentOnContinue by rememberUpdatedState(onContinue)
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            currentOnContinue()
        }
    }
    ModalBottomSheet(onDismissRequest = finish, sheetState = sheetState) {
        WhatsNewSheetContent(
            entries = entries,
            strings = strings,
            // A programmatic hide does not call onDismissRequest, so finish once the sheet has slid away.
            onContinue = { scope.launch { sheetState.hide() }.invokeOnCompletion { finish() } },
        )
    }
}

/** The sheet's content: the title and releases scroll, the Continue button stays below them. */
@Composable
internal fun WhatsNewSheetContent(
    entries: List<WhatsNewEntry>,
    strings: WhatsNewStrings,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                strings.title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WhatsNewReleaseHeader(strings.versionTitle(entry.version.toString()), entry.date)
                    WhatsNewBulletList(entry.bullets)
                }
            }
        }
        Button(
            onClick = onContinue,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .widthIn(max = maxButtonWidth)
                .fillMaxWidth(),
        ) {
            Text(strings.continueButton, Modifier.padding(vertical = 4.dp), textAlign = TextAlign.Center)
        }
    }
}
