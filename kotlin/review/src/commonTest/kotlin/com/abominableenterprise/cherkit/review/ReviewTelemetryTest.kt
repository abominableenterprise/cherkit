package com.abominableenterprise.cherkit.review

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

class ReviewTelemetryTest {
    private class Sink : TelemetrySink {
        val breadcrumbs = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
            error("The rules do not report errors")
        }
        override fun breadcrumb(name: String, attributes: Map<String, Any>) {
            breadcrumbs += name to attributes
        }
    }

    @Test
    fun successfulMarkReportsOnceWithoutVersionOrInterruptionSource() {
        val sink = Sink()
        val review = ReviewCoordinator(InMemoryReviewStore(), "private-version", cooldown = Duration.ZERO, telemetrySink = sink)
        assertFalse(review.markPrompted())
        review.arm()
        review.setInterruptionVisible(true, "private-modal")
        assertFalse(review.markPrompted())
        assertTrue(sink.breadcrumbs.isEmpty())
        review.setInterruptionVisible(false, "private-modal")
        assertTrue(review.markPrompted())
        assertFalse(review.markPrompted())
        assertEquals(listOf("cherkit.review.prompted" to emptyMap()), sink.breadcrumbs)
    }

    @Test
    fun failedSaveDoesNotReportPrompted() {
        val sink = Sink()
        val store = object : ReviewStore {
            override fun load(): ReviewState = ReviewState(isDue = true)
            override fun save(state: ReviewState) { error("private storage failure") }
        }
        val review = ReviewCoordinator(store, "private-version", telemetrySink = sink)
        assertFailsWith<IllegalStateException> { review.markPrompted() }
        assertTrue(sink.breadcrumbs.isEmpty())
    }

    @Test
    fun disabledAndIneligibleCoordinatorsAreSilent() {
        val sink = Sink()
        for (review in listOf(
            ReviewCoordinator(InMemoryReviewStore(), "1", enabled = false, telemetrySink = sink),
            ReviewCoordinator(InMemoryReviewStore(), "1", isEligible = { false }, telemetrySink = sink),
        )) {
            review.arm()
            assertFalse(review.markPrompted())
        }
        assertTrue(sink.breadcrumbs.isEmpty())
    }

    @Test
    fun throwingSinkCannotBreakSuccessfulMark() {
        var attempts = 0
        val sink = object : TelemetrySink {
            override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) = Unit
            override fun breadcrumb(name: String, attributes: Map<String, Any>) {
                attempts++
                error("provider unavailable")
            }
        }
        val store = InMemoryReviewStore()
        val review = ReviewCoordinator(store, "1", telemetrySink = sink).apply { arm() }
        assertTrue(review.markPrompted())
        assertFalse(review.markPrompted())
        assertEquals("1", store.load().lastPromptedVersion)
        assertEquals(1, attempts)
    }

    @Test
    fun nullSinkPreservesPromptAndPersistence() {
        for (explicitNull in listOf(false, true)) {
            val store = InMemoryReviewStore()
            val review = if (explicitNull) ReviewCoordinator(store, "1", telemetrySink = null)
                         else ReviewCoordinator(store, "1")
            review.arm()
            assertTrue(review.markPrompted())
            assertEquals("1", store.load().lastPromptedVersion)
            assertFalse(review.markPrompted())
        }
    }
}
