package com.abominableenterprise.cherkit.update.compose

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateLauncherTest {
    private val flow = FakeUpdateFlow()
    private val store = InMemoryUpdatePromptStore()
    private val session = UpdateSession()

    private suspend fun launch(allowed: Boolean = true) = UpdateLauncher.launch(flow, store, session) { allowed }

    @Test
    fun anAvailableFlexibleUpdateStartsOncePerSession() = runTest {
        assertEquals(UpdateLaunchResult.STARTED, launch())
        assertEquals(42, session.promptedVersionCode)
        assertEquals(UpdateLaunchResult.ALREADY_PROMPTED, launch())
        assertEquals(1, flow.starts)
    }

    @Test
    fun noUpdateOrImmediateOnlyStartsNothing() = runTest {
        flow.status = FakeUpdateFlow.none
        assertEquals(UpdateLaunchResult.NO_UPDATE, launch())
        flow.status = FakeUpdateFlow.available(42, flexible = false)
        assertEquals(UpdateLaunchResult.NO_UPDATE, launch())
        assertEquals(0, flow.starts)
    }

    @Test
    fun notNowIsRememberedPerVersionCode() = runTest {
        assertEquals(UpdateLaunchResult.STARTED, launch())
        UpdateLauncher.finish(UpdateFlowResult.CANCELLED, 42, store)
        assertEquals(42, store.declinedVersionCode)

        val next = UpdateSession()
        assertEquals(UpdateLaunchResult.DECLINED_BEFORE, UpdateLauncher.launch(flow, store, next))
        flow.status = FakeUpdateFlow.available(43)
        assertEquals(UpdateLaunchResult.STARTED, UpdateLauncher.launch(flow, store, next))
    }

    @Test
    fun acceptedAndFailedAreNotRemembered() {
        UpdateLauncher.finish(UpdateFlowResult.ACCEPTED, 42, store)
        UpdateLauncher.finish(UpdateFlowResult.FAILED, 42, store)
        assertNull(store.declinedVersionCode)
    }

    @Test
    fun suppressedHoldsWithoutUsingTheSession() = runTest {
        assertEquals(UpdateLaunchResult.SUPPRESSED, launch(allowed = false))
        assertEquals(0, flow.starts)
        assertNull(session.promptedVersionCode)
        assertEquals(UpdateLaunchResult.STARTED, launch())
    }

    @Test
    fun aDownloadedUpdateIsReportedEvenAfterThePrompt() = runTest {
        launch()
        flow.status = FakeUpdateFlow.downloaded(42)
        assertEquals(UpdateLaunchResult.DOWNLOADED, launch())
    }

    @Test
    fun storeErrorsAreQuiet() = runTest {
        flow.requestError = IllegalStateException("no Play")
        assertEquals(UpdateLaunchResult.CHECK_FAILED, launch())

        flow.requestError = null
        flow.startError = IllegalStateException("boom")
        assertEquals(UpdateLaunchResult.START_FAILED, launch())
        flow.startError = null
        flow.startResult = false
        assertEquals(UpdateLaunchResult.START_FAILED, launch())
        assertNull(session.promptedVersionCode)
    }

    @Test
    fun aBrokenStoreFailsOpen() = runTest {
        val broken = object : UpdatePromptStore {
            override val declinedVersionCode: Int? get() = throw IllegalStateException()
            override fun rememberDeclined(versionCode: Int) = throw IllegalStateException()
        }
        assertEquals(UpdateLaunchResult.STARTED, UpdateLauncher.launch(flow, broken, session))
        UpdateLauncher.finish(UpdateFlowResult.CANCELLED, 42, broken)
    }
}
