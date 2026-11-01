package com.abominableenterprise.cherkit.update.compose

import android.content.Context
import android.os.Build

/**
 * Which app installed this build, for `requirePlayInstall`. The same check as review-compose's `PlayInstall`
 * (mirrored, so this module does not depend on review-compose).
 */
internal object PlayInstall {
    /** The Play Store's package name. Play testing tracks (internal, closed, open) install through it too. */
    const val PLAY_STORE_PACKAGE: String = "com.android.vending"

    /** `getInstallSourceInfo(...).installingPackageName` on API 30+, `getInstallerPackageName` before; `null` if unknown. */
    @Suppress("DEPRECATION")
    fun installerPackageName(context: Context): String? = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(context.packageName)
        }
    } catch (_: Exception) {
        null
    }

    /** True for production and Play testing tracks; false for sideloads, IDE/emulator installs and other stores. */
    fun isPlayInstall(context: Context): Boolean = installerPackageName(context) == PLAY_STORE_PACKAGE
}
