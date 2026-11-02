package com.abominableenterprise.cherkit.review.compose

import com.abominableenterprise.cherkit.review.InMemoryReviewStore
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The launch order: request first, then markPrompted, then launch; a failed request consumes nothing. */
class ReviewLauncherTest {
    private val store = InMemoryReviewStore()
    private val review = ReviewCoordinator(store, appVersion = "1.0").apply { arm() }

    @Test
    fun marksPromptedOnlyAfterTheRequestSucceeds() = runTest {
        val flow = FakeReviewFlow(onRequest = { assertTrue(review.isDue, "not consumed before the request returns") })
        assertEquals(ReviewLaunchResult.LAUNCHED, ReviewLauncher.launch(review, flow))
        assertEquals(listOf("request", "launch:info"), flow.calls)
        assertFalse(review.isDue)
        assertEquals("1.0", store.load().lastPromptedVersion)
    }

    @Test
    fun originalPositionalIdleCallbackStillCompilesAndForwards() = runTest {
        val flow = FakeReviewFlow()
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(review, flow, { false }))
        assertTrue(flow.calls.isEmpty())
        assertTrue(review.isDue)
        assertEquals(ReviewLaunchResult.LAUNCHED, ReviewLauncher.launch(review, flow, { true }))
    }

    @Test
    fun trailingIdleCallbackRemainsUnambiguous() = runTest {
        val flow = FakeReviewFlow()
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(review, flow) { false })
        assertTrue(flow.calls.isEmpty())
    }

    @Test
    fun aFailedRequestLeavesThePromptDue() = runTest {
        val flow = FakeReviewFlow(requestFails = true)
        assertEquals(ReviewLaunchResult.REQUEST_FAILED, ReviewLauncher.launch(review, flow))
        assertEquals(listOf("request"), flow.calls)
        assertTrue(review.isDue)
        assertTrue(review.shouldPrompt())
        assertEquals(null, store.load().lastPromptedVersion)

        // The next check still prompts.
        flow.requestFails = false
        assertEquals(ReviewLaunchResult.LAUNCHED, ReviewLauncher.launch(review, flow))
    }

    @Test
    fun aFailedLaunchStillCountsAsPrompted() = runTest {
        val flow = FakeReviewFlow(launchFails = true)
        assertEquals(ReviewLaunchResult.LAUNCH_FAILED, ReviewLauncher.launch(review, flow))
        assertFalse(review.isDue)
    }

    @Test
    fun skipsWithoutRequestingWhenNotDue() = runTest {
        val notDue = ReviewCoordinator(InMemoryReviewStore(), appVersion = "1.0")
        val flow = FakeReviewFlow()
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(notDue, flow))
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(review, flow, isIdle = { false }))
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(ReviewCoordinator.disabled(), flow))
        assertEquals(emptyList<String>(), flow.calls)
    }

    @Test
    fun aModalThatAppearsDuringTheRequestCancelsTheLaunch() = runTest {
        val flow = FakeReviewFlow(onRequest = { review.setInterruptionVisible(true, "paywall") })
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(review, flow))
        assertEquals(listOf("request"), flow.calls)
        assertTrue(review.isDue)
    }

    @Test
    fun aScreenThatStopsBeingIdleDuringTheRequestCancelsTheLaunch() = runTest {
        var idle = true
        val flow = FakeReviewFlow(onRequest = { idle = false })
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(review, flow, isIdle = { idle }))
        assertTrue(review.isDue)
    }
}
