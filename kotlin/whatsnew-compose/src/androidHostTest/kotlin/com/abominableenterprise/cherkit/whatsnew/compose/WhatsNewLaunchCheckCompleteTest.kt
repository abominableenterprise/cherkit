package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals

/**
 * `onLaunchCheckComplete` of `WhatsNewPrompt(checker = ...)`: once per launch state (the process, for the shared one),
 * after the sheet's `onVisibilityChange(true)`, whether or not the sheet is suppressed.
 */
@RunWith(RobolectricTestRunner::class)
class WhatsNewLaunchCheckCompleteTest {
    @get:Rule
    val compose = createComposeRule()

    private val strings = WhatsNewStrings()
    private val test = TestChecker()
    private var launch = WhatsNewLaunch(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    /** Every callback in the order it ran. */
    private val events = mutableListOf<String>()
    private var suppressed by mutableStateOf(false)
    private var composed by mutableStateOf(true)
    private var withCallback by mutableStateOf(true)
    private var generation by mutableStateOf(0)

    private fun show(installedVersion: String = "1.1.0") {
        compose.setContent {
            if (composed) {
                val g = generation
                WhatsNewCheckerPrompt(
                    launch, test.checker, installedVersion, listOf("en-US"), strings, suppressed,
                    // Inline, capturing composition state: a new instance whenever `generation` changes.
                    onLaunchCheckComplete = if (withCallback) {
                        { hasNotes -> events += "complete $hasNotes ($g)" }
                    } else {
                        null
                    },
                    onVisibilityChange = { events += "visible $it" },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun notesAreReportedOnceRightAfterTheSheetShows() {
        show()
        compose.onNodeWithText(strings.title).assertExists()
        assertEquals(listOf("visible true", "complete true (0)"), events)
    }

    @Test
    fun nothingNewReportsFalse() {
        show(installedVersion = "1.0.0")
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals(listOf("complete false (0)"), events)
    }

    @Test
    fun notBeforeOnLaunchReturns() {
        val io = HeldDispatcher()
        launch = WhatsNewLaunch(CoroutineScope(Dispatchers.Unconfined), io)
        show()
        assertEquals(emptyList(), events, "not while onLaunch runs")
        assertEquals(0, test.launches)

        io.runAll()
        compose.waitForIdle()
        assertEquals(listOf("visible true", "complete true (0)"), events)
    }

    /** Holds what is dispatched to it until [runAll], so a test decides when `onLaunch` runs. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            held.addLast(block)
        }

        fun runAll() {
            while (held.isNotEmpty()) held.removeFirst().run()
        }
    }

    @Test
    fun recompositionAndRecreationDoNotReportAgain() {
        show()
        repeat(3) {
            generation++
            compose.waitForIdle()
        }
        // As after a rotation: the composition goes away and a new one meets the same launch state.
        composed = false
        compose.waitForIdle()
        composed = true
        compose.waitForIdle()
        compose.onNodeWithText(strings.continueButton).performClick()
        compose.waitForIdle()
        assertEquals(
            listOf("visible true", "complete true (0)", "visible false", "visible true", "visible false"),
            events,
        )
        assertEquals(1, test.launches)
    }

    @Test
    fun suppressedStillReportsAndTheSheetShowsLater() {
        suppressed = true
        show()
        compose.onNodeWithText(strings.title).assertDoesNotExist()
        assertEquals(listOf("complete true (0)"), events, "the check ran; only the sheet waits")
        assertEquals(1, test.launches)

        suppressed = false
        compose.waitForIdle()
        compose.onNodeWithText(strings.title).assertExists()
        assertEquals(listOf("complete true (0)", "visible true"), events)
    }

    @Test
    fun aLaunchFinishedBeforeAnyCallbackStillReportsToTheFirstOne() {
        withCallback = false
        show()
        assertEquals(listOf("visible true"), events)
        assertEquals(false, launch.launchReported)

        withCallback = true
        compose.waitForIdle()
        assertEquals(listOf("visible true", "complete true (0)"), events)
        assertEquals(true, launch.launchReported)
    }

    @Test
    fun aDismissalBeforeTheFirstCallbackStillReportsTheLaunchFound() {
        withCallback = false
        show()
        compose.onNodeWithText(strings.continueButton).performClick()
        compose.waitForIdle()
        withCallback = true
        compose.waitForIdle()
        assertEquals(listOf("visible true", "visible false", "complete true (0)"), events)
    }
}
