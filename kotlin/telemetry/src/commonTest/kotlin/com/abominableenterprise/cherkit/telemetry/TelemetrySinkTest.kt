package com.abominableenterprise.cherkit.telemetry

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetrySinkTest {
    @Test
    fun sinkRoutesThroughNormalValidationAndRestrictionGates() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        val sink: TelemetrySink = telemetry
        val attributes = mapOf<String, Any>("kind" to "soft", "message" to "private message", "url" to "https://invalid.test")
        sink.recordError("cherkit.sunset", 1, attributes)
        sink.breadcrumb("cherkit.sunset.decision", attributes)
        assertEquals(listOf(TelemetryError("Error", "cherkit.sunset", 1)), backend.errors)
        assertEquals(listOf(mapOf<String, Any>("kind" to "soft"), mapOf<String, Any>("kind" to "soft")), backend.attributes)
        telemetry.restricted = true
        sink.recordError("cherkit.whatsnew", 2)
        sink.breadcrumb("cherkit.whatsnew.shown")
        assertEquals(2, backend.errors.size)
        assertEquals(listOf("cherkit.sunset.decision"), backend.names)
        telemetry.enabled = false
        sink.recordError("cherkit.review", 1)
        sink.breadcrumb("cherkit.review.prompted")
        assertEquals(2, backend.errors.size)
        assertEquals(1, backend.names.size)
    }

    @Test
    fun buildDisabledSinkNeverStartsBackend() {
        val backend = RecordingBackend()
        val sink: TelemetrySink = Telemetry(backend, false)
        sink.recordError("cherkit.update", 1)
        sink.breadcrumb("cherkit.update.available")
        assertTrue(backend.calls.isEmpty())
    }
}
