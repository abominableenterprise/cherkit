#if canImport(PostHog)
@testable import CherKitAnalytics
import PostHog
import XCTest

/// Checks the hardening on a real `PostHogConfig` (the hardened setup). Each flag is checked on the platforms
/// where posthog-ios 3.71.4 defines it.
final class PostHogHardenedConfigTests: XCTestCase {
    private let config = PostHogHardenedConfig.make(apiKey: "phc_test", host: "https://us.i.posthog.com")

    func testKeyAndHost() {
        XCTAssertEqual(config.projectToken, "phc_test")
        XCTAssertEqual(config.host.absoluteString, "https://us.i.posthog.com")
    }

    func testAutomaticCaptureIsOff() {
        XCTAssertFalse(config.captureApplicationLifecycleEvents)
        XCTAssertFalse(config.captureScreenViews)
        XCTAssertFalse(config.enableSwizzling)
        XCTAssertFalse(config.errorTrackingConfig.autoCapture)
        #if os(iOS) || targetEnvironment(macCatalyst)
        XCTAssertFalse(config.captureElementInteractions)
        #endif
        #if os(iOS) || os(macOS)
        XCTAssertFalse(config.capturePushNotificationSubscriptions)
        XCTAssertFalse(config.capturePushNotificationOpened)
        #endif
    }

    func testFeatureFlagsAndPersonDataAreOff() {
        XCTAssertFalse(config.preloadFeatureFlags)
        XCTAssertFalse(config.sendFeatureFlagEvent)
        XCTAssertFalse(config.setDefaultPersonProperties)
        XCTAssertEqual(config.personProfiles, .never)
    }

    func testReplayAndSurveysAreOff() {
        #if os(iOS)
        XCTAssertFalse(config.sessionReplay)
        XCTAssertFalse(config.surveys)
        #endif
    }

    func testEachCallBuildsAFreshConfig() {
        XCTAssertFalse(config === PostHogHardenedConfig.make(apiKey: "phc_test", host: "https://us.i.posthog.com"))
    }
}
#endif
