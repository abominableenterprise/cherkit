package com.abominableenterprise.cherkit.update.compose

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [PlayInstall] against Robolectric's PackageManager, on API 30+ and before. */
@RunWith(RobolectricTestRunner::class)
class PlayInstallTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun installedBy(installer: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, null, installer)
        }
        @Suppress("DEPRECATION")
        context.packageManager.setInstallerPackageName(context.packageName, installer)
    }

    @Test
    fun playAndItsTestingTracksPass() {
        // Internal, closed and open testing builds are installed by the Play Store app itself.
        installedBy("com.android.vending")
        assertTrue(PlayInstall.isPlayInstall(context))
    }

    @Test
    fun sideloadsAndOtherStoresDoNot() {
        installedBy(null)
        assertFalse(PlayInstall.isPlayInstall(context))
        installedBy("com.android.packageinstaller")
        assertFalse(PlayInstall.isPlayInstall(context))
        installedBy("com.amazon.venezia")
        assertFalse(PlayInstall.isPlayInstall(context))
    }

    @Test
    @Config(sdk = [28])
    fun beforeApi30TheLegacyInstallerNameIsUsed() {
        installedBy("com.android.vending")
        assertTrue(PlayInstall.isPlayInstall(context))
    }
}
