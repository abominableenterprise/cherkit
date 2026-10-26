package com.abominableenterprise.cherkit.analytics

import com.posthog.PersonProfiles
import com.posthog.android.PostHogAndroidConfig

/**
 * The hardened PostHog config the apps use: no autocapture of any kind, no feature flags,
 * no person profiles or default person properties, no session replay, no surveys, no error autocapture.
 * Only the events [Analytics] hands over are ever sent.
 */
internal fun postHogHardenedConfig(apiKey: String, host: String): PostHogAndroidConfig =
    PostHogAndroidConfig(apiKey, host).apply {
        captureApplicationLifecycleEvents = false
        captureDeepLinks = false
        captureScreenViews = false
        capturePushNotificationSubscriptions = false
        capturePushNotificationOpened = false
        preloadFeatureFlags = false
        sendFeatureFlagEvent = false
        setDefaultPersonProperties = false
        personProfiles = PersonProfiles.NEVER
        sessionReplay = false
        surveys = false
        errorTrackingConfig.autoCapture = false
    }
