package com.abominableenterprise.cherkit.analytics

import com.posthog.PersonProfiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Builds the real SDK config (no context, no network) and checks every hardening flag. */
class PostHogHardenedConfigTest {
    @Test
    fun everyHardeningFlagIsApplied() {
        val config = postHogHardenedConfig("phc_test", "https://us.i.posthog.com")

        assertEquals("phc_test", config.apiKey)
        assertEquals("https://us.i.posthog.com", config.host)
        assertFalse(config.captureApplicationLifecycleEvents)
        assertFalse(config.captureDeepLinks)
        assertFalse(config.captureScreenViews)
        assertFalse(config.capturePushNotificationSubscriptions)
        assertFalse(config.capturePushNotificationOpened)
        assertFalse(config.preloadFeatureFlags)
        assertFalse(config.sendFeatureFlagEvent)
        assertFalse(config.setDefaultPersonProperties)
        assertEquals(PersonProfiles.NEVER, config.personProfiles)
        assertFalse(config.sessionReplay)
        assertFalse(config.surveys)
        assertFalse(config.errorTrackingConfig.autoCapture)
    }

    @Test
    fun theBackendHandsTheHardenedConfigToTheFactory() {
        val fakes = FakePostHogClientFactory()
        PostHogAnalyticsBackend("phc_test", "https://eu.i.posthog.com", fakes.factory).start()

        val config = fakes.configs.single()
        assertEquals("https://eu.i.posthog.com", config.host)
        assertFalse(config.captureScreenViews)
        assertFalse(config.sessionReplay)
        assertFalse(config.surveys)
        assertFalse(config.preloadFeatureFlags)
        assertEquals(PersonProfiles.NEVER, config.personProfiles)
        assertFalse(config.errorTrackingConfig.autoCapture)
    }
}
