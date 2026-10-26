package com.abominableenterprise.cherkit.sunset.compose

import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle.Event
import com.abominableenterprise.cherkit.sunset.AndroidSunset
import com.abominableenterprise.cherkit.sunset.SunsetCheckScheduler
import com.abominableenterprise.cherkit.sunset.SunsetChecker
import com.abominableenterprise.cherkit.sunset.SunsetDecision
import com.abominableenterprise.cherkit.sunset.SunsetPrompt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The runner behind `SunsetPrompt(checker = ...)` turns Lifecycle events into checks and keeps the decision. A fake
 * clock and fetch; the checks run unconfined, so a check that does not suspend has finished when the call returns.
 */
class SunsetCheckRunnerTest {
    private val hard = SunsetDecision.HardUpdate(SunsetPrompt("https://play.google.com/store/apps/details?id=a", null))
    private var clock = Duration.ZERO
    private var fetches = 0

    private fun runner(minimumInterval: Duration = 1.hours) =
        SunsetCheckRunner(minimumInterval, now = { clock }, scope = CoroutineScope(Dispatchers.Unconfined))

    private val check: suspend () -> SunsetDecision = {
        fetches++
        hard
    }

    private fun SunsetCheckRunner.events(vararg events: Event) {
        for (event in events) lifecycleEvent(event, check)
    }

    /** What an activity reports when it starts: no ON_STOP, so no check. */
    private val start = arrayOf(Event.ON_CREATE, Event.ON_START, Event.ON_RESUME)

    /** Home, then back to the app. */
    private val backgroundAndReturn = arrayOf(Event.ON_PAUSE, Event.ON_STOP, Event.ON_START, Event.ON_RESUME)

    @Test
    fun oneFetchAtLaunch() {
        val runner = runner()
        runner.launched(check)
        runner.events(*start)
        // Composed again (the root rebuilt, the activity recreated): still one.
        runner.launched(check)
        runner.events(*start)
        assertEquals(1, fetches)
        assertEquals(hard, runner.decision)
        assertFalse(runner.scheduler.isChecking)
    }

    @Test
    fun stopThenResumeFetchesAgain() {
        val runner = runner(minimumInterval = Duration.ZERO)
        runner.launched(check)
        runner.events(*start)
        runner.events(*backgroundAndReturn)
        assertEquals(2, fetches)
        runner.events(*backgroundAndReturn)
        assertEquals(3, fetches)
    }

    @Test
    fun pauseThenResumeDoesNotFetch() {
        val runner = runner(minimumInterval = Duration.ZERO)
        runner.launched(check)
        runner.events(*start)
        // A system dialog or a permission prompt over the activity.
        runner.events(Event.ON_PAUSE, Event.ON_RESUME, Event.ON_PAUSE, Event.ON_RESUME)
        assertEquals(1, fetches)
    }

    @Test
    fun aCheckInFlightSkipsANewOne() {
        val pending = CompletableDeferred<SunsetDecision>()
        val slow: suspend () -> SunsetDecision = {
            fetches++
            pending.await()
        }
        val runner = runner(minimumInterval = Duration.ZERO)
        runner.launched(slow)
        for (event in backgroundAndReturn) runner.lifecycleEvent(event, slow)
        assertEquals(1, fetches)
        assertNull(runner.decision)

        pending.complete(SunsetDecision.UpToDate)
        assertEquals(SunsetDecision.UpToDate, runner.decision)
        for (event in backgroundAndReturn) runner.lifecycleEvent(event, slow)
        assertEquals(2, fetches)
    }

    @Test
    fun reChecksRespectTheInterval() {
        val runner = runner(minimumInterval = 1.hours)
        runner.launched(check)
        clock = 59.minutes
        runner.events(*backgroundAndReturn)
        assertEquals(1, fetches, "too soon after the launch check")

        clock = 60.minutes
        runner.events(*backgroundAndReturn)
        assertEquals(2, fetches, "the interval has passed")

        clock = 119.minutes
        runner.events(*backgroundAndReturn)
        assertEquals(2, fetches, "counted from the last check")

        runner.setMinimumInterval(Duration.ZERO)
        runner.events(*backgroundAndReturn)
        assertEquals(3, fetches, "the interval can change")
    }

    @Test
    fun aFailedCheckKeepsTheDecisionAndAllowsTheNextOne() {
        val runner = runner(minimumInterval = Duration.ZERO)
        runner.launched(check)
        runner.events(*backgroundAndReturn.copyOfRange(0, 2))
        runner.lifecycleEvent(Event.ON_RESUME) { error("boom") }
        assertEquals(hard, runner.decision)
        assertFalse(runner.scheduler.isChecking)
        runner.events(*backgroundAndReturn)
        assertEquals(2, fetches)
    }

    @Test
    fun disabledCheckerAnswersUpToDate() {
        val runner = runner()
        runner.launched { AndroidSunset.check(SunsetChecker.disabled(), ContextWrapper(null), listOf("en")) }
        assertIs<SunsetDecision.UpToDate>(runner.decision)
    }

    @Test
    fun lifecycleEventsMapToPhases() {
        assertEquals(SunsetCheckScheduler.Phase.BACKGROUND, SunsetCheckRunner.phase(Event.ON_STOP))
        assertEquals(SunsetCheckScheduler.Phase.ACTIVE, SunsetCheckRunner.phase(Event.ON_RESUME))
        for (event in listOf(Event.ON_CREATE, Event.ON_START, Event.ON_PAUSE, Event.ON_DESTROY, Event.ON_ANY)) {
            assertEquals(SunsetCheckScheduler.Phase.INACTIVE, SunsetCheckRunner.phase(event), "$event")
        }
    }
}
