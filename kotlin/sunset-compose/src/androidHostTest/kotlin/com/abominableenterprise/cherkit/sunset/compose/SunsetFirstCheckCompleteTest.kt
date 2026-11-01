package com.abominableenterprise.cherkit.sunset.compose

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.sunset.SunsetCheckOutcome
import com.abominableenterprise.cherkit.sunset.SunsetChecker
import com.abominableenterprise.cherkit.sunset.SunsetDecision
import com.abominableenterprise.cherkit.sunset.SunsetPrompt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * `onFirstCheckComplete` of `SunsetPrompt(checker = ...)`: once per runner (the process, for the shared one), after
 * the first decision's `onPromptChange` / `onVisibilityChange`, whatever the launch check found.
 */
@RunWith(RobolectricTestRunner::class)
class SunsetFirstCheckCompleteTest {
    @get:Rule
    val compose = createComposeRule()

    private val strings = SunsetStrings()
    private val prompt = SunsetPrompt("https://play.google.com/store/apps/details?id=a", null, AppVersion.parse("2.0"))
    private val soft = SunsetDecision.SoftUpdate(prompt)
    private val hard = SunsetDecision.HardUpdate(prompt)

    /** Every callback in the order it ran. */
    private val events = mutableListOf<String>()
    private var fetches = 0
    private lateinit var memory: SunsetSoftPromptMemory
    private val owner = FakeLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
    private var shown by mutableStateOf(true)

    @Before
    fun setUp() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("sunset-first-check", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        memory = SunsetSoftPromptMemory(prefs)
    }

    /** A lifecycle the test drives by hand. */
    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun newRunner() =
        SunsetCheckRunner(Duration.ZERO, now = { Duration.ZERO }, scope = CoroutineScope(Dispatchers.Unconfined))

    private fun compose(
        runner: SunsetCheckRunner,
        disabled: Boolean = false,
        check: suspend () -> SunsetCheckOutcome,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown) {
                    SunsetCheckerPrompt(
                        runner = runner,
                        minimumInterval = Duration.ZERO,
                        memory = memory,
                        strings = strings,
                        appIcon = null,
                        onVisibilityChange = { events += "visible $it" },
                        onPromptChange = { events += "kind $it" },
                        onFirstCheckComplete = { events += "settled" },
                        disabled = disabled,
                    ) {
                        fetches++
                        check()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun backgroundAndReturn() {
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        compose.waitForIdle()
    }

    @Test
    fun upToDateFiresOnceAndNotOnResumeRecheckOrRecomposition() {
        val pending = CompletableDeferred<SunsetCheckOutcome>()
        compose(newRunner()) { pending.await() }
        assertEquals(emptyList(), events, "not before the launch check finishes")

        pending.complete(SunsetCheckOutcome(SunsetDecision.UpToDate, failed = false))
        compose.waitForIdle()
        assertEquals(listOf("settled"), events)

        backgroundAndReturn()
        assertEquals(2, fetches)
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()
        assertEquals(listOf("settled"), events)
        assertEquals(2, fetches)
    }

    @Test
    fun softIsReportedBeforeSettled() {
        compose(newRunner()) { SunsetCheckOutcome(soft, failed = false) }
        compose.onNodeWithText(strings.softTitle).assertExists()
        assertEquals(listOf("visible true", "kind SOFT", "settled"), events)
    }

    @Test
    fun hardIsReportedBeforeSettled() {
        val pending = CompletableDeferred<SunsetCheckOutcome>()
        compose(newRunner()) { pending.await() }
        pending.complete(SunsetCheckOutcome(hard, failed = false))
        compose.waitForIdle()
        compose.onNodeWithText(strings.hardTitle).assertExists()
        assertEquals(listOf("kind HARD", "settled"), events)
    }

    @Test
    fun aFailedFetchStillSettlesAndLaterChecksDoNotFireAgain() {
        // An endless retry delay, so the quick retry never runs on its own; the return from the background re-checks.
        val runner = SunsetCheckRunner(
            Duration.ZERO,
            retryDelay = Duration.INFINITE,
            now = { Duration.ZERO },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        var answer = SunsetCheckOutcome(SunsetDecision.UpToDate, failed = true)
        compose(runner) { answer }
        assertEquals(listOf("settled"), events)

        answer = SunsetCheckOutcome(hard, failed = false)
        backgroundAndReturn()
        assertEquals(2, fetches)
        compose.onNodeWithText(strings.hardTitle).assertExists()
        assertEquals(listOf("settled", "kind HARD"), events)
    }

    @Test
    fun aThrowingCheckStillSettles() {
        compose(newRunner()) { error("boom") }
        assertEquals(listOf("settled"), events)
    }

    @Test
    fun disabledFiresAtOnce() {
        // The check never finishes: settled comes from the disabled checker alone.
        compose(newRunner(), disabled = true) { CompletableDeferred<SunsetCheckOutcome>().await() }
        assertEquals(listOf("settled"), events)
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()
    }

    @Test
    fun disabledCheckerOnThePublicOverloadFiresOnce() {
        var count = 0
        compose.setContent {
            if (shown) SunsetPrompt(checker = SunsetChecker.disabled(), memory = memory, onFirstCheckComplete = { count++ })
        }
        compose.waitForIdle()
        assertEquals(1, count)
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()
        assertEquals(1, count)
    }

    @Test
    fun aRunnerThatAlreadyReportedNeverFiresAgain() {
        // As after a rotation: a new composition meets the process's runner, which already reported.
        val runner = newRunner()
        runner.launched { SunsetCheckOutcome(SunsetDecision.UpToDate, failed = false) }
        runner.firstCheckReported = true
        compose(runner) { SunsetCheckOutcome(soft, failed = false) }
        assertEquals(emptyList(), events)
        assertEquals(0, fetches)
    }

    @Test
    fun aCheckFinishedBeforeAnyCallbackStillFiresForTheFirstOne() {
        // Nothing passed a callback yet, so nothing is marked as reported.
        val runner = newRunner()
        runner.launched { SunsetCheckOutcome(SunsetDecision.UpToDate, failed = false) }
        assertEquals(false, runner.firstCheckReported)
        compose(runner) { SunsetCheckOutcome(SunsetDecision.UpToDate, failed = false) }
        assertEquals(listOf("settled"), events)
        assertEquals(true, runner.firstCheckReported)
    }
}
