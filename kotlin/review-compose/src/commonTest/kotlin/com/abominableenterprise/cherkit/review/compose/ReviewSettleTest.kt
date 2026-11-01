package com.abominableenterprise.cherkit.review.compose

import com.abominableenterprise.cherkit.review.InMemoryReviewStore
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The settle delay and the re-checks, in virtual time. */
class ReviewSettleTest {
    private fun TestScope.coordinator() =
        ReviewCoordinator(InMemoryReviewStore(), "1.0", cooldown = 10.seconds, clock = { testScheduler.currentTime })

    @Test
    fun waitsTheSettleDelayBeforeLaunching() = runTest {
        val review = coordinator().apply { arm() }
        var launches = 0
        val run = async {
            ReviewSettle.awaitThenLaunch(review, 1500.milliseconds) { launches++; ReviewLaunchResult.LAUNCHED }
        }
        advanceTimeBy(1499)
        runCurrent()
        assertEquals(0, launches)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, launches)
        assertEquals(ReviewLaunchResult.LAUNCHED, run.await())
    }

    @Test
    fun keepsCheckingUntilThePromptBecomesDue() = runTest {
        val review = coordinator()
        var launches = 0
        val run = async { ReviewSettle.awaitThenLaunch(review, 1.seconds) { launches++; ReviewLaunchResult.LAUNCHED } }
        advanceTimeBy(5_000)
        assertEquals(0, launches)
        review.arm()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(1, launches)
        run.await()
    }

    @Test
    fun waitsOutTheCooldownThenSettlesAgain() = runTest {
        val review = coordinator().apply { arm() }
        review.setInterruptionVisible(true, "banner")
        review.setInterruptionVisible(false, "banner")
        var launchedAt = -1L
        val run = async {
            ReviewSettle.awaitThenLaunch(review, 1.seconds) {
                launchedAt = testScheduler.currentTime
                ReviewLaunchResult.LAUNCHED
            }
        }
        run.await()
        // 1 s settle, then the 9 s left of the cooldown, then another 1 s settle.
        assertEquals(11_000L, launchedAt)
    }

    @Test
    fun aSkippedLaunchKeepsWaiting() = runTest {
        val review = coordinator().apply { arm() }
        val results = ArrayDeque(listOf(ReviewLaunchResult.SKIPPED, ReviewLaunchResult.REQUEST_FAILED))
        var launches = 0
        val result = ReviewSettle.awaitThenLaunch(review, 1.seconds) { launches++; results.removeFirst() }
        assertEquals(ReviewLaunchResult.REQUEST_FAILED, result)
        assertEquals(2, launches)
        assertEquals(2_000L, testScheduler.currentTime)
    }

    @Test
    fun aDisabledCoordinatorReturnsAtOnce() = runTest {
        var launched = false
        val result = ReviewSettle.awaitThenLaunch(ReviewCoordinator.disabled()) {
            launched = true
            ReviewLaunchResult.LAUNCHED
        }
        assertEquals(ReviewLaunchResult.SKIPPED, result)
        assertFalse(launched)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun aFlickeringSourceRestartsTheCooldownOnEveryHide() = runTest {
        val review = coordinator().apply { arm() }
        repeat(5) {
            review.setInterruptionVisible(true, "banner")
            advanceTimeBy(100)
            review.setInterruptionVisible(false, "banner")
            advanceTimeBy(3_000)
        }
        // 3 s after the last hide: 7 s of the 10 s cooldown are left.
        assertEquals(7.seconds, review.cooldownRemaining)
        assertFalse(review.shouldPrompt())
        advanceTimeBy(7_000)
        assertTrue(review.shouldPrompt())
    }
}
