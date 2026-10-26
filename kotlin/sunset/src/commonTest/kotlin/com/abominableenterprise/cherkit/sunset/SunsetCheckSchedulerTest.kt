package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.sunset.SunsetCheckScheduler.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * When a Sunset check runs: once at launch, again only on a real return from the background, never two at once, and
 * re-checks no more often than `minimumInterval`; a failed check is retried once and does not count. The same cases as Swift's `SunsetCheckSchedulerTests`.
 */
class SunsetCheckSchedulerTest {
    private val start = 1_000_000.seconds

    private fun at(seconds: Int): Duration = start + seconds.seconds

    /** A scheduler whose launch check has started and finished at [start]. */
    private fun launched(minimumInterval: Duration = 1.hours): SunsetCheckScheduler {
        val scheduler = SunsetCheckScheduler(minimumInterval)
        assertTrue(scheduler.shouldCheckAtLaunch(start))
        scheduler.checkFinished()
        return scheduler
    }

    @Test
    fun defaultIntervalIsAnHour() {
        assertEquals(3600.seconds, SunsetCheckScheduler().minimumInterval)
        assertEquals(1.hours, SunsetCheckScheduler.DEFAULT_MINIMUM_INTERVAL)
    }

    @Test
    fun coldLaunchRunsExactlyOneCheck() {
        val scheduler = SunsetCheckScheduler()
        var checks = 0
        // A cold launch: first composition, then the initial lifecycle events.
        if (scheduler.shouldCheckAtLaunch(start)) checks++
        for (phase in listOf(Phase.INACTIVE, Phase.INACTIVE, Phase.ACTIVE)) {
            if (scheduler.shouldCheck(phase, at(0))) checks++
        }
        scheduler.checkFinished()
        // Composing again (the root rebuilt) does not check either.
        if (scheduler.shouldCheckAtLaunch(at(1))) checks++
        assertEquals(1, checks)
    }

    @Test
    fun initialActiveBeforeLaunchDoesNotCheckAndLaunchStillDoes() {
        val scheduler = SunsetCheckScheduler()
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, start))
        assertTrue(scheduler.shouldCheckAtLaunch(start))
    }

    @Test
    fun inactiveToActiveRunsNoCheck() {
        val scheduler = launched(minimumInterval = Duration.ZERO)
        assertFalse(scheduler.shouldCheck(Phase.INACTIVE, at(7200)))
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, at(7200)))
    }

    @Test
    fun backgroundToActiveRunsOneCheck() {
        val scheduler = launched()
        assertFalse(scheduler.shouldCheck(Phase.INACTIVE, at(7200)))
        assertFalse(scheduler.shouldCheck(Phase.BACKGROUND, at(7200)))
        assertFalse(scheduler.shouldCheck(Phase.INACTIVE, at(7300)))
        assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(7300)))
        scheduler.checkFinished()
        // A second ACTIVE without a new trip to the background does not check again.
        assertFalse(scheduler.shouldCheck(Phase.INACTIVE, at(7400)))
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, at(7400)))
    }

    @Test
    fun backgroundDirectlyToActiveAlsoChecks() {
        val scheduler = launched()
        assertFalse(scheduler.shouldCheck(Phase.BACKGROUND, at(7200)))
        assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(7200)))
    }

    @Test
    fun inFlightCheckSkipsANewOne() {
        val scheduler = SunsetCheckScheduler(Duration.ZERO)
        assertTrue(scheduler.shouldCheckAtLaunch(start))
        assertTrue(scheduler.isChecking)
        // The launch check is still running when the app goes away and comes back.
        scheduler.shouldCheck(Phase.BACKGROUND, at(10))
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, at(20)))

        scheduler.checkFinished()
        assertFalse(scheduler.isChecking)
        scheduler.shouldCheck(Phase.BACKGROUND, at(30))
        assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(40)))
        // And again while that one runs.
        scheduler.shouldCheck(Phase.BACKGROUND, at(50))
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, at(60)))
    }

    @Test
    fun intervalIsRespected() {
        val scheduler = launched(minimumInterval = 3600.seconds)
        scheduler.shouldCheck(Phase.BACKGROUND, at(60))
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, at(3599)), "too soon after the launch check")

        scheduler.shouldCheck(Phase.BACKGROUND, at(3600))
        assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(3600)), "the interval has passed")
        assertEquals(at(3600), scheduler.lastCheckStarted)
        scheduler.checkFinished()

        scheduler.shouldCheck(Phase.BACKGROUND, at(4000))
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, at(7199)), "counted from the last check")
        scheduler.shouldCheck(Phase.BACKGROUND, at(7200))
        assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(7200)))
    }

    @Test
    fun zeroIntervalChecksOnEveryReturn() {
        val scheduler = launched(minimumInterval = Duration.ZERO)
        for (second in listOf(1, 2, 3)) {
            scheduler.shouldCheck(Phase.BACKGROUND, at(second))
            assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(second)))
            scheduler.checkFinished()
        }
    }

    @Test
    fun failedLaunchCheckDoesNotConsumeTheIntervalAndAsksForOneRetry() {
        val scheduler = SunsetCheckScheduler(1.hours)
        assertTrue(scheduler.shouldCheckAtLaunch(start))
        assertTrue(scheduler.checkFinished(failed = true), "the first failure asks for the quick retry")
        assertFalse(scheduler.isChecking)
        assertNull(scheduler.lastCheckStarted, "a failed check does not count toward the interval")
        assertTrue(scheduler.isRetryPending)

        assertTrue(scheduler.shouldRetry(at(3)))
        assertFalse(scheduler.shouldRetry(at(3)), "the retry starts once")
        assertFalse(scheduler.checkFinished(failed = true), "a second failure in a row does not retry again")
        assertFalse(scheduler.shouldRetry(at(6)))
        assertNull(scheduler.lastCheckStarted)
        assertEquals(2, scheduler.failuresInARow)
    }

    @Test
    fun afterTwoFailuresTheNextForegroundRechecksDespiteTheInterval() {
        val scheduler = SunsetCheckScheduler(1.hours)
        assertTrue(scheduler.shouldCheckAtLaunch(start))
        scheduler.checkFinished(failed = true)
        assertTrue(scheduler.shouldRetry(at(3)))
        scheduler.checkFinished(failed = true)

        // INACTIVE → ACTIVE (a system dialog) still never checks.
        assertFalse(scheduler.shouldCheck(Phase.INACTIVE, at(10)))
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, at(10)))
        // A real return from the background does, long before the interval.
        scheduler.shouldCheck(Phase.BACKGROUND, at(20))
        assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(30)))
        // It succeeds: from now on the interval counts from it again.
        assertFalse(scheduler.checkFinished(failed = false))
        assertEquals(0, scheduler.failuresInARow)
        assertEquals(at(30), scheduler.lastCheckStarted)
        scheduler.shouldCheck(Phase.BACKGROUND, at(40))
        assertFalse(scheduler.shouldCheck(Phase.ACTIVE, at(50)), "the interval applies again")
    }

    @Test
    fun failedRecheckKeepsTheLastGoodCheckForTheInterval() {
        val scheduler = launched(minimumInterval = 1.hours)
        scheduler.shouldCheck(Phase.BACKGROUND, at(3600))
        assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(3600)))
        assertTrue(scheduler.checkFinished(failed = true))
        assertEquals(start, scheduler.lastCheckStarted, "the failed re-check is not counted; the launch check still is")
    }

    @Test
    fun aCheckDuringTheRetryDelayMakesTheRetryStale() {
        val scheduler = SunsetCheckScheduler(1.hours)
        assertTrue(scheduler.shouldCheckAtLaunch(start))
        scheduler.checkFinished(failed = true)
        // The app goes away and back before the retry fires: that return re-checks instead.
        scheduler.shouldCheck(Phase.BACKGROUND, at(1))
        assertTrue(scheduler.shouldCheck(Phase.ACTIVE, at(2)))
        assertFalse(scheduler.shouldRetry(at(3)), "the pending retry is dropped once another check starts")
    }

    @Test
    fun successfulChecksNeverAskForARetry() {
        val scheduler = SunsetCheckScheduler()
        assertTrue(scheduler.shouldCheckAtLaunch(start))
        assertFalse(scheduler.checkFinished(failed = false))
        assertFalse(scheduler.shouldRetry(at(3)))
        assertEquals(start, scheduler.lastCheckStarted)
    }
}
