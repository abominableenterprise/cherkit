package com.abominableenterprise.cherkit.review.compose

import android.content.Context
import android.os.Build

/** Which app installed this build, for `requirePlayInstall`. */
public object PlayInstall {
    /** The Play Store's package name. Play testing tracks (internal, closed, open) install through it too. */
    public const val PLAY_STORE_PACKAGE: String = "com.android.vending"

    /**
     * The package that installed this app: `getInstallSourceInfo(...).installingPackageName` on API 30+,
     * `getInstallerPackageName` before. `null` when unknown (sideloaded with adb or a file manager, an emulator
     * install from the IDE) or when the PackageManager fails.
     */
    @Suppress("DEPRECATION")
    public fun installerPackageName(context: Context): String? = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(context.packageName)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Whether the Play Store installed this app. True for production **and** for Play testing tracks (internal,
     * closed and open testing all install through [PLAY_STORE_PACKAGE]); false for sideloaded builds, emulator
     * installs from the IDE and other stores, where the Play review UI never shows.
     */
    public fun isPlayInstall(context: Context): Boolean = installerPackageName(context) == PLAY_STORE_PACKAGE
}
