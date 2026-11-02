package com.abominableenterprise.cherkit.telemetry.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SanitizedExceptionTest {
    @Test fun syntheticExceptionHasNoMessageCauseStackOrSuppressedErrors() {
        val error = SanitizedTelemetryException()
        assertNull(error.message)
        assertNull(error.localizedMessage)
        assertNull(error.cause)
        assertTrue(error.stackTrace.isEmpty())
        assertTrue(error.suppressed.isEmpty())
        error.addSuppressed(Exception("private message"))
        error.stackTrace = arrayOf(StackTraceElement("Private", "secret", "private.kt", 1))
        assertTrue(error.suppressed.isEmpty())
        assertTrue(error.stackTrace.isEmpty())
        assertEquals(SanitizedTelemetryException::class.java.name, error.toString())
    }
}
