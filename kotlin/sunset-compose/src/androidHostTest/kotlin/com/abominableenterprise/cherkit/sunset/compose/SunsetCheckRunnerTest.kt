package com.abominableenterprise.cherkit.sunset.compose

import androidx.lifecycle.Lifecycle.Event
import com.abominableenterprise.cherkit.sunset.FetchSunsetConfigSource
import com.abominableenterprise.cherkit.sunset.SunsetCheckOutcome
import com.abominableenterprise.cherkit.sunset.SunsetCheckScheduler
import com.abominableenterprise.cherkit.sunset.SunsetChecker
import com.abominableenterprise.cherkit.sunset.SunsetConfigCache
import com.abominableenterprise.cherkit.sunset.SunsetDecision
import com.abominableenterprise.cherkit.sunset.SunsetPrompt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    private val check: suspend () -> SunsetCheckOutcome = {
        fetches++
        SunsetCheckOutcome(hard, failed = false)
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
        val pending = CompletableDeferred<SunsetCheckOutcome>()
        val slow: suspend () -> SunsetCheckOutcome = {
            fetches++
            pending.await()
        }
        val runner = runner(minimumInterval = Duration.ZERO)
        runner.launched(slow)
        for (event in backgroundAndReturn) runner.lifecycleEvent(event, slow)
        assertEquals(1, fetches)
        assertNull(runner.decision)

        pending.complete(SunsetCheckOutcome(SunsetDecision.UpToDate, failed = false))
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
        assertNull(runner.retryJob, "a check that throws is not a failed fetch: no retry")
        assertEquals(hard, runner.decision)
        assertFalse(runner.scheduler.isChecking)
        runner.events(*backgroundAndReturn)
        assertEquals(2, fetches)
    }

    @Test
    fun disabledCheckerAnswersUpToDate() {
        val runner = runner()
        runner.launched { SunsetChecker.disabled().checkReportingFailure("1.0", "34", listOf("en")) }
        assertIs<SunsetDecision.UpToDate>(runner.decision)
        assertNull(runner.retryJob, "the disabled checker never fails, so it is never retried")
    }

    /** A real [SunsetChecker] over a fetch that answers [body] (`null`: a failed fetch) and counts its calls. */
    private inner class Source(var body: String?) {
        var bodyAfterFirstFetch: String? = null
        val checker = SunsetChecker(FetchSunsetConfigSource { fetch() }, NoCache)
        val check: suspend () -> SunsetCheckOutcome = { checker.checkReportingFailure("1.0", "34", listOf("en")) }

        private fun fetch(): String? {
            fetches++
            return bodyAfterFirstFetch?.takeIf { fetches > 1 } ?: body
        }
    }

    /** A runner whose checks and retries run in the test's own single-threaded [scope], with a short [retryDelay]. */
    private fun retryingRunner(scope: CoroutineScope, minimumInterval: Duration = 1.hours) =
        SunsetCheckRunner(minimumInterval, retryDelay = 10.milliseconds, now = { clock }, scope = scope)

    /** Waits for the running check, then for the quick retry and the check it starts, if any. */
    private suspend fun SunsetCheckRunner.settle() {
        job?.join()
        retryJob?.join()
        job?.join()
    }

    private fun SunsetCheckRunner.events(vararg events: Event, check: suspend () -> SunsetCheckOutcome) {
        for (event in events) lifecycleEvent(event, check)
    }

    @Test
    fun failedLaunchCheckIsRetriedOnceThenTheNextReturnFromTheBackgroundRechecks() = runBlocking<Unit> {
        val source = Source(body = null)
        val runner = retryingRunner(this, minimumInterval = 1.hours)
        runner.launched(source.check)
        runner.events(*start, check = source.check)
        runner.settle()
        assertEquals(2, fetches, "the launch check and one quick retry")
        assertNull(runner.retryJob, "no second retry")
        assertEquals(SunsetDecision.UpToDate, runner.decision, "fails open meanwhile")

        // The network comes back; the next real return from the background re-checks, well within the hour.
        source.body = HARD_CONFIG
        clock = 1.minutes
        runner.events(*backgroundAndReturn, check = source.check)
        runner.settle()
        assertEquals(3, fetches)
        assertIs<SunsetDecision.HardUpdate>(runner.decision)

        // That check succeeded, so the interval applies again.
        clock = 2.minutes
        runner.events(*backgroundAndReturn, check = source.check)
        runner.settle()
        assertEquals(3, fetches)
    }

    @Test
    fun quickRetryShowsTheHardBlockWhenTheFetchRecovers() = runBlocking<Unit> {
        val source = Source(body = null).apply { bodyAfterFirstFetch = HARD_CONFIG }
        val runner = retryingRunner(this)
        runner.launched(source.check)
        runner.settle()
        assertEquals(2, fetches)
        assertIs<SunsetDecision.HardUpdate>(runner.decision)
        assertEquals(0, runner.scheduler.failuresInARow)
    }

    @Test
    fun failedFetchWithAUsableCacheIsNotRetried() = runBlocking<Unit> {
        val cached = object : SunsetConfigCache {
            override suspend fun load(): String = HARD_CONFIG
            override suspend fun save(text: String) {}
        }
        val checker = SunsetChecker(FetchSunsetConfigSource { fetches++; null }, cached)
        val runner = retryingRunner(this)
        runner.launched { checker.checkReportingFailure("1.0", "34", listOf("en")) }
        runner.settle()
        assertEquals(1, fetches)
        assertNull(runner.retryJob)
        assertIs<SunsetDecision.HardUpdate>(runner.decision, "the cached hard block")
    }

    @Test
    fun aReturnFromTheBackgroundBeforeTheRetryDropsIt() = runBlocking<Unit> {
        val source = Source(body = null)
        val runner = SunsetCheckRunner(1.hours, retryDelay = 1.hours, now = { clock }, scope = this)
        runner.launched(source.check)
        runner.job?.join()
        val retry = runner.retryJob
        assertTrue(retry != null && retry.isActive, "the retry is waiting")

        // The app goes away and back before the retry fires: that return re-checks instead.
        source.body = HARD_CONFIG
        runner.events(*backgroundAndReturn, check = source.check)
        assertTrue(retry.isCancelled, "the pending retry is dropped once another check starts")
        runner.job?.join()
        assertEquals(2, fetches)
        assertNull(runner.retryJob)
        assertIs<SunsetDecision.HardUpdate>(runner.decision)
    }

    @Test
    fun defaultRetryDelayIsAFewSeconds() {
        assertEquals(3.seconds, SunsetCheckRunner().retryDelay)
        assertEquals(3.seconds, SunsetCheckRunner.DEFAULT_RETRY_DELAY)
    }

    @Test
    fun lifecycleEventsMapToPhases() {
        assertEquals(SunsetCheckScheduler.Phase.BACKGROUND, SunsetCheckRunner.phase(Event.ON_STOP))
        assertEquals(SunsetCheckScheduler.Phase.ACTIVE, SunsetCheckRunner.phase(Event.ON_RESUME))
        for (event in listOf(Event.ON_CREATE, Event.ON_START, Event.ON_PAUSE, Event.ON_DESTROY, Event.ON_ANY)) {
            assertEquals(SunsetCheckScheduler.Phase.INACTIVE, SunsetCheckRunner.phase(event), "$event")
        }
    }

    private object NoCache : SunsetConfigCache {
        override suspend fun load(): String? = null
        override suspend fun save(text: String) {}
    }

    private companion object {
        const val HARD_CONFIG =
            """{"schemaVersion": 1, "android": {"minimumVersion": "2.0", "storeURL": "https://play.google.com/store/apps/details?id=a"}}"""
    }
}
