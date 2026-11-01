package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.abominableenterprise.cherkit.whatsnew.WhatsNewHistoryEntry

private val maxContentWidth = 560.dp

/**
 * Every release in [entries] (newest first, as `WhatsNewChecker.history(locales)` returns them) with its date and
 * bullets, like iOS's `WhatsNewHistoryView`, for a Settings screen. The installed release carries the
 * [WhatsNewStrings.installedBadge] ("You're on this version"), a release without bullets shows
 * [WhatsNewStrings.noNotes] and an empty list shows [WhatsNewStrings.emptyHistory]. It scrolls.
 *
 * It has no top bar: put it in your own `Scaffold`, with [WhatsNewStrings.historyTitle] as the title if you like, and
 * pass the scaffold's padding as [contentPadding]. Or pass [showTitle] to show that title as a heading at the top of
 * the list, scrolling with it, when your screen has no title of its own.
 *
 * @param entries The releases, newest first.
 * @param modifier Applied to the list.
 * @param strings The text; English defaults.
 * @param contentPadding Padding around the list content, e.g. the `Scaffold`'s inner padding.
 * @param showTitle Shows [WhatsNewStrings.historyTitle] as a heading (for TalkBack too) above the releases. Off by
 *   default, for apps that put the title in their own top bar.
 */
@Composable
public fun WhatsNewHistoryScreen(
    entries: List<WhatsNewHistoryEntry>,
    modifier: Modifier = Modifier,
    strings: WhatsNewStrings = WhatsNewStrings(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showTitle: Boolean = false,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showTitle) {
            item {
                Text(
                    strings.historyTitle,
                    Modifier
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    strings.emptyHistory,
                    Modifier.widthIn(max = maxContentWidth).fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(entries) { _, entry ->
            HistoryRow(entry, strings)
        }
    }
}

/** One release as an inset card, like a section of iOS's inset grouped list. */
@Composable
private fun HistoryRow(entry: WhatsNewHistoryEntry, strings: WhatsNewStrings) {
    Surface(
        Modifier.widthIn(max = maxContentWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WhatsNewReleaseHeader(strings.versionTitle(entry.version.toString()), entry.date)
            if (entry.isInstalled) WhatsNewInstalledBadge(strings.installedBadge)
            if (entry.bullets.isEmpty()) {
                Text(
                    strings.noNotes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WhatsNewBulletList(entry.bullets)
            }
        }
    }
}

/** The 0.4.0 signature, kept so code compiled against it still links. Source calls resolve to the one above. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
public fun WhatsNewHistoryScreen(
    entries: List<WhatsNewHistoryEntry>,
    modifier: Modifier = Modifier,
    strings: WhatsNewStrings = WhatsNewStrings(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    WhatsNewHistoryScreen(entries, modifier, strings, contentPadding, showTitle = false)
}
