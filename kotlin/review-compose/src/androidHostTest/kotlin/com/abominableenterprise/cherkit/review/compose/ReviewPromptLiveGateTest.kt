package com.abominableenterprise.cherkit.review.compose

import android.app.Activity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abominableenterprise.cherkit.review.InMemoryReviewStore
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Request completions deliberately run before the next Compose frame. */
@RunWith(RobolectricTestRunner::class)
class ReviewPromptLiveGateTest {
    @get:Rule
    val compose = createComposeRule()

    private class Owner(state: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = state }
        override val lifecycle: Lifecycle get() = registry
    }

    private val controllerA = Robolectric.buildActivity(Activity::class.java).setup()
    private val activityA = controllerA.get()
    private val activityB = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val ownerA = Owner(Lifecycle.State.RESUMED)
    private val ownerB = Owner(Lifecycle.State.STARTED)
    private val store = InMemoryReviewStore()
    private val coordinator = ReviewCoordinator(store, "1.0").apply { arm() }
    private val requestA = CompletableDeferred<Unit>()
    private val attempts = mutableListOf<Activity>()
    private val launches = mutableListOf<Activity>()
    private val results = mutableListOf<Pair<Activity, ReviewLaunchResult>>()
    // Plain model state, not Compose state: no recomposition can make this test pass accidentally.
    private var idleA = true
    private var rootEligible = true

    private fun show() {
        coordinator.isEligible = { rootEligible }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            for ((activity, owner) in listOf(activityA to ownerA, activityB to ownerB)) {
                CompositionLocalProvider(LocalContext provides activity, LocalLifecycleOwner provides owner) {
                    ReviewPromptEffect(
                        coordinator,
                        isIdle = { activity !== activityA || idleA },
                        settleDelay = 100.milliseconds,
                        onResult = {},
                    ) { boundActivity, idle ->
                        val flow = object : ReviewFlow<Unit> {
                            override suspend fun request() {
                                attempts += boundActivity
                                if (boundActivity === activityA) requestA.await()
                            }
                            override suspend fun launch(info: Unit) { launches += boundActivity }
                        }
                        ReviewLauncher.launch(coordinator, flow, isIdle = idle).also {
                            results += boundActivity to it
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(150)
        compose.waitForIdle()
        assertEquals(listOf(activityA), attempts)
    }

    private fun releaseWithoutFrame(change: () -> Unit) {
        compose.runOnIdle {
            change()
            requestA.complete(Unit)
        }
        compose.waitForIdle()
        assertEquals(listOf(activityA to ReviewLaunchResult.SKIPPED), results)
        assertTrue(launches.isEmpty())
        assertTrue(coordinator.isDue)
        assertNull(store.load().lastPromptedVersion)
    }

    @Test
    fun liveIdleChangeDuringRequestSkipsBeforeRecomposition() {
        show()
        releaseWithoutFrame { idleA = false }
    }

    @Test
    fun stoppedRootCannotConsumePromptAuthorizedByAnotherResumedRoot() {
        show()
        releaseWithoutFrame {
            ownerA.registry.currentState = Lifecycle.State.CREATED // stopped, with no frame
            rootEligible = false
            ownerB.registry.currentState = Lifecycle.State.RESUMED
            rootEligible = true // shared coordinator now reflects B, not A
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertEquals(listOf(activityB), launches)
        assertEquals(activityB to ReviewLaunchResult.LAUNCHED, results.last())
        assertEquals("1.0", store.load().lastPromptedVersion)
    }

    @Test
    fun finishingBoundActivitySkipsEvenWithResumedLifecycle() {
        show()
        releaseWithoutFrame { activityA.finish() }
    }

    @Test
    fun destroyedBoundActivitySkipsEvenWithResumedLifecycle() {
        show()
        releaseWithoutFrame { controllerA.pause().stop().destroy() }
    }
}
