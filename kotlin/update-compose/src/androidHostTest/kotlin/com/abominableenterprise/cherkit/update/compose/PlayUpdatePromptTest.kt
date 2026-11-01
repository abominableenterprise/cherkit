package com.abominableenterprise.cherkit.update.compose

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.ActivityResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PlayUpdatePrompt] against Play's [FakeAppUpdateManager], with a fake lifecycle and a fresh session per test. */
@RunWith(RobolectricTestRunner::class)
class PlayUpdatePromptTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fake = FakeAppUpdateManager(context)
    private val store = InMemoryUpdatePromptStore()
    private val session = UpdateSession()
    private val results = mutableListOf<UpdateLaunchResult>()
    private val visibility = mutableListOf<Boolean>()
    private var downloads = 0
    private var suppressed by mutableStateOf(false)
    private var playInstall = true
    private lateinit var state: PlayUpdateState
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun show(
        enabled: Boolean = true,
        manager: (Context) -> AppUpdateManager = { fake },
        isSuppressed: () -> Boolean = { suppressed },
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                state = rememberPlayUpdateState(manager)
                PlayUpdatePrompt(
                    state, isSuppressed(), enabled, store,
                    onVisibilityChange = { visibility += it },
                    onDownloaded = { downloads++ },
                    onResult = { results += it },
                    session = session,
                    isPlayInstall = { playInstall },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSideloadedBuildAsksPlayNothing() {
        playInstall = false
        fake.setUpdateAvailable(42)
        show()
        assertEquals(listOf(UpdateLaunchResult.NOT_PLAY_INSTALL), results)
        assertFalse(fake.isConfirmationDialogVisible)
    }

    @Test
    fun disabledAsksPlayNothing() {
        fake.setUpdateAvailable(42)
        show(enabled = false)
        assertEquals(listOf(UpdateLaunchResult.DISABLED), results)
        assertFalse(fake.isConfirmationDialogVisible)
    }

    @Test
    fun anAvailableUpdateStartsTheFlexibleFlowOnce() {
        fake.setUpdateAvailable(42)
        show()
        assertEquals(listOf(UpdateLaunchResult.STARTED), results)
        assertTrue(fake.isConfirmationDialogVisible)
        assertEquals(42, session.promptedVersionCode)
        assertEquals(listOf(true), visibility)
        assertTrue(state.isVisible)

        // A return from the background asks Play again but never shows the sheet twice in a session.
        owner.registry.currentState = Lifecycle.State.STARTED
        compose.waitForIdle()
        owner.registry.currentState = Lifecycle.State.RESUMED
        compose.waitForIdle()
        assertEquals(UpdateLaunchResult.ALREADY_PROMPTED, results.last())
    }

    @Test
    fun suppressedHoldsUntilItTurnsFalse() {
        fake.setUpdateAvailable(42)
        suppressed = true
        show()
        assertEquals(listOf(UpdateLaunchResult.SUPPRESSED), results)
        assertFalse(fake.isConfirmationDialogVisible)
        assertNull(session.promptedVersionCode)

        suppressed = false
        compose.waitForIdle()
        assertEquals(UpdateLaunchResult.STARTED, results.last())
        assertTrue(fake.isConfirmationDialogVisible)
    }

    @Test
    fun theReadmeSunsetGateHoldsTheSheetUntilALateHardBlockArrives() {
        // The README recipe: SunsetPrompt's launch fetch is still running (sunsetKind is null, not settled yet)
        // while Play already answers. A late HARD must win: the sheet never opens.
        var sunsetKind by mutableStateOf<String?>(null)
        var sunsetSettled by mutableStateOf(false)
        fake.setUpdateAvailable(42)
        show(isSuppressed = { !sunsetSettled || sunsetKind != null })
        assertEquals(listOf(UpdateLaunchResult.SUPPRESSED), results)

        // onPromptChange(HARD) runs before onFirstCheckComplete.
        sunsetKind = "HARD"
        sunsetSettled = true
        compose.waitForIdle()
        assertFalse(fake.isConfirmationDialogVisible)
        assertNull(session.promptedVersionCode)
        assertEquals(emptyList(), visibility)
    }

    @Test
    fun theReadmeSunsetGateReleasesTheSheetWhenTheFetchFails() {
        // A failed or up-to-date Sunset check still calls onFirstCheckComplete with no prompt: fail open.
        var sunsetSettled by mutableStateOf(false)
        fake.setUpdateAvailable(42)
        show(isSuppressed = { !sunsetSettled })
        assertFalse(fake.isConfirmationDialogVisible)

        sunsetSettled = true
        compose.waitForIdle()
        assertEquals(UpdateLaunchResult.STARTED, results.last())
        assertTrue(fake.isConfirmationDialogVisible)
    }

    @Test
    fun notNowIsRememberedForThatVersionCode() {
        fake.setUpdateAvailable(42)
        show()
        compose.runOnIdle { state.onFlowResult(Activity.RESULT_CANCELED, session, store) }
        compose.waitForIdle()
        assertEquals(42, store.declinedVersionCode)
        assertEquals(listOf(true, false), visibility)
        assertFalse(state.isVisible)
    }

    @Test
    fun acceptingOrAFailureRemembersNothing() {
        fake.setUpdateAvailable(42)
        show()
        compose.runOnIdle {
            assertEquals(UpdateFlowResult.ACCEPTED, state.onFlowResult(Activity.RESULT_OK, session, store))
            assertEquals(
                UpdateFlowResult.FAILED,
                state.onFlowResult(ActivityResult.RESULT_IN_APP_UPDATE_FAILED, session, store),
            )
        }
        assertNull(store.declinedVersionCode)
    }

    @Test
    fun aDeclinedVersionIsNotOfferedAgain() {
        store.rememberDeclined(42)
        fake.setUpdateAvailable(42)
        show()
        assertEquals(listOf(UpdateLaunchResult.DECLINED_BEFORE), results)
        assertFalse(fake.isConfirmationDialogVisible)
    }

    @Test
    fun aFinishedDownloadOffersRestartToInstall() {
        fake.setUpdateAvailable(42)
        show()
        compose.runOnIdle {
            fake.userAcceptsUpdate()
            fake.downloadStarts()
            fake.downloadCompletes()
        }
        compose.waitForIdle()
        assertTrue(state.isDownloaded)
        assertEquals(1, downloads)

        compose.runOnIdle { assertTrue(state.completeUpdate()) }
        assertTrue(fake.isInstallSplashScreenVisible)
    }

    @Test
    fun aDownloadFromAnEarlierSessionShowsOnResume() {
        // Downloaded in an earlier process: this session never showed the sheet.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        fake.setUpdateAvailable(42)
        fake.startUpdateFlowForResult(fake.appUpdateInfo.result, activity, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE), 1)
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        fake.downloadCompletes()
        show()
        assertEquals(listOf(UpdateLaunchResult.DOWNLOADED), results)
        assertTrue(state.isDownloaded)
        assertEquals(1, downloads)
        assertNull(session.promptedVersionCode)
        assertEquals(emptyList(), visibility)
    }

    @Test
    fun completeUpdateWithoutADownloadDoesNothing() {
        show()
        compose.runOnIdle { assertFalse(state.completeUpdate()) }
        assertFalse(fake.isInstallSplashScreenVisible)
    }

    @Test
    fun noUpdateShowsNothing() {
        fake.setUpdateNotAvailable()
        show()
        assertEquals(listOf(UpdateLaunchResult.NO_UPDATE), results)
        assertEquals(emptyList(), visibility)
    }

    @Test
    fun aThrowingPlayIsQuiet() {
        show(manager = { throw IllegalStateException("no Play") })
        assertEquals(listOf(UpdateLaunchResult.CHECK_FAILED), results)
        compose.runOnIdle { assertFalse(state.completeUpdate()) }
    }
}
