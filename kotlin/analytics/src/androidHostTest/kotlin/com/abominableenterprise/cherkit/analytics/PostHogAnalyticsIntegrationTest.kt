package com.abominableenterprise.cherkit.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [PostHogAnalyticsBackend] driven by the real [Analytics] gate, with fake clients. */
class PostHogAnalyticsIntegrationTest {
    private enum class Event(override val eventName: String) : AnalyticsEvent {
        APP_OPENED("app_opened"),
    }

    private val fakes = FakePostHogClientFactory()

    private fun analytics(apiKey: String) =
        Analytics<Event>(PostHogAnalyticsBackend(apiKey, PostHogAnalyticsBackend.DEFAULT_HOST, fakes.factory), true)

    @Test
    fun gateDrivesTheClientLifecycle() {
        val analytics = analytics("phc_test")
        analytics.consentGranted = true
        assertTrue(fakes.clients.isEmpty(), "no client before the gate opens")

        analytics.sessionActive = true
        assertTrue(analytics.track(Event.APP_OPENED))
        analytics.consentGranted = false
        assertFalse(analytics.track(Event.APP_OPENED))
        analytics.consentGranted = true
        assertTrue(analytics.track(Event.APP_OPENED))

        assertEquals(listOf("optIn", "capture", "optOut", "close"), fakes.clients[0].methods)
        assertEquals(listOf("optIn", "capture"), fakes.clients[1].methods)
    }

    @Test
    fun blankKeyFailsSafelyAndTheGateStaysUsable() {
        val analytics = analytics(" ")
        analytics.consentGranted = true
        analytics.sessionActive = true

        assertTrue(analytics.isGateOpen)
        assertFalse(analytics.track(Event.APP_OPENED))
        analytics.consentGranted = false
        assertFalse(analytics.isGateOpen)
        analytics.consentGranted = true
        assertFalse(analytics.track(Event.APP_OPENED))
        assertTrue(fakes.clients.isEmpty(), "a blank key never creates an SDK client")
    }

    @Test
    fun failedOptInIsRolledBackAndTheNextStartUsesAFreshClient() {
        // Only the first client fails in optIn; Analytics then calls stop(), which opts out and closes it.
        val failFirst = PostHogClientFactory { config ->
            fakes.factory.create(config).also { if (fakes.clients.size == 1) fakes.clients[0].failNext += "optIn" }
        }
        val analytics = Analytics<Event>(PostHogAnalyticsBackend("phc_test", "https://h", failFirst), true)
        analytics.consentGranted = true
        analytics.sessionActive = true
        assertEquals(listOf("optIn", "optOut", "close"), fakes.clients[0].methods)

        // The gate is still open, so track retries start() with a new client.
        assertTrue(analytics.track(Event.APP_OPENED))
        assertEquals(listOf("optIn", "capture"), fakes.clients[1].methods)
    }
}
