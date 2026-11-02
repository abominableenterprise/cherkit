package com.abominableenterprise.cherkit.telemetry

import com.abominableenterprise.cherkit.core.AppVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TelemetryFixtureTest {
    @Test
    fun everyGateCase() {
        val cases = telemetryFixture<List<GateCase>>("gates.json")
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val backend = RecordingBackend()
            val telemetry = Telemetry(backend, case.buildEnabled)
            telemetry.enabled = case.enabled
            telemetry.restricted = case.restricted
            assertEquals(!(case.buildEnabled && case.enabled), telemetry.isDisabled, case.toString())
            telemetry.recordError("network", 1)
            assertEquals(case.errors, backend.errors.size, case.toString())
            telemetry.breadcrumb("ready")
            assertEquals(case.diagnostics, backend.names.size, case.toString())
            telemetry.setKey("state", "ready")
            assertEquals(case.diagnostics, backend.keys.size, case.toString())
            telemetry.startSpan("load").end()
            assertEquals(case.diagnostics, backend.endings.size, case.toString())
            assertEquals(case.diagnostics * 2, backend.names.size, case.toString())
        }
    }

    @Test
    fun everyValidationCase() {
        val cases = telemetryFixture<List<ValidationCase>>("validation.json")
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val value: Any = when (case.kind) {
                "string" -> case.value
                "bool" -> case.value.toBooleanStrict()
                "int" -> case.value.toInt()
                "double" -> case.value.toDouble()
                "version" -> requireNotNull(AppVersion.parse(case.value))
                "array" -> listOf("safe")
                "object" -> Any()
                "nan" -> Double.NaN
                else -> fail("unknown kind: ${case.kind}")
            }
            val label = case.toString()
            assertEquals(case.validName, TelemetryValidation.validName(case.name), label)
            assertEquals(case.validValue, TelemetryValidation.validValue(value), label)
            val backend = RecordingBackend()
            val telemetry = Telemetry(backend, true)
            val attributes = mapOf(case.name to value)
            val expected = if (case.validName && case.validValue) attributes else emptyMap()
            telemetry.recordError("network", 1, attributes)
            telemetry.breadcrumb("ready", attributes)
            val span = telemetry.startSpan("load", attributes)
            assertEquals(listOf(expected, expected, expected), backend.attributes, label)
            telemetry.setKey(case.name, value)
            span.setAttribute(case.name, value)
            val count = if (case.validName && case.validValue) 1 else 0
            assertEquals(count, backend.keys.size, label)
            assertEquals(count, backend.traceAttributes.size, label)
            span.end()
            telemetry.breadcrumb(case.name)
            telemetry.startSpan(case.name).end()
            assertEquals(if (case.validName) 4 else 2, backend.names.size, label)
        }
    }

    @Test
    fun everyLifecycleCaseInOrder() {
        val cases = telemetryFixture<List<LifecycleCase>>("lifecycle.json")
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val backend = RecordingBackend()
            val telemetry = Telemetry(backend, true)
            for (step in case.steps) {
                when (step) {
                    "error" -> telemetry.recordError("network", 1)
                    "disable" -> telemetry.enabled = false
                    "enable" -> telemetry.enabled = true
                    "restrict" -> telemetry.restricted = true
                    "unrestrict" -> telemetry.restricted = false
                    "breadcrumb" -> telemetry.breadcrumb("ready")
                    "span" -> telemetry.startSpan("load").end()
                    "key" -> telemetry.setKey("state", "ready")
                    else -> fail("unknown step $step in ${case.name}")
                }
            }
            assertEquals(case.expected, backend.calls, case.name)
        }
    }
}
