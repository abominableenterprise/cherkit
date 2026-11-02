package com.abominableenterprise.cherkit.telemetry.firebase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.abominableenterprise.cherkit.telemetry.Telemetry

/** Android factory. App-owned test/emulator flags can close the gate on unrecognized test hosts. */
public object AndroidTelemetry {
    public fun firebase(
        context: Context,
        enabled: Boolean = true,
        restricted: Boolean = false,
        isRunningTests: Boolean = detectedTests(),
        isEmulator: Boolean = detectedEmulator(),
        performanceEnabled: Boolean = false,
    ): Telemetry {
        val buildEnabled = try {
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 &&
                !isRunningTests && !isEmulator
        } catch (_: Throwable) { false }
        return firebaseTelemetry(FirebaseBackend(AndroidFirebaseClient(), performanceEnabled),
            buildEnabled, enabled, restricted)
    }
}

private fun detectedTests(): Boolean = listOf(
    "org.junit.Test", "org.junit.jupiter.api.Test", "androidx.test.platform.app.InstrumentationRegistry",
    "androidx.test.espresso.Espresso", "org.robolectric.Robolectric",
).any { name ->
    try { Class.forName(name, false, AndroidTelemetry::class.java.classLoader); true }
    catch (_: Throwable) { false }
}

private fun detectedEmulator(): Boolean = try {
    Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
        Build.MODEL.contains("Emulator", true) || Build.MODEL.contains("Android SDK", true) ||
        Build.HARDWARE in listOf("goldfish", "ranchu") || Build.PRODUCT.contains("sdk", true)
} catch (_: Throwable) { true }
