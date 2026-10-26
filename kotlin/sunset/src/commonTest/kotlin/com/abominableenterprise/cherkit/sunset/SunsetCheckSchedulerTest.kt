package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.sunset.SunsetCheckScheduler.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * When a Sunset check runs: once at launch, again only on a real return from the background, never two at once, and
 * re-checks no more often than `minimumInterval`. The same cases as Swift's `SunsetCheckSchedulerTests`.
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
}
