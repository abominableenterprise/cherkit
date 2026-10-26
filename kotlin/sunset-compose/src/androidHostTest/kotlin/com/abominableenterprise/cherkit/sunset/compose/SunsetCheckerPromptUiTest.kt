package com.abominableenterprise.cherkit.sunset.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.sunset.SunsetChecker
import com.abominableenterprise.cherkit.sunset.SunsetDecision
import com.abominableenterprise.cherkit.sunset.SunsetPrompt
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

/** `SunsetPrompt(checker = ...)` composed with a fake lifecycle and fetch, and `onVisibilityChange` on both overloads. */
@RunWith(RobolectricTestRunner::class)
class SunsetCheckerPromptUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val strings = SunsetStrings()
    private val soft = SunsetDecision.SoftUpdate(
        SunsetPrompt("https://play.google.com/store/apps/details?id=a", null, AppVersion.parse("2.0")),
    )
    private val visibility = mutableListOf<Boolean>()
    private var fetches = 0
    private lateinit var memory: SunsetSoftPromptMemory

    @Before
    fun setUp() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("sunset-checker-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        memory = SunsetSoftPromptMemory(prefs)
    }

    /** A lifecycle the test drives by hand. */
    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun compose(
        runner: SunsetCheckRunner,
        owner: LifecycleOwner,
        context: Context? = null,
        answers: () -> SunsetDecision,
    ) {
        compose.setContent {
            val locals = listOfNotNull(LocalLifecycleOwner provides owner, context?.let { LocalContext provides it })
            CompositionLocalProvider(*locals.toTypedArray()) {
                SunsetCheckerPrompt(
                    runner = runner,
                    minimumInterval = Duration.ZERO,
                    memory = memory,
                    strings = strings,
                    appIcon = null,
                    onVisibilityChange = { visibility += it },
                    check = {
                        fetches++
                        answers()
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun newRunner() =
        SunsetCheckRunner(Duration.ZERO, now = { Duration.ZERO }, scope = CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun checksAtLaunchAndAfterStopThenResumeButNotAfterPause() {
        val owner = FakeLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var answer: SunsetDecision = SunsetDecision.UpToDate
        compose(newRunner(), owner) { answer }
        assertEquals(1, fetches)
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()

        answer = soft
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        compose.waitForIdle()
        assertEquals(1, fetches)

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        compose.waitForIdle()
        assertEquals(2, fetches)
        compose.onNodeWithText(strings.softTitle).assertExists()
        assertEquals(listOf(true), visibility)

        compose.onNodeWithText(strings.notNowButton).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()
        assertEquals("2.0", memory.dismissedVersion)
        assertEquals(listOf(true, false), visibility)
    }

    @Test
    fun aConfigurationChangeIsNotAReturnFromTheBackground() {
        val rotating = object : Activity() {
            override fun isChangingConfigurations(): Boolean = true
        }
        val owner = FakeLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        compose(newRunner(), owner, ContextWrapper(rotating)) { SunsetDecision.UpToDate }
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        compose.waitForIdle()
        assertEquals(1, fetches)
    }

    @Test
    fun sharedRunnerSurvivesRecomposition() {
        val runner = newRunner()
        val owner = FakeLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var show by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (show) {
                    SunsetCheckerPrompt(runner, Duration.ZERO, memory, strings, null, {}) {
                        fetches++
                        SunsetDecision.UpToDate
                    }
                }
            }
        }
        compose.waitForIdle()
        show = false
        compose.waitForIdle()
        show = true
        compose.waitForIdle()
        assertEquals(1, fetches)
    }

    @Test
    fun disabledCheckerShowsNothing() {
        compose.setContent {
            SunsetPrompt(checker = SunsetChecker.disabled(), memory = memory, onVisibilityChange = { visibility += it })
        }
        compose.waitForIdle()
        assertEquals(SunsetDecision.UpToDate, SunsetCheckRunner.shared.decision)
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()
        compose.onNodeWithText(strings.hardTitle).assertDoesNotExist()
        assertEquals(emptyList(), visibility)
    }

    @Test
    fun decisionOverloadReportsTheSoftDialogOnly() {
        var decision: SunsetDecision? by mutableStateOf(null)
        compose.setContent {
            SunsetPrompt(decision, onDecisionHandled = { decision = null }, memory = memory) { visibility += it }
        }
        compose.waitForIdle()
        assertEquals(emptyList(), visibility)

        decision = soft
        compose.waitForIdle()
        assertEquals(listOf(true), visibility)
        compose.onNodeWithText(strings.updateButton).performClick()
        compose.waitForIdle()
        assertEquals(listOf(true, false), visibility)

        // The hard block is not reported: an app keeps checking behind it so it can go away.
        decision = SunsetDecision.HardUpdate(soft.prompt)
        compose.waitForIdle()
        compose.onNodeWithText(strings.hardTitle).assertExists()
        assertEquals(listOf(true, false), visibility)

        // Already answered "Not now" for 2.0: not shown, not reported.
        memory.rememberNotNow("2.0")
        decision = SunsetDecision.SoftUpdate(soft.prompt.copy())
        compose.waitForIdle()
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()
        assertEquals(listOf(true, false), visibility)
    }
}
