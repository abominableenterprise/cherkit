package com.abominableenterprise.cherkit.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class AnalyticsLifecycleTest {
    @Test
    fun everyLifecycleFixtureCase() {
        val fixture = analyticsFixture<LifecycleFixture>("lifecycle.json")
        assertTrue(fixture.cases.isNotEmpty())
        for (case in fixture.cases) {
            val backend = RecordingBackend()
            val analytics = Analytics<NamedEvent>(backend, case.buildEnabled)
            case.operations.forEachIndexed { index, operation ->
                apply(operation, analytics, backend, "${case.description} [op $index: ${operation.op}]")
            }
            assertEquals(case.calls, backend.calls, case.description)
        }
    }

    private fun apply(
        operation: LifecycleFixture.Operation,
        analytics: Analytics<NamedEvent>,
        backend: RecordingBackend,
        label: String,
    ) {
        when (operation.op) {
            "setConsent" -> analytics.consentGranted = requireNotNull(operation.value) { label }
            "setSession" -> analytics.sessionActive = requireNotNull(operation.value) { label }
            "track" -> {
                val sent = analytics.track(NamedEvent(requireNotNull(operation.event) { label }))
                assertEquals(requireNotNull(operation.sent) { label }, sent, label)
            }
            "failNext" -> backend.failNext += requireNotNull(operation.call) { label }
            else -> fail("unknown operation: $label")
        }
    }
}
