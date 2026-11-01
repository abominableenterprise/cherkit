package com.abominableenterprise.cherkit.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `disabled`, eligibility and the key-value store; the rules themselves run from the shared fixture in jvmTest. */
class ReviewCoordinatorTest {
    @Test
    fun disabledIsSwitchedOff() {
        val disabled = ReviewCoordinator.disabled()
        assertTrue(disabled.isDisabled)
        disabled.arm()
        disabled.setInterruptionVisible(true, ReviewInterruption.SUNSET_BLOCK)
        assertFalse(disabled.isDue)
        assertFalse(disabled.shouldPrompt())
        assertTrue(disabled.visibleInterruptions.isEmpty())
        assertFalse(ReviewCoordinator(InMemoryReviewStore(), "1.0").isDisabled)
        assertTrue(ReviewCoordinator(InMemoryReviewStore(), "1.0", enabled = false).isDisabled)
    }

    @Test
    fun wellKnownSourcesMatchSwift() {
        // The same strings as Swift's ReviewInterruption, so both platforms log and test the same sources.
        assertEquals("sunset.prompt", ReviewInterruption.SUNSET_PROMPT)
        assertEquals("sunset.block", ReviewInterruption.SUNSET_BLOCK)
        assertEquals("whatsNew", ReviewInterruption.WHATS_NEW)
    }

    @Test
    fun eligibilityIsReadOnEveryCall() {
        val review = ReviewCoordinator(InMemoryReviewStore(), "1.0")
        review.arm()
        review.isEligible = { false }
        assertFalse(review.shouldPrompt())
        review.isEligible = { true }
        assertTrue(review.shouldPrompt())
    }

    @Test
    fun keyValueStoreRoundTripsAndTreatsMissingAsDefaults() {
        val storage = MapStorage()
        val store = KeyValueReviewStore(storage)
        assertEquals(ReviewState(), store.load())
        store.save(ReviewState(2, true, "1.0"))
        assertEquals(ReviewState(2, true, "1.0"), store.load())
        assertEquals(2, storage.values["cherkit.review.successCount"])
        store.save(ReviewState())
        assertNull(storage.values["cherkit.review.lastPromptedVersion"])
        assertFalse("cherkit.review.lastPromptedVersion" in storage.values)
    }

    @Test
    fun aCoordinatorPersistsThroughTheKeyValueStore() {
        val storage = MapStorage()
        ReviewCoordinator(KeyValueReviewStore(storage), "1.0").arm()
        assertTrue(ReviewCoordinator(KeyValueReviewStore(storage), "1.0").markPrompted())
        val third = ReviewCoordinator(KeyValueReviewStore(storage), "1.0")
        third.arm()
        assertFalse(third.isDue)
    }

    private class MapStorage(val values: MutableMap<String, Any> = mutableMapOf()) : ReviewKeyValueStorage {
        override fun getInt(key: String): Int? = values[key] as? Int
        override fun getBoolean(key: String): Boolean? = values[key] as? Boolean
        override fun getString(key: String): String? = values[key] as? String
        override fun put(values: Map<String, Any?>) {
            values.forEach { (key, value) -> if (value == null) this.values.remove(key) else this.values[key] = value }
        }
    }
}
