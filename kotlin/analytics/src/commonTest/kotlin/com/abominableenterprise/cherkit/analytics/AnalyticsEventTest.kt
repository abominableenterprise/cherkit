package com.abominableenterprise.cherkit.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsEventTest {
    private enum class AppEvent(override val eventName: String) : AnalyticsEvent {
        APP_OPENED("app_opened"),
        ITEM_ADDED("item_added"),
    }

    @Test
    fun appEnumIsTrackedByEventName() {
        val backend = RecordingBackend()
        val analytics = Analytics<AppEvent>(backend, buildEnabled = true)
        analytics.consentGranted = true
        analytics.sessionActive = true

        assertTrue(analytics.track(AppEvent.ITEM_ADDED))
        assertEquals(listOf("start", "capture:item_added"), backend.calls)
    }

    @Test
    fun appsCanCheckTheirWholeAllowlist() {
        assertTrue(AppEvent.entries.all { AnalyticsEventName.isValid(it.eventName) })
    }

    @Test
    fun consentAndSessionDefaultToFalse() {
        val analytics = Analytics<AppEvent>(RecordingBackend(), buildEnabled = true)
        assertFalse(analytics.consentGranted)
        assertFalse(analytics.sessionActive)
        assertFalse(analytics.isGateOpen)
    }

    @Test
    fun throwingEventNameIsDroppedNotPropagated() {
        val event = object : AnalyticsEvent {
            override val eventName: String get() = throw IllegalStateException("boom")
        }
        val analytics = Analytics<AnalyticsEvent>(RecordingBackend(), buildEnabled = true)
        analytics.consentGranted = true
        analytics.sessionActive = true
        assertFalse(analytics.track(event))
    }
}
