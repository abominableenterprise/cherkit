package com.abominableenterprise.cherkit.telemetry.firebase

import com.abominableenterprise.cherkit.telemetry.SpanStatus
import com.abominableenterprise.cherkit.telemetry.TelemetryError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebasePrivacyTest {
    @Test fun closedGatesExplicitlyDisableCollectionAndNeverRecord() {
        for ((build, enabled) in listOf(false to false, false to true, true to false)) {
            val sdk = RecordingFirebase()
            val telemetry = firebaseTelemetry(FirebaseBackend(sdk, true), build, enabled, false)
            telemetry.recordError(IllegalStateException("private message"))
            telemetry.breadcrumb("ready")
            telemetry.setKey("state", "ready")
            telemetry.startSpan("load").end()
            assertEquals(listOf(false), sdk.crashes)
            assertEquals(listOf(false), sdk.performance)
            assertTrue(sdk.errors.isEmpty() && sdk.logs.isEmpty() && sdk.keys.isEmpty() && sdk.traces.isEmpty())
        }
    }

    @Test fun restrictedSessionsAndTransitionsKeepOnlyErrors() {
        val sdk = RecordingFirebase()
        val telemetry = firebaseTelemetry(FirebaseBackend(sdk, true), true, true, true)
        assertFalse(sdk.performance.last())
        telemetry.breadcrumb("ready")
        telemetry.setKey("state", "ready")
        telemetry.startSpan("load").end()
        telemetry.recordError("storage", 3, mapOf("retry" to true))
        assertEquals(1, sdk.errors.size)
        assertEquals(mapOf("retry" to "true"), sdk.errors.single().attributes)
        assertTrue(sdk.logs.isEmpty() && sdk.keys.isEmpty() && sdk.traces.isEmpty())
        telemetry.restricted = false
        assertTrue(sdk.performance.last())
        telemetry.setKey("state", "ready")
        val span = telemetry.startSpan("load")
        telemetry.restricted = true
        assertEquals(1, sdk.traces.single().stops)
        assertFalse(sdk.performance.last())
        assertEquals("", sdk.keys["state"])
        span.setAttribute("state", "private")
        span.end()
        assertTrue(sdk.traces.single().attributes.isEmpty())
        telemetry.recordError("storage", 4)
        assertEquals(2, sdk.errors.size)
    }

    @Test fun errorsNeverReadOrSendOriginalMessageCauseOrStack() {
        val sdk = RecordingFirebase()
        val telemetry = firebaseTelemetry(FirebaseBackend(sdk), true, true, false)
        val privateError = object : Exception("private message", Exception("private cause")) {
            override val message: String get() = error("must never read message")
        }
        telemetry.recordError(privateError, mapOf("detail" to "private text", "retry" to true,
            "cherkit.error.type" to "spoofed"))
        telemetry.recordError("https://example.com?q=secret", 42)
        assertEquals("Throwable", sdk.errors.first().type)
        assertEquals(mapOf("retry" to "true"), sdk.errors.first().attributes)
        assertEquals(null, sdk.errors.last().domain)
        assertEquals(42, sdk.errors.last().code)
        assertFalse(sdk.errors.toString().contains("private"))
        assertFalse(sdk.performance.any { it })
    }

    @Test fun directBackendAlsoValidatesAndSuppressesRestrictedDiagnostics() {
        val sdk = RecordingFirebase()
        val backend = FirebaseBackend(sdk, true)
        backend.start()
        backend.breadcrumb("sync.ready", mapOf("source" to "manual", "url" to "https://example.com?q=secret"))
        assertEquals(listOf("sync.ready source=manual"), sdk.logs)
        backend.breadcrumb("private text", emptyMap())
        backend.setKey("bad", Any())
        backend.setRestricted(true)
        backend.breadcrumb("ready", emptyMap())
        backend.setKey("state", "ready")
        assertEquals("", backend.startTrace("load", emptyMap()))
        backend.recordError(TelemetryError("private text", "private text", 1), emptyMap())
        assertEquals(RecordingFirebase.Error("Error", null, 1, emptyMap()), sdk.errors.single())
        assertEquals(1, sdk.logs.size)
        assertTrue(sdk.keys.isEmpty())
    }

    @Test fun traceLimitsAreEnforcedAndExistingKeysCanBeUpdated() {
        val sdk = RecordingFirebase()
        val backend = FirebaseBackend(sdk, true)
        backend.start()
        val id = backend.startTrace("load", mapOf("a" to "ready", "b" to 1, "c" to true,
            "d" to 2.5, "e" to "ok", "extra" to "drop"))
        val trace = sdk.traces.single()
        assertEquals(5, trace.attributes.size)
        backend.setAttribute(id, "a", "done")
        assertEquals("done", trace.attributes["a"])
        backend.setAttribute(id, "sixth", "drop")
        backend.stopTrace(id, SpanStatus.ERROR)
        backend.stopTrace(id, SpanStatus.OK)
        assertEquals(1, trace.stops)
        val second = backend.startTrace("load", emptyMap())
        for (key in listOf("a".repeat(41), "with.dot", "with1", "firebase_key", "google_key", "ga_key")) {
            backend.setAttribute(second, key, "ready")
        }
        backend.setAttribute(second, "safe", "a".repeat(101))
        assertTrue(sdk.traces.last().attributes.isEmpty())
        backend.setAttribute(second, "a".repeat(40), "a".repeat(64))
        assertEquals(1, sdk.traces.last().attributes.size)
        backend.stop()
        assertFalse(sdk.crashes.last())
        assertFalse(sdk.performance.last())
        assertEquals(1, sdk.traces.last().stops)
    }

    @Test fun everySdkThrowableIsSwallowedAndCleanupContinues() {
        val sdk = RecordingFirebase()
        sdk.failures += setOf("crash", "perf")
        val backend = FirebaseBackend(sdk, true)
        backend.start()
        sdk.failures.clear()
        backend.start()
        sdk.failures += setOf("error", "log", "key", "trace")
        backend.recordError(TelemetryError("Error"), emptyMap())
        backend.breadcrumb("ready", emptyMap())
        backend.setKey("state", "ready")
        assertEquals("", backend.startTrace("load", emptyMap()))
        sdk.failures.clear()
        val id = backend.startTrace("load", emptyMap())
        sdk.failures += setOf("attribute", "stop")
        backend.setAttribute(id, "state", "ready")
        backend.stopTrace(id, SpanStatus.OK)
        sdk.failures.clear()
        backend.startTrace("load", emptyMap())
        sdk.failures += setOf("stop", "key", "perf", "crash")
        backend.stop()
        assertTrue(sdk.calls.containsAll(listOf("error", "log", "key", "trace", "attribute", "stop", "crash", "perf")))
    }
}
