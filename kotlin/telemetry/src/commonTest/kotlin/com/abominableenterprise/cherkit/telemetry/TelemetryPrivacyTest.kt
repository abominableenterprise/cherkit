package com.abominableenterprise.cherkit.telemetry

import com.abominableenterprise.cherkit.core.AppVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryPrivacyTest {
    private class PrivateFailure : Exception() {
        override val message: String
            get() = error("The facade must never read the message")
    }

    @Test
    fun throwableIsReducedToTypeOnly() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        telemetry.recordError(PrivateFailure(), mapOf("state" to "retry", "detail" to "private text"))
        assertEquals(listOf(TelemetryError("PrivateFailure")), backend.errors)
        assertEquals(listOf(mapOf<String, Any>("state" to "retry")), backend.attributes)
    }

    @Test
    fun domainAndCodeAreExplicitErrorIdentity() {
        val backend = RecordingBackend()
        Telemetry(backend, true).recordError("network", 42)
        assertEquals(listOf(TelemetryError(type = "Error", domain = "network", code = 42)), backend.errors)
        assertEquals(TelemetryError("type", null, null), TelemetryError("type"))
    }

    @Test
    fun allowlistRejectsOtherNumericTypesNonfiniteCollectionsObjectsAndNull() {
        val version = requireNotNull(AppVersion.parse("1.2.3"))
        val safe = mapOf<String, Any>("bool" to true, "int" to 42, "double" to 1.5,
            "state" to "ready-1.0", "version" to version)
        val unsafe = mapOf<String, Any>(
            "long" to 42L,
            "float" to 1.5f,
            "nan" to Double.NaN,
            "positive_infinity" to Double.POSITIVE_INFINITY,
            "negative_infinity" to Double.NEGATIVE_INFINITY,
            "array" to arrayOf("safe"),
            "list" to listOf("safe"),
            "map" to mapOf("safe" to true),
            "object" to Any(),
            "char" to 'a',
            "Bad" to "ready",
            "a-b" to "ready",
        )
        assertFalse(TelemetryValidation.validValue(null))
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        telemetry.recordError("network", 1, safe + unsafe)
        telemetry.breadcrumb("ready", safe + unsafe)
        val span = telemetry.startSpan("load", safe + unsafe)
        for ((key, value) in safe + unsafe) {
            telemetry.setKey(key, value)
            span.setAttribute(key, value)
        }
        span.end()
        assertEquals(listOf(safe, safe, safe), backend.attributes)
        assertEquals(safe, backend.keys.toMap())
        assertEquals(safe, backend.traceAttributes.associate { it.second to it.third })
        assertTrue(backend.attributes.first()["version"] is AppVersion)
    }

    @Test
    fun namesAndStringsRequireFullAsciiMatch() {
        for (name in listOf("a\n", "a\r", "a\r\n", "é", "aé", "a\u0000", "a b", "a-", "A", "1a")) {
            assertFalse(TelemetryValidation.validName(name), name)
        }
        for (value in listOf("a\n", "a\r", "é", "a\u0000", "a b", "A", "", "a".repeat(65))) {
            assertFalse(TelemetryValidation.validValue(value), value)
        }
        for (name in listOf("a", "a_0.z", "a".repeat(64))) {
            assertTrue(TelemetryValidation.validName(name), name)
        }
        for (value in listOf("0", "-", "a_0.z-", "a".repeat(64))) {
            assertTrue(TelemetryValidation.validValue(value), value)
        }
    }

    @Test
    fun invalidOperationsDoNotStartBackend() {
        val backend = RecordingBackend()
        val telemetry = Telemetry(backend, true)
        telemetry.breadcrumb("Bad")
        telemetry.startSpan("Bad").end()
        telemetry.setKey("Bad", "safe")
        telemetry.setKey("safe", "private text")
        assertTrue(backend.calls.isEmpty())
    }
}
