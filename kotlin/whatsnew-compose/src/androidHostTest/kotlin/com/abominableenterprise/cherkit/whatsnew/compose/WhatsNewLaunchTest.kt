package com.abominableenterprise.cherkit.whatsnew.compose

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The state behind `WhatsNewPrompt(checker = ...)`, run unconfined so `onLaunch` has finished when the call returns. */
class WhatsNewLaunchTest {
    private val test = TestChecker()
    private val launch = WhatsNewLaunch(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    @Test
    fun onLaunchRunsOnce() {
        launch.launched(test.checker, "1.1.0", listOf("en-US"))
        launch.launched(test.checker, "1.1.0", listOf("en-US"))
        assertEquals(1, test.launches)
        assertEquals(listOf(AppVersion.parse("1.1.0")), launch.entries.map { it.version })
        assertEquals(listOf("CSV export.", "Dark mode."), launch.entries.single().bullets)
    }

    @Test
    fun marksSeenOnlyOnDismissalAndOnce() {
        launch.launched(test.checker, "1.1.0", listOf("en-US"))
        assertEquals("1.0.0", test.lastSeen, "not before the user dismissed the sheet")
        launch.dismissed()
        assertEquals("1.1.0", test.lastSeen)
        assertTrue(launch.entries.isEmpty())
        launch.dismissed()
        assertEquals(1, test.launches)
    }

    @Test
    fun nothingNewShowsNothing() {
        launch.launched(test.checker, "1.0.0", listOf("en-US"))
        assertTrue(launch.entries.isEmpty())
        launch.dismissed()
        assertEquals("1.0.0", test.lastSeen)
    }
}
