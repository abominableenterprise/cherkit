package com.abominableenterprise.cherkit.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryRestrictionHookTest {
    @Test
    fun currentRestrictionIsAppliedBeforeEveryStart() {
        for (restricted in listOf(false, true)) {
            val backend = HookBackend()
            val telemetry = Telemetry(backend, true)
            telemetry.restricted = restricted
            assertTrue(backend.calls.isEmpty())
            telemetry.recordError("network", 1)
            telemetry.enabled = false
            telemetry.restricted = !restricted
            telemetry.enabled = true
            assertEquals(listOf("restricted:$restricted", "start", "error", "stop",
                "restricted:${!restricted}", "start"), backend.calls)
        }
    }

    @Test
    fun restrictedSetterNeverStartsInactiveBackend() {
        for (buildEnabled in listOf(false, true)) {
            val backend = HookBackend()
            val telemetry = Telemetry(backend, buildEnabled)
            telemetry.restricted = true
            telemetry.restricted = true
            telemetry.restricted = false
            telemetry.enabled = false
            telemetry.restricted = true
            telemetry.restricted = false
            assertTrue(backend.calls.isEmpty())
        }
    }

    @Test
    fun activeTransitionsFinishSpansBeforeHookAndUnchangedAssignmentsDoNothing() {
        val backend = HookBackend()
        val telemetry = Telemetry(backend, true)
        val span = telemetry.startSpan("load")
        telemetry.restricted = false
        span.setAttribute("state", "ready")
        telemetry.restricted = true
        telemetry.restricted = true
        span.end()
        telemetry.breadcrumb("blocked")
        telemetry.setKey("state", "blocked")
        telemetry.startSpan("blocked").end()
        telemetry.recordError("network", 1)
        telemetry.restricted = false
        telemetry.restricted = false
        span.setAttribute("state", "stale")
        span.end()
        telemetry.breadcrumb("ready")
        assertEquals(listOf("restricted:false", "start", "startTrace", "setAttribute", "stopTrace",
            "restricted:true", "error", "restricted:false", "breadcrumb"), backend.calls)
    }

    @Test
    fun failedRestrictionSetupPreventsStartAndRetriesEvenIfRollbackThrows() {
        for (restricted in listOf(false, true)) {
            val backend = HookBackend()
            val telemetry = Telemetry(backend, true)
            telemetry.restricted = restricted
            backend.failNextRestriction = true
            backend.recording.failure = AssertionError("stop failed")
            backend.recording.failNext += "stop"
            telemetry.recordError("network", 1)
            assertEquals(listOf("restricted:$restricted", "stop"), backend.calls)
            telemetry.recordError("network", 2)
            assertEquals(listOf("restricted:$restricted", "stop", "restricted:$restricted", "start", "error"),
                backend.calls)
        }
    }

    @Test
    fun failedActiveTransitionStopsBackendAndRequiresSetupBeforeRestart() {
        for (restricted in listOf(false, true)) {
            val backend = HookBackend()
            val telemetry = Telemetry(backend, true)
            telemetry.restricted = !restricted
            telemetry.recordError("network", 1)
            backend.calls.clear()
            backend.failNextRestriction = true
            telemetry.restricted = restricted
            assertEquals(listOf("restricted:$restricted", "stop"), backend.calls)
            telemetry.restricted = restricted
            assertEquals(listOf("restricted:$restricted", "stop"), backend.calls)
            backend.failNextRestriction = true
            telemetry.recordError("network", 2)
            assertEquals(listOf("restricted:$restricted", "stop", "restricted:$restricted", "stop"), backend.calls)
            telemetry.recordError("network", 3)
            assertEquals(listOf("restricted:$restricted", "stop", "restricted:$restricted", "stop",
                "restricted:$restricted", "start", "error"), backend.calls)
        }
    }

    @Test
    fun failedStartReappliesRestrictionBeforeRetry() {
        val backend = HookBackend()
        val telemetry = Telemetry(backend, true)
        telemetry.restricted = true
        backend.recording.failNext += "start"
        telemetry.recordError("network", 1)
        telemetry.recordError("network", 2)
        assertEquals(listOf("restricted:true", "start", "stop", "restricted:true", "start", "error"),
            backend.calls)
    }

    private class HookBackend(val recording: RecordingBackend = RecordingBackend()) : TelemetryBackend by recording {
        val calls: MutableList<String> get() = recording.calls
        var failNextRestriction = false

        override fun setRestricted(restricted: Boolean) {
            calls += "restricted:$restricted"
            if (failNextRestriction) {
                failNextRestriction = false
                throw AssertionError("restriction setup failed")
            }
        }
    }
}
