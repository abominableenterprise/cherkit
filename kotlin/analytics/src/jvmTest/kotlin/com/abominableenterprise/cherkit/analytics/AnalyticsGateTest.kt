package com.abominableenterprise.cherkit.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsGateTest {
    @Test
    fun everyGateFixtureCase() {
        val fixture = analyticsFixture<GateFixture>("gate.json")
        assertTrue(fixture.cases.size >= 8)
        for (case in fixture.cases) {
            val backend = RecordingBackend()
            val analytics = Analytics<NamedEvent>(backend, case.buildEnabled)
            analytics.consentGranted = case.consentGranted
            analytics.sessionActive = case.sessionActive

            val sent = analytics.track(NamedEvent(case.event))

            assertEquals(case.sent, sent, case.description)
            assertEquals(case.sent, "capture:${case.event}" in backend.calls, case.description)
            assertEquals(case.sent, AnalyticsEventName.isValid(case.event) && analytics.isGateOpen, case.description)
        }
    }
}
