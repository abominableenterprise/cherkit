package com.abominableenterprise.cherkit.review.compose

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abominableenterprise.cherkit.review.InMemoryReviewStore
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [ReviewPromptEffect] with a fake lifecycle and a fake Play launch, on the compose test clock. */
@RunWith(RobolectricTestRunner::class)
class ReviewPromptEffectTest {
    @get:Rule
    val compose = createComposeRule()

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val review = ReviewCoordinator(InMemoryReviewStore(), "1.0").apply { arm() }
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
    private val launches = mutableListOf<Activity>()
    private val results = mutableListOf<ReviewLaunchResult>()
    private var idle by mutableStateOf(true)
    private var nextResult = ReviewLaunchResult.LAUNCHED

    private fun show(coordinator: ReviewCoordinator = review) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // A wrapped context, as inside a themed Surface: the effect still finds the Activity.
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalContext provides ContextWrapper(activity),
            ) {
                ReviewPromptEffect(
                    coordinator, idle, settleDelay = 1500.milliseconds, onResult = { results += it },
                ) { a, isIdle ->
                    launches += a
                    // The real order: a failed request consumes nothing.
                    when {
                        nextResult == ReviewLaunchResult.REQUEST_FAILED -> nextResult
                        isIdle() && coordinator.markPrompted() -> nextResult
                        else -> ReviewLaunchResult.SKIPPED
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Changes state on the main thread and lets one frame recompose with it. */
    private fun change(block: () -> Unit) {
        compose.runOnIdle {
            block()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    @Test
    fun positionalNamedAndTrailingResultCallbacksRemainUnambiguous() {
        val disabled = ReviewCoordinator.disabled()
        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalContext provides ContextWrapper(activity),
            ) {
                ReviewPromptEffect(disabled, true, 1500.milliseconds, false, { results += it })
                ReviewPromptEffect(disabled, onResult = { results += it })
                ReviewPromptEffect(disabled) { results += it }
            }
        }
        compose.waitForIdle()
        assertEquals(List(3) { ReviewLaunchResult.SKIPPED }, results)
    }

    @Test
    fun launchesOnceAfterTheSettleDelay() {
        show()
        advance(1_000)
        assertEquals(0, launches.size)
        advance(600)
        assertEquals(1, launches.size)
        assertSame(activity, launches.single())
        assertEquals(listOf(ReviewLaunchResult.LAUNCHED), results)
        advance(10_000)
        assertEquals(1, launches.size)
    }

    @Test
    fun aBusyScreenRestartsTheSettle() {
        show()
        advance(1_000)
        change { idle = false }
        advance(5_000)
        assertEquals(0, launches.size)
        change { idle = true }
        advance(1_000)
        assertEquals(0, launches.size, "the settle starts over when the screen is idle again")
        advance(600)
        assertEquals(1, launches.size)
    }

    @Test
    fun rechecksOnResume() {
        nextResult = ReviewLaunchResult.REQUEST_FAILED
        val failing = ReviewCoordinator(InMemoryReviewStore(), "1.0").apply { arm() }
        show(failing)
        advance(2_000)
        assertEquals(listOf(ReviewLaunchResult.REQUEST_FAILED), results)
        advance(10_000)
        assertEquals(1, launches.size, "a failed request is not retried on the same screen")

        change { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        advance(5_000)
        assertEquals(1, launches.size, "paused: no check")
        change { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        advance(1_000)
        assertEquals(1, launches.size, "a fresh settle after ON_RESUME")
        advance(600)
        assertEquals(2, launches.size, "ON_RESUME checks again")
        assertEquals(true, failing.isDue, "still due after two failed requests")
    }

    @Test
    fun pausingDuringTheSettleCancelsIt() {
        show()
        advance(1_000)
        change { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        advance(5_000)
        assertEquals(0, launches.size)
    }

    @Test
    fun aDisabledCoordinatorNeverLaunches() {
        show(ReviewCoordinator.disabled())
        advance(10_000)
        assertEquals(0, launches.size)
        assertEquals(listOf(ReviewLaunchResult.SKIPPED), results)
    }

    @Test
    fun aVisibleInterruptionHoldsTheLaunchUntilItsCooldownEnds() {
        var now = 0L
        val held = ReviewCoordinator(InMemoryReviewStore(), "1.0", cooldown = 5.seconds, clock = { now })
        held.arm()
        held.setInterruptionVisible(true, "sheet")
        show(held)
        advance(3_000)
        assertEquals(0, launches.size)
        held.setInterruptionVisible(false, "sheet")
        now += 4_000
        advance(1_000)
        assertEquals(0, launches.size, "within the cooldown")
        now += 2_000
        advance(5_000)
        assertEquals(1, launches.size)
    }
}
