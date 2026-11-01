package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.whatsnew.WhatsNewHistoryEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [WhatsNewHistoryScreen]: the installed badge, fix-only releases and the empty state. */
@RunWith(RobolectricTestRunner::class)
class WhatsNewHistoryScreenUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val strings = WhatsNewStrings()

    private fun entry(version: String, date: String?, bullets: List<String>, installed: Boolean = false) =
        WhatsNewHistoryEntry(AppVersion.parse(version)!!, date, bullets, installed)

    @Test
    fun badgeOnTheInstalledReleaseAndNoNotesForFixOnly() {
        compose.setContent {
            WhatsNewHistoryScreen(
                listOf(
                    entry("2.1.0", "2026-11-02", listOf("Export to CSV.")),
                    entry("2.0.0", "2026-09-14", listOf("A fresh new look."), installed = true),
                    entry("1.9.2", null, emptyList()),
                ),
            )
        }
        compose.onNodeWithText("Version 2.1.0").assert(isHeading)
        compose.onNodeWithText("2026-11-02").assertExists()
        compose.onNodeWithText("Export to CSV.").assertExists()
        compose.onAllNodesWithText(strings.installedBadge).assertCountEquals(1)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Version 1.9.2"))
        compose.onNodeWithText(strings.noNotes).assertExists()
        compose.onAllNodesWithText(strings.noNotes).assertCountEquals(1)
        compose.onNodeWithText(strings.emptyHistory).assertDoesNotExist()
        // The title belongs in the app's own top bar.
        compose.onNodeWithText(strings.historyTitle).assertDoesNotExist()
    }

    @Test
    fun emptyHistoryShowsTheEmptyText() {
        compose.setContent { WhatsNewHistoryScreen(emptyList()) }
        compose.onNodeWithText(strings.emptyHistory).assertExists()
        compose.onNodeWithText(strings.installedBadge).assertDoesNotExist()
    }

    @Test
    fun customStrings() {
        val german = WhatsNewStrings(installedBadge = "Installiert", noNotes = "Fehlerbehebungen.", versionTitle = { "v$it" })
        compose.setContent {
            WhatsNewHistoryScreen(listOf(entry("3.0", null, emptyList(), installed = true)), strings = german)
        }
        compose.onNodeWithText("v3.0").assertExists()
        compose.onNodeWithText("Installiert").assertExists()
        compose.onNodeWithText("Fehlerbehebungen.").assertExists()
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
}
