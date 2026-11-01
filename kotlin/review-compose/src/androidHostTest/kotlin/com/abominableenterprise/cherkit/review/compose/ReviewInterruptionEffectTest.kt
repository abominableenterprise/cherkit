package com.abominableenterprise.cherkit.review.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.abominableenterprise.cherkit.review.InMemoryReviewStore
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** [ReviewInterruptionEffect]: visible while shown, cleared on hide and dispose, safe for a flickering source. */
@RunWith(RobolectricTestRunner::class)
class ReviewInterruptionEffectTest {
    @get:Rule
    val compose = createComposeRule()

    private var now = 0L
    private val review = ReviewCoordinator(InMemoryReviewStore(), "1.0", cooldown = 10.seconds, clock = { now })
        .apply { arm() }

    @Test
    fun reportsVisibleAndClearsOnHideAndDispose() {
        var visible by mutableStateOf(true)
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) ReviewInterruptionEffect(review, "paywall", visible) }
        compose.waitForIdle()
        assertEquals(setOf("paywall"), review.visibleInterruptions)

        visible = false
        compose.waitForIdle()
        assertTrue(review.visibleInterruptions.isEmpty())
        assertEquals(10.seconds, review.cooldownRemaining)

        visible = true
        compose.waitForIdle()
        assertEquals(setOf("paywall"), review.visibleInterruptions)
        now += 20_000
        shown = false
        compose.waitForIdle()
        assertTrue(review.visibleInterruptions.isEmpty())
        assertEquals(10.seconds, review.cooldownRemaining, "leaving the composition starts the cooldown too")
    }

    @Test
    fun aFlickeringSourceEndsHiddenAndRestartsTheCooldownOnEveryHide() {
        var visible by mutableStateOf(false)
        compose.setContent { ReviewInterruptionEffect(review, "banner", visible) }
        compose.waitForIdle()
        assertTrue(review.shouldPrompt())

        // Fast toggles across frames: each hide restarts the cooldown.
        repeat(5) {
            visible = true
            compose.waitForIdle()
            assertFalse(review.shouldPrompt())
            visible = false
            compose.waitForIdle()
            now += 3_000
        }
        assertTrue(review.visibleInterruptions.isEmpty(), "never left visible")
        assertEquals(7.seconds, review.cooldownRemaining, "3 s after the last hide")
        // A toggle within one frame never shows: nothing changes.
        visible = true
        visible = false
        compose.waitForIdle()
        assertTrue(review.visibleInterruptions.isEmpty())
        assertEquals(7.seconds, review.cooldownRemaining)
        assertFalse(review.shouldPrompt())

        visible = true
        compose.waitForIdle()
        now += 60_000
        assertFalse(review.shouldPrompt(), "held for as long as it stays visible")
        visible = false
        compose.waitForIdle()
        assertEquals(10.seconds, review.cooldownRemaining, "the cooldown restarts at the latest hide")
        now += 10_000
        assertTrue(review.shouldPrompt())
    }

    @Test
    fun twoSourcesAreIndependent() {
        var paywall by mutableStateOf(true)
        compose.setContent {
            ReviewInterruptionEffect(review, "paywall", paywall)
            ReviewInterruptionEffect(review, "error", true)
        }
        compose.waitForIdle()
        paywall = false
        compose.waitForIdle()
        assertEquals(setOf("error"), review.visibleInterruptions)
    }
}
