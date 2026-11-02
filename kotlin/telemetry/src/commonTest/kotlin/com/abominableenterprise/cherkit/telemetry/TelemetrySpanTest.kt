package com.abominableenterprise.cherkit.telemetry

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class TelemetrySpanTest {
    @Test
    fun manualSpansAreIndependentAndEndIsIdempotent() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val first = telemetry.startSpan("first")
        val second = telemetry.startSpan("second")
        first.setAttribute("state", "ready")
        first.end(SpanStatus.ERROR)
        first.end()
        first.setAttribute("state", "done")
        second.end()
        assertEquals(listOf("trace-1" to SpanStatus.ERROR, "trace-2" to SpanStatus.OK), backend.endings)
        assertEquals(listOf(Triple<String, String, Any>("trace-1", "state", "ready")), backend.traceAttributes)
        assertTrue(backend.liveTraces.isEmpty())
    }

    @Test
    fun restrictionStopsAllTracesAndOldSpansCannotRevive() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val first = telemetry.startSpan("first")
        val second = telemetry.startSpan("second")
        telemetry.restricted = true
        assertTrue(backend.liveTraces.isEmpty())
        assertEquals(2, backend.endings.size)
        assertFalse("stop" in backend.calls)
        first.setAttribute("state", "ready")
        second.end(SpanStatus.ERROR)
        val before = backend.calls.toList()
        telemetry.breadcrumb("ready")
        telemetry.setKey("state", "ready")
        val restricted = telemetry.startSpan("restricted")
        restricted.setAttribute("state", "ready")
        restricted.end()
        assertEquals(before, backend.calls)
        telemetry.restricted = false
        first.setAttribute("state", "ready")
        first.end()
        second.setAttribute("state", "ready")
        second.end()
        restricted.setAttribute("state", "ready")
        restricted.end()
        assertEquals(before, backend.calls)
        telemetry.startSpan("fresh").end()
        assertEquals(3, backend.endings.size)
    }

    @Test
    fun disableFinishesTracesBeforeStoppingBackendAndNeverRevivesOldSpans() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val old = telemetry.startSpan("load")
        telemetry.enabled = false
        assertTrue(backend.liveTraces.isEmpty())
        assertEquals(listOf("start", "startTrace", "stopTrace", "stop"), backend.calls)
        old.setAttribute("state", "ready")
        old.end()
        val disabled = telemetry.startSpan("load")
        telemetry.enabled = true
        val before = backend.calls.toList()
        old.setAttribute("state", "ready")
        old.end(SpanStatus.ERROR)
        disabled.setAttribute("state", "ready")
        disabled.end()
        assertEquals(before, backend.calls)
        telemetry.startSpan("fresh").end()
        assertEquals(2, backend.endings.size)
    }

    @Test
    fun unchangedGatesDoNotInvalidateLiveSpan() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val span = telemetry.startSpan("load")
        telemetry.enabled = true
        telemetry.restricted = false
        span.setAttribute("state", "ready")
        span.end()
        assertEquals(1, backend.traceAttributes.size)
        assertEquals(1, backend.endings.size)
    }

    @Test
    fun cleanupContinuesAfterTraceStopException() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val first = telemetry.startSpan("first")
        val second = telemetry.startSpan("second")
        backend.failNext += "stopTrace"
        telemetry.enabled = false
        assertTrue(backend.liveTraces.isEmpty())
        assertEquals(listOf("start", "startTrace", "startTrace", "stopTrace", "stopTrace", "stop"), backend.calls)
        telemetry.enabled = true
        val before = backend.calls.toList()
        first.end()
        second.setAttribute("state", "ready")
        second.end()
        assertEquals(before, backend.calls)
    }

    @Test
    fun suspendSpanReturnsResultAndClosesSuccessfully() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val result = runImmediate {
            telemetry.span("load", mapOf("state" to "ready")) { 42 }
        }
        assertEquals(42, result)
        assertEquals(listOf("trace-1" to SpanStatus.OK), backend.endings)
    }

    @Test
    fun suspendSpanRethrowsExactAppFailureAndEndsError() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val appError = object : Throwable("private app message") {}
        val caught = try {
            runImmediate { telemetry.span("load") { throw appError } }
            fail("expected application failure")
        } catch (error: Throwable) {
            error
        }
        assertSame(appError, caught)
        assertEquals(listOf("trace-1" to SpanStatus.ERROR), backend.endings)
        assertTrue(backend.errors.isEmpty())
    }

    @Test
    fun backendEndFailureCannotReplaceAppResultOrFailure() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        backend.failNext += "stopTrace"
        assertEquals(42, runImmediate { telemetry.span("load") { 42 } })
        val appError = IllegalArgumentException("private app message")
        backend.failNext += "stopTrace"
        val caught = try {
            runImmediate { telemetry.span("load") { throw appError } }
            fail("expected application failure")
        } catch (error: Throwable) {
            error
        }
        assertSame(appError, caught)
        assertTrue(backend.liveTraces.isEmpty())
    }

    @Test
    fun disabledSuspendSpanStillExecutesBlock() {
        var ran = false
        assertEquals(7, runImmediate {
            Telemetry.disabled.span("load") {
                ran = true
                7
            }
        })
        assertTrue(ran)
    }

    @Test
    fun actualSuspensionAcrossGateChangeCannotReviveSpan() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        var continuation: Continuation<Unit>? = null
        var result: Result<Int>? = null
        val block: suspend () -> Int = {
            telemetry.span("load") {
                suspendCoroutine<Unit> { continuation = it }
                42
            }
        }
        block.startCoroutine(object : Continuation<Int> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<Int>) { result = value }
        })
        assertEquals(null, result)
        assertEquals(1, backend.liveTraces.size)
        telemetry.restricted = true
        assertTrue(backend.liveTraces.isEmpty())
        telemetry.restricted = false
        val before = backend.calls.toList()
        requireNotNull(continuation).resume(Unit)
        assertEquals(42, requireNotNull(result).getOrThrow())
        assertEquals(before, backend.calls)
    }
}

/** Only for blocks that complete immediately; the suspension test drives its continuation explicitly. */
private fun <T> runImmediate(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) { result = value }
    })
    return checkNotNull(result) { "test block unexpectedly suspended" }.getOrThrow()
}
