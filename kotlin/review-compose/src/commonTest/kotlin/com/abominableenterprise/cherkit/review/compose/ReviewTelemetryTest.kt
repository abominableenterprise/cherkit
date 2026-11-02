package com.abominableenterprise.cherkit.review.compose

import com.abominableenterprise.cherkit.core.TelemetrySink
import com.abominableenterprise.cherkit.review.InMemoryReviewStore
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewTelemetryTest {
    private val sink = ReviewTelemetrySink()
    private fun review(): ReviewCoordinator =
        ReviewCoordinator(InMemoryReviewStore(), "private-version", telemetrySink = sink).apply { arm() }

    @Test
    fun eachFailureReportsExactlyOnceWithoutMessageOrInfo() = runTest {
        for (requestFails in listOf(true, false)) {
            sink.errors.clear()
            sink.breadcrumbs.clear()
            val review = review()
            val flow = object : ReviewFlow<String> {
                override suspend fun request(): String {
                    if (requestFails) error("private request message https://invalid.example/private")
                    return "private review info"
                }
                override suspend fun launch(info: String) { error("private launch message: $info") }
            }
            val result = ReviewLauncher.launch(review, flow, telemetrySink = sink)
            assertEquals(if (requestFails) ReviewLaunchResult.REQUEST_FAILED else ReviewLaunchResult.LAUNCH_FAILED, result)
            assertEquals(listOf(Triple("cherkit.review", if (requestFails) 1 else 2, emptyMap())), sink.errors)
            assertEquals(
                if (requestFails) emptyList() else listOf("cherkit.review.prompted" to emptyMap()),
                sink.breadcrumbs,
            )
            assertEquals(requestFails, review.isDue)
        }
    }

    @Test
    fun successReportsOnlyTheCoordinatorsBreadcrumbOnce() = runTest {
        val review = review()
        assertEquals(ReviewLaunchResult.LAUNCHED, ReviewLauncher.launch(review, FakeReviewFlow(), telemetrySink = sink))
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(review, FakeReviewFlow(), telemetrySink = sink))
        assertTrue(sink.errors.isEmpty())
        assertEquals(listOf("cherkit.review.prompted" to emptyMap()), sink.breadcrumbs)
    }

    @Test
    fun cancellationIsNotAnErrorAtEitherStage() = runTest {
        for (duringRequest in listOf(true, false)) {
            sink.breadcrumbs.clear()
            val review = review()
            val flow = object : ReviewFlow<String> {
                override suspend fun request(): String {
                    if (duringRequest) throw CancellationException("private cancellation")
                    return "info"
                }
                override suspend fun launch(info: String) { throw CancellationException("private cancellation") }
            }
            assertFailsWith<CancellationException> { ReviewLauncher.launch(review, flow, telemetrySink = sink) }
            assertTrue(sink.errors.isEmpty())
            assertEquals(duringRequest, review.isDue)
            assertEquals(if (duringRequest) 0 else 1, sink.breadcrumbs.size)
        }
    }

    @Test
    fun interruptedRequestIsSilentAndDoesNotConsumeTheVersion() = runTest {
        val review = review()
        val flow = FakeReviewFlow(onRequest = { review.setInterruptionVisible(true, "private source") })
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(review, flow, telemetrySink = sink))
        assertTrue(review.isDue)
        assertTrue(sink.errors.isEmpty())
        assertTrue(sink.breadcrumbs.isEmpty())
    }

    @Test
    fun throwingSinkCannotChangeEitherFailureResult() = runTest {
        var attempts = 0
        val throwing = object : TelemetrySink {
            override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
                attempts++
                error("provider unavailable")
            }
            override fun breadcrumb(name: String, attributes: Map<String, Any>) { error("provider unavailable") }
        }
        for (requestFails in listOf(true, false)) {
            val review = ReviewCoordinator(InMemoryReviewStore(), "1", telemetrySink = throwing).apply { arm() }
            val result = ReviewLauncher.launch(
                review, FakeReviewFlow(requestFails = requestFails, launchFails = !requestFails), throwing,
            ) { true }
            assertEquals(if (requestFails) ReviewLaunchResult.REQUEST_FAILED else ReviewLaunchResult.LAUNCH_FAILED, result)
            assertEquals(requestFails, review.isDue)
        }
        assertEquals(2, attempts)
    }

    @Test
    fun trailingIdleCallbackStillWorksWithDefaultSink() = runTest {
        assertEquals(ReviewLaunchResult.SKIPPED, ReviewLauncher.launch(review(), FakeReviewFlow()) { false })
    }

    @Test
    fun nullSinkPreservesFailureResults() = runTest {
        for (requestFails in listOf(true, false)) {
            val review = ReviewCoordinator(InMemoryReviewStore(), "1").apply { arm() }
            val flow = FakeReviewFlow(requestFails = requestFails, launchFails = !requestFails)
            assertEquals(
                if (requestFails) ReviewLaunchResult.REQUEST_FAILED else ReviewLaunchResult.LAUNCH_FAILED,
                ReviewLauncher.launch(review, flow, telemetrySink = null),
            )
            if (requestFails) assertTrue(review.isDue) else assertFalse(review.isDue)
        }
    }
}
