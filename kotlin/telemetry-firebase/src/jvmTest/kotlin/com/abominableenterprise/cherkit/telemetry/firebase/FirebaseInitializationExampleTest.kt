package com.abominableenterprise.cherkit.telemetry.firebase

import com.abominableenterprise.cherkit.telemetry.Telemetry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FirebaseInitializationExampleTest {
    @Test fun coreReadmeAppliesRestrictionBeforeEnablingPerformance() {
        val readme = File(System.getProperty("cherkit.fixtures"), "../kotlin/telemetry/README.md")
        val assignments = readme.readLines().filter {
            it.startsWith("telemetry.enabled =") || it.startsWith("telemetry.restricted =")
        }
        assertEquals(2, assignments.size, "Keep this test aligned with the core initialization example")
        val sdk = RecordingFirebase()
        val telemetry = Telemetry(FirebaseBackend(sdk, performanceEnabled = true), buildEnabled = true)
        // Replay the documented order with diagnostics enabled in a restricted session.
        assignments.forEach { line ->
            when (line.substringBefore(" =")) {
                "telemetry.restricted" -> telemetry.restricted = true
                "telemetry.enabled" -> telemetry.enabled = true
            }
        }
        telemetry.recordError("storage", 3)
        assertEquals(listOf(false, true), sdk.crashes)
        assertEquals(1, sdk.errors.size)
        assertFalse(sdk.performance.any { it }, "Restricted startup must never briefly enable Performance")
    }
}
