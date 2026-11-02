package com.abominableenterprise.cherkit.telemetry

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TelemetryLifecycleTest {
    @Test
    fun defaultsAreEnabledAndConstructionIsLazy() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        assertTrue(telemetry.enabled)
        assertFalse(telemetry.restricted)
        assertFalse(telemetry.isDisabled)
        assertTrue(backend.calls.isEmpty())
        telemetry.restricted = true
        telemetry.restricted = false
        assertTrue(backend.calls.isEmpty())
        telemetry.breadcrumb("ready")
        assertEquals(listOf("start", "breadcrumb"), backend.calls)
    }

    @Test
    fun failedStartRollsBackAndNextOperationRetries() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        backend.failNext += "start"
        backend.failNext += "stop"
        telemetry.recordError("network", 1)
        assertEquals(listOf("start", "stop"), backend.calls)
        telemetry.recordError("network", 2)
        assertEquals(listOf("start", "stop", "start", "error"), backend.calls)
    }

    @Test
    fun failedSetterStartRetriesOnRepeatedEnable() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        backend.failNext += "start"
        telemetry.enabled = true
        telemetry.enabled = true
        telemetry.enabled = true
        assertEquals(listOf("start", "stop", "start"), backend.calls)
    }

    @Test
    fun failedStopDoesNotPreventRestart() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        telemetry.recordError("network", 1)
        backend.failNext += "stop"
        telemetry.enabled = false
        telemetry.enabled = false
        telemetry.enabled = true
        telemetry.recordError("network", 2)
        assertEquals(listOf("start", "error", "stop", "start", "error"), backend.calls)
    }

    @Test
    fun backendOperationExceptionsAreSwallowedAndDoNotPoisonLaterCalls() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        backend.failNext += setOf("error", "breadcrumb", "key", "startTrace")
        telemetry.recordError("network", 1)
        telemetry.breadcrumb("ready")
        telemetry.setKey("state", "ready")
        val inert = telemetry.startSpan("load")
        inert.setAttribute("state", "ready")
        inert.end()
        val active = telemetry.startSpan("load")
        backend.failNext += setOf("setAttribute", "stopTrace")
        active.setAttribute("state", "ready")
        active.end()
        active.end()
        telemetry.recordError("network", 2)
        assertEquals(1, backend.errors.size)
        assertEquals(listOf("start", "error", "breadcrumb", "key", "startTrace", "startTrace",
            "setAttribute", "stopTrace", "error"), backend.calls)
    }

    @Test
    fun backendThrowablesAreSwallowedIncludingDuringRollbackAndTraceEnd() {
        val backend = RecordingBackend()
        backend.failure = AssertionError("provider failure")
        val telemetry = Telemetry(backend, true)
        backend.failNext += setOf("start", "stop")
        telemetry.recordError("network", 1)
        backend.failNext += setOf("error", "breadcrumb", "key", "startTrace")
        telemetry.recordError("network", 1)
        telemetry.breadcrumb("ready")
        telemetry.setKey("state", "ready")
        telemetry.startSpan("load").end()
        val span = telemetry.startSpan("load")
        backend.failNext += setOf("setAttribute", "stopTrace", "stop")
        span.setAttribute("state", "ready")
        span.end()
        telemetry.enabled = false
        telemetry.enabled = true
        telemetry.recordError("network", 2)
        assertEquals(1, backend.errors.size)
        assertTrue(backend.liveTraces.isEmpty())
        assertTrue(backend.failNext.isEmpty())
    }

    @Test
    fun disabledInstancesAreFreshAndPermanentlyBuildDisabled() {
        val first = Telemetry.disabled
        val second = Telemetry.disabled
        assertNotSame(first, second)
        first.enabled = false
        first.restricted = true
        assertTrue(second.enabled)
        assertFalse(second.restricted)
        first.enabled = true
        assertFalse(first.buildEnabled)
        assertTrue(first.isDisabled)
        assertTrue(second.isDisabled)
        first.recordError("network", 1)
        first.breadcrumb("ready")
        first.setKey("state", "ready")
        first.startSpan("load").end()
    }

    @Test
    fun coreSinkDefaultsAreAvailableOnFacadeAndInterface() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val sink: TelemetrySink = telemetry
        telemetry.recordError("network", 1)
        telemetry.breadcrumb("ready")
        sink.recordError("network", 2)
        sink.breadcrumb("done")
        assertEquals(listOf("start", "error", "breadcrumb", "error", "breadcrumb"), backend.calls)
    }
}
