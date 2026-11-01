package com.abominableenterprise.cherkit.review.compose

import android.app.Activity
import android.os.Build
import com.abominableenterprise.cherkit.review.InMemoryReviewStore
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.testing.FakeReviewManager
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PlayReview.launch] and [PlayInstall] against Robolectric's PackageManager and fake Play managers. */
@RunWith(RobolectricTestRunner::class)
class PlayReviewTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val store = InMemoryReviewStore()
    private val review = ReviewCoordinator(store, appVersion = "1.0").apply { arm() }

    private fun installedBy(installer: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            shadowOf(activity.packageManager).setInstallSourceInfo(activity.packageName, null, installer)
        }
        @Suppress("DEPRECATION")
        activity.packageManager.setInstallerPackageName(activity.packageName, installer)
    }

    /** A Play API whose request fails, like a device without the Play Store or offline. */
    private class FailingManager : ReviewManager {
        var launches = 0
        override fun requestReviewFlow(): Task<ReviewInfo> = Tasks.forException(IllegalStateException("no Play"))
        override fun launchReviewFlow(activity: Activity, info: ReviewInfo): Task<Void> {
            launches++
            return Tasks.forResult(null)
        }
    }

    @Test
    fun aPlayInstallLaunchesAndCountsAsPrompted() = runTest {
        installedBy(PlayInstall.PLAY_STORE_PACKAGE)
        val result = PlayReview.launch(activity, review, manager = { FakeReviewManager(activity) })
        assertEquals(ReviewLaunchResult.LAUNCHED, result)
        assertFalse(review.isDue)
        assertEquals("1.0", store.load().lastPromptedVersion)
    }

    @Test
    fun playTestingTracksPass() = runTest {
        // Internal, closed and open testing builds are all installed by the Play Store app itself.
        installedBy("com.android.vending")
        assertTrue(PlayInstall.isPlayInstall(activity))
        val result = PlayReview.launch(activity, review, manager = { FakeReviewManager(activity) })
        assertEquals(ReviewLaunchResult.LAUNCHED, result)
    }

    @Test
    fun aSideloadedBuildSkipsWithoutConsumingAnything() = runTest {
        for (installer in listOf(null, "com.google.android.packageinstaller", "com.amazon.venezia")) {
            installedBy(installer)
            assertFalse(PlayInstall.isPlayInstall(activity), "installer $installer")
            var created = 0
            val result = PlayReview.launch(activity, review, manager = { created++; FakeReviewManager(activity) })
            assertEquals(ReviewLaunchResult.NOT_PLAY_INSTALL, result)
            assertEquals(0, created, "Play is never asked")
            assertTrue(review.isDue)
            assertNull(store.load().lastPromptedVersion)
        }
    }

    @Test
    fun requirePlayInstallFalseSkipsTheInstallerCheck() = runTest {
        installedBy(null)
        val result = PlayReview.launch(
            activity, review, requirePlayInstall = false, manager = { FakeReviewManager(activity) },
        )
        assertEquals(ReviewLaunchResult.LAUNCHED, result)
    }

    @Test
    fun aFailedRequestKeepsThePromptDue() = runTest {
        installedBy(PlayInstall.PLAY_STORE_PACKAGE)
        val failing = FailingManager()
        assertEquals(ReviewLaunchResult.REQUEST_FAILED, PlayReview.launch(activity, review, manager = { failing }))
        assertEquals(0, failing.launches)
        assertTrue(review.isDue)
        assertNull(store.load().lastPromptedVersion)
    }

    @Test
    fun aManagerThatCannotBeCreatedFailsQuietly() = runTest {
        installedBy(PlayInstall.PLAY_STORE_PACKAGE)
        val result = PlayReview.launch(activity, review, manager = { throw IllegalStateException("no Play Services") })
        assertEquals(ReviewLaunchResult.REQUEST_FAILED, result)
        assertTrue(review.isDue)
    }

    @Test
    fun notDueAsksNothing() = runTest {
        installedBy(PlayInstall.PLAY_STORE_PACKAGE)
        var created = 0
        val idle = PlayReview.launch(
            activity, review, isIdle = { false }, manager = { created++; FakeReviewManager(activity) },
        )
        assertEquals(ReviewLaunchResult.SKIPPED, idle)
        assertEquals(0, created)
        assertTrue(review.isDue)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun beforeApi30TheInstallerPackageNameIsUsed() {
        installedBy(PlayInstall.PLAY_STORE_PACKAGE)
        assertEquals(PlayInstall.PLAY_STORE_PACKAGE, PlayInstall.installerPackageName(activity))
        installedBy(null)
        assertFalse(PlayInstall.isPlayInstall(activity))
    }
}
