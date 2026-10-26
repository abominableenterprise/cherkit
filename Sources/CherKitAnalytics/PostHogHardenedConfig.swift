#if canImport(PostHog)
import PostHog

/// Builds the PostHog config the adapter uses: the hardened setup the apps use, with every automatic capture,
/// person-profile, feature-flag, replay and survey feature off, so only the events `Analytics` sends leave the app.
enum PostHogHardenedConfig {
    static func make(apiKey: String, host: String) -> PostHogConfig {
        let config = PostHogConfig(projectToken: apiKey, host: host)
        config.captureApplicationLifecycleEvents = false
        config.captureScreenViews = false
        config.enableSwizzling = false
        #if os(iOS) || targetEnvironment(macCatalyst)
        config.captureElementInteractions = false
        #endif
        #if os(iOS) || os(macOS)
        config.capturePushNotificationSubscriptions = false
        config.capturePushNotificationOpened = false
        #endif
        config.preloadFeatureFlags = false
        config.sendFeatureFlagEvent = false
        config.setDefaultPersonProperties = false
        config.personProfiles = .never
        #if os(iOS)
        config.sessionReplay = false
        config.surveys = false
        #endif
        config.errorTrackingConfig.autoCapture = false
        return config
    }
}
#endif
