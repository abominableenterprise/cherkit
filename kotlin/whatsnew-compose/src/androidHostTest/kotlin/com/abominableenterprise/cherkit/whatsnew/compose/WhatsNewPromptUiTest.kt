package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.whatsnew.WhatsNewEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** Both `WhatsNewPrompt` overloads rendered with Robolectric: when the sheet shows, and what dismissing it does. */
@RunWith(RobolectricTestRunner::class)
class WhatsNewPromptUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val strings = WhatsNewStrings()
    private val test = TestChecker()
    private val launch = WhatsNewLaunch(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
    private val visibility = mutableListOf<Boolean>()
    private var suppressed by mutableStateOf(false)
    private var composed by mutableStateOf(true)

    private fun showChecker() {
        compose.setContent {
            if (composed) {
                WhatsNewCheckerPrompt(launch, test.checker, "1.1.0", listOf("en-US"), strings, suppressed) {
                    visibility += it
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSheetShown() {
        compose.onNodeWithText(strings.title).assertIsDisplayed().assert(isHeading)
        compose.onNodeWithText("Version 1.1.0").assertIsDisplayed().assert(isHeading)
        compose.onNodeWithText("2026-11-02").assertIsDisplayed()
        compose.onNodeWithText("CSV export.").assertIsDisplayed()
        compose.onNodeWithText("Dark mode.").assertIsDisplayed()
    }

    @Test
    fun continueMarksSeenOnlyOnDismissal() {
        showChecker()
        assertSheetShown()
        assertEquals("1.0.0", test.lastSeen, "shown is not seen: only the dismissal marks it")
        assertEquals(listOf(true), visibility)

        compose.onNodeWithText(strings.continueButton).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals("1.1.0", test.lastSeen)
        assertEquals(listOf(true, false), visibility)
    }

    @Test
    fun backDismissesAndMarksSeen() {
        showChecker()
        assertSheetShown()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals("1.1.0", test.lastSeen)
        assertEquals(listOf(true, false), visibility)
    }

    @Test
    fun suppressedWaitsThenShows() {
        suppressed = true
        showChecker()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals(emptyList(), visibility)
        assertEquals(1, test.launches)

        suppressed = false
        compose.waitForIdle()
        assertSheetShown()
        assertEquals(listOf(true), visibility)
        assertEquals("1.0.0", test.lastSeen)
    }

    @Test
    fun suppressingAShownSheetIsNotADismissal() {
        showChecker()
        suppressed = true
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals("1.0.0", test.lastSeen)
        assertEquals(listOf(true, false), visibility)

        suppressed = false
        compose.waitForIdle()
        assertSheetShown()
        assertEquals(listOf(true, false, true), visibility)
    }

    @Test
    fun onLaunchRunsOnceAcrossRecompositionAndKeepsUnseenNotes() {
        showChecker()
        composed = false
        compose.waitForIdle()
        assertEquals(listOf(true, false), visibility)
        assertEquals("1.0.0", test.lastSeen, "leaving the composition is not a dismissal")
        composed = true
        compose.waitForIdle()
        assertEquals(1, test.launches)
        assertSheetShown()
    }

    @Test
    fun nothingNewShowsNothing() {
        compose.setContent {
            WhatsNewCheckerPrompt(launch, test.checker, "1.0.0", listOf("en"), strings, false) { visibility += it }
        }
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals(emptyList(), visibility)
    }

    private fun entry(version: String, vararg bullets: String) =
        WhatsNewEntry(AppVersion.parse(version)!!, null, bullets.toList())

    @Test
    fun entriesOverloadDismissesOncePerNewList() {
        var entries by mutableStateOf(emptyList<WhatsNewEntry>())
        var dismissals = 0
        compose.setContent {
            WhatsNewPrompt(entries, onDismiss = { dismissals++ }, onVisibilityChange = { visibility += it })
        }
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertDoesNotExist()

        entries = listOf(entry("2.0", "Widgets."), entry("1.9", "Dark mode."))
        compose.waitForIdle()
        compose.onNodeWithText("Version 2.0").assertIsDisplayed()
        compose.onNodeWithText("Version 1.9").assertExists()
        compose.onNodeWithText(strings.continueButton).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals(1, dismissals)

        // An equal list is not shown again; a new one is.
        entries = listOf(entry("2.0", "Widgets."), entry("1.9", "Dark mode."))
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        entries = listOf(entry("2.1", "Export."))
        compose.waitForIdle()
        compose.onNodeWithText("Version 2.1").assertIsDisplayed()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(2, dismissals)
        assertEquals(listOf(true, false, true, false), visibility)
    }

    @Test
    fun entriesOverloadShowsAListSuppliedFromTheDismissal() {
        var entries by mutableStateOf(listOf(entry("2.0", "Widgets.")))
        var dismissals = 0
        compose.setContent {
            WhatsNewPrompt(
                entries,
                onDismiss = {
                    dismissals++
                    if (dismissals == 1) entries = listOf(entry("2.1", "Export."))
                },
                onVisibilityChange = { visibility += it },
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText(strings.continueButton).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Version 2.1").assertIsDisplayed()
        assertEquals(listOf(true, false, true), visibility)

        compose.onNodeWithText(strings.continueButton).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals(2, dismissals)
        assertEquals(listOf(true, false, true, false), visibility)
    }

    @Test
    fun aNewVisibilityRecipientWhileShownGetsTheRegistration() {
        val a = mutableListOf<Boolean>()
        val b = mutableListOf<Boolean>()
        val toA: (Boolean) -> Unit = { a += it }
        val toB: (Boolean) -> Unit = { b += it }
        var recipient by mutableStateOf<((Boolean) -> Unit)?>(null)
        val entries = listOf(entry("2.0", "Widgets."))
        compose.setContent { WhatsNewPrompt(entries, onDismiss = {}, onVisibilityChange = recipient) }
        compose.waitForIdle()

        recipient = toA
        compose.waitForIdle()
        assertEquals(listOf(true), a, "null to A: A learns the sheet is showing")
        recipient = toB
        compose.waitForIdle()
        assertEquals(listOf(true, false), a, "A to B: A's true is balanced on A")
        assertEquals(listOf(true), b)

        compose.onNodeWithText(strings.continueButton).performClick()
        compose.waitForIdle()
        assertEquals(listOf(true, false), b)
        assertEquals(listOf(true, false), a)
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
}
