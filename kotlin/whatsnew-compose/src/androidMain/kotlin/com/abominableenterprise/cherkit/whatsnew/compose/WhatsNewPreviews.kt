package com.abominableenterprise.cherkit.whatsnew.compose

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.whatsnew.WhatsNewEntry
import com.abominableenterprise.cherkit.whatsnew.WhatsNewHistoryEntry

/** Sample releases of an "Example App", the same as the iOS previews. */
internal object WhatsNewPreviewData {
    private class Release(val version: String, val date: String?, val bullets: List<String>)

    private val releases = listOf(
        Release(
            "2.1.0",
            "2026-11-02",
            listOf(
                "Export your entries to CSV from Settings.",
                "Widgets now follow your accent color.",
                "Fixed a crash when opening a shared link.",
            ),
        ),
        Release(
            "2.0.0",
            "2026-09-14",
            listOf("A fresh new look across Example App.", "Search now finds notes as well as titles."),
        ),
        Release("1.9.2", null, emptyList()),
        Release("1.9.0", "2026-06-30", listOf("Dark mode for every screen.")),
    )

    fun entries(count: Int): List<WhatsNewEntry> = releases.filter { it.bullets.isNotEmpty() }.take(count).mapNotNull {
        AppVersion.parse(it.version)?.let { version -> WhatsNewEntry(version, it.date, it.bullets) }
    }

    val history: List<WhatsNewHistoryEntry> = releases.mapNotNull {
        AppVersion.parse(it.version)?.let { version ->
            WhatsNewHistoryEntry(version, it.date, it.bullets, isInstalled = it.version == "2.0.0")
        }
    }
}

/** Material 3 light or dark, following the system, as a stand-in for the app's own theme. */
@Composable
internal fun WhatsNewPreviewTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(content = content)
    }
}

// The sheet's content without the ModalBottomSheet window, which previews do not draw.
@Preview(name = "Sheet, one version")
@Composable
private fun SheetOnePreview() {
    WhatsNewPreviewTheme { WhatsNewSheetContent(WhatsNewPreviewData.entries(1), WhatsNewStrings(), onContinue = {}) }
}

@Preview(name = "Sheet, three versions", heightDp = 720)
@Preview(name = "Sheet, three versions, dark", heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SheetThreePreview() {
    WhatsNewPreviewTheme { WhatsNewSheetContent(WhatsNewPreviewData.entries(3), WhatsNewStrings(), onContinue = {}) }
}

@Preview(name = "History", heightDp = 720)
@Preview(name = "History, dark", heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryPreview() {
    WhatsNewPreviewTheme { WhatsNewHistoryScreen(WhatsNewPreviewData.history) }
}

@Preview(name = "Largest font", heightDp = 720, fontScale = 2f)
@Composable
private fun LargestFontPreview() {
    WhatsNewPreviewTheme { WhatsNewSheetContent(WhatsNewPreviewData.entries(3), WhatsNewStrings(), onContinue = {}) }
}
