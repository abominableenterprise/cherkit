package com.abominableenterprise.cherkit.sunset.compose

import android.content.Context
import android.os.Looper
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
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * `onCheckingChange` of `SunsetPrompt(checker = ...)`: `true` when any check starts (launch, re-check on return from
 * the background, quick retry), `false` once it finished, after the prompt reported that check's result.
 */
@RunWith(RobolectricTestRunner::class)
class SunsetCheckingChangeTest {
    @get:Rule
    val compose = createComposeRule()

    private val strings = SunsetStrings()
    private val prompt = SunsetPrompt("https://play.google.com/store/apps/details?id=a", null, AppVersion.parse("2.0"))
    private val hard = SunsetDecision.HardUpdate(prompt)
    private val upToDate = SunsetCheckOutcome(SunsetDecision.UpToDate, failed = false)

    /** Every callback in the order it ran. */
    private val events = mutableListOf<String>()

    /** The answers the checks wait for, one per check, in order. */
    private val answers = mutableListOf<CompletableDeferred<SunsetCheckOutcome>>()
    private lateinit var memory: SunsetSoftPromptMemory
    private val owner = FakeLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
    private var shown by mutableStateOf(true)
    private var generation by mutableStateOf(0)

    @Before
    fun setUp() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("sunset-checking", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        memory = SunsetSoftPromptMemory(prefs)
    }

    /** A lifecycle the test drives by hand. */
    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** Checks and the retry delay run on the main looper, which the test advances. */
    private val runner =
        SunsetCheckRunner(Duration.ZERO, now = { Duration.ZERO }, scope = CoroutineScope(Dispatchers.Main))

    private fun compose(disabled: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown) {
                    val g = generation
                    SunsetCheckerPrompt(
                        runner = runner,
                        minimumInterval = Duration.ZERO,
                        memory = memory,
                        strings = strings,
                        appIcon = null,
                        onVisibilityChange = { events += "visible $it" },
                        onPromptChange = { events += "kind $it" },
                        onFirstCheckComplete = { events += "settled" },
                        // Inline, capturing composition state: a new instance whenever `generation` changes.
                        onCheckingChange = { events += "checking $it${if (g > 0) " ($g)" else ""}" },
                        disabled = disabled,
                    ) {
                        val answer = CompletableDeferred<SunsetCheckOutcome>()
                        answers += answer
                        answer.await()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun answer(outcome: SunsetCheckOutcome) {
        answers.last().complete(outcome)
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
    fun launchCheckIsTrueThenFalse() {
        compose()
        assertEquals(listOf("checking true"), events)
        answer(upToDate)
        assertEquals(listOf("checking true", "settled", "checking false"), events)
    }

    @Test
    fun aHardResultIsReportedBeforeTheFalse() {
        compose()
        answer(SunsetCheckOutcome(hard, failed = false))
        compose.onNodeWithText(strings.hardTitle).assertExists()
        assertEquals(listOf("checking true", "kind HARD", "settled", "checking false"), events)
    }

    @Test
    fun resumeRecheckIsTrueThenFalseAndAHardBlockComesBeforeTheFalse() {
        compose()
        answer(upToDate)
        events.clear()

        backgroundAndReturn()
        assertEquals(2, answers.size)
        assertEquals(listOf("checking true"), events, "the re-check is in flight")
        answer(SunsetCheckOutcome(hard, failed = false))
        assertEquals(listOf("checking true", "kind HARD", "checking false"), events)
    }

    @Test
    fun theRecheckTrueIsReportedAsItStartsBeforeTheNextFrame() {
        compose()
        answer(upToDate)
        events.clear()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        // No frame in between: a gate reading the flag in the next composition already holds.
        assertEquals(listOf("checking true"), events)
    }

    @Test
    fun theQuickRetryIsTrueThenFalse() {
        compose()
        answer(SunsetCheckOutcome(SunsetDecision.UpToDate, failed = true))
        assertEquals(listOf("checking true", "settled", "checking false"), events)
        events.clear()

        shadowOf(Looper.getMainLooper()).idleFor(runner.retryDelay.plus(1.seconds).toJavaDuration())
        compose.waitForIdle()
        assertEquals(2, answers.size, "the retry started")
        assertEquals(listOf("checking true"), events)
        answer(SunsetCheckOutcome(hard, failed = false))
        assertEquals(listOf("checking true", "kind HARD", "checking false"), events)
    }

    @Test
    fun aNewLambdaInstanceSendsNothingAndTheFalseGoesToTheCallbackThatGotTheTrue() {
        compose()
        repeat(2) {
            generation++
            compose.waitForIdle()
        }
        answer(upToDate)
        assertEquals(listOf("checking true", "settled", "checking false"), events)

        backgroundAndReturn()
        answer(upToDate)
        assertEquals(
            listOf("checking true", "settled", "checking false", "checking true (2)", "checking false (2)"),
            events,
        )
    }

    @Test
    fun leavingTheCompositionMidCheckSendsFalseAndARecreatedOneReportsTheRunningCheck() {
        compose()
        // As in a rotation: the composition goes away while the launch check is still running.
        shown = false
        compose.waitForIdle()
        assertEquals(listOf("checking true", "checking false"), events)
        shown = true
        compose.waitForIdle()
        assertEquals(1, answers.size, "no second launch check")
        assertEquals(listOf("checking true", "checking false", "checking true"), events)

        answer(upToDate)
        assertEquals(listOf("checking true", "checking false", "checking true", "settled", "checking false"), events)
    }

    @Test
    fun aRecreatedCompositionDoesNotReportAFinishedCheck() {
        compose()
        answer(upToDate)
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()
        assertEquals(listOf("checking true", "settled", "checking false"), events)
    }

    @Test
    fun disabledNeverReportsChecking() {
        compose(disabled = true)
        backgroundAndReturn()
        answers.forEach { it.complete(upToDate) }
        compose.waitForIdle()
        assertEquals(listOf("settled"), events)
    }

    @Test
    fun disabledCheckerOnThePublicOverloadNeverReportsChecking() {
        val checking = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                SunsetPrompt(checker = SunsetChecker.disabled(), memory = memory, onCheckingChange = { checking += it })
            }
        }
        compose.waitForIdle()
        backgroundAndReturn()
        assertEquals(emptyList(), checking)
    }
}
