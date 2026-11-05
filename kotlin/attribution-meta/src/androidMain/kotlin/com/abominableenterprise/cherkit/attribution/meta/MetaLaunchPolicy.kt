package com.abominableenterprise.cherkit.attribution.meta

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.abominableenterprise.cherkit.attribution.revenuecat.Audience

internal object MetaLaunchPolicy {
    private const val PREFIX = "com.facebook.sdk."
    private val disabledFlags = listOf(
        "AutoInitEnabled", "AutoLogAppEventsEnabled", "AdvertiserIDCollectionEnabled",
        "AutoLogMetaDataEnabled", "AutoLogProductImprovementEnabled",
    )

    @Suppress("DEPRECATION")
    internal fun hasConfiguration(application: Application, metadata: Bundle?): Boolean {
        fun configured(key: String): Boolean {
            val value = metadata?.get(PREFIX + key)
            val text = when (value) {
                is String -> value
                is Int -> runCatching { application.getString(value) }.getOrNull()
                else -> null
            }
            return !text.isNullOrBlank()
        }
        return configured("ApplicationId") && configured("ClientToken") &&
            disabledFlags.all { metadata?.get(PREFIX + it) == false }
    }

    @Suppress("DEPRECATION")
    fun allowed(application: Application, audience: Audience): Boolean {
        val info = application.packageManager.getApplicationInfo(application.packageName, PackageManager.GET_META_DATA)
        val emulator = Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator", ignoreCase = true) || Build.MODEL.contains("Android SDK") ||
            Build.MODEL.contains("sdk_gphone") || Build.HARDWARE in setOf("goldfish", "ranchu") ||
            Build.PRODUCT.contains("sdk") || Build.MANUFACTURER.contains("Genymotion")
        val testHost = (info.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0 ||
            listOf("org.junit.Test", "org.robolectric.Robolectric", "androidx.test.platform.app.InstrumentationRegistry")
                .any { name -> runCatching { Class.forName(name, false, application.classLoader) }.isSuccess }
        return shouldEnableMetaAttribution(
            "android", audience, (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            testHost, emulator, hasConfiguration(application, info.metaData),
        )
    }
}
