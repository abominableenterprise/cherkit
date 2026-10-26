#if canImport(PostHog)
@testable import CherKitAnalytics
import XCTest

@MainActor
final class PostHogAnalyticsBackendTests: XCTestCase {
    private enum Event: String, AnalyticsEvent {
        case appOpened = "app_opened"
        case itemAdded = "item_added"
    }

    private func makeBackend(apiKey: String = "phc_test", client: FakePostHogClient) -> PostHogAnalyticsBackend {
        PostHogAnalyticsBackend(apiKey: apiKey, host: "https://eu.i.posthog.com", client: client)
    }

    func testStartSetsUpWithTheKeyAndHostThenOptsIn() throws {
        let client = FakePostHogClient()
        try makeBackend(client: client).start()

        XCTAssertEqual(client.calls, ["setup", "optIn"])
        let config = try XCTUnwrap(client.configs.first)
        XCTAssertEqual(config.projectToken, "phc_test")
        XCTAssertEqual(config.host.absoluteString, "https://eu.i.posthog.com")
    }

    func testStopOptsOutThenCloses() throws {
        let client = FakePostHogClient()
        let backend = makeBackend(client: client)
        try backend.start()
        try backend.capture("app_opened")
        try backend.stop()

        XCTAssertEqual(client.calls, ["setup", "optIn", "capture:app_opened", "optOut", "close"])
    }

    func testStartWithoutStopDoesNotSetUpTwice() throws {
        let client = FakePostHogClient()
        let backend = makeBackend(client: client)
        try backend.start()
        try backend.start()

        XCTAssertEqual(client.calls, ["setup", "optIn", "optIn"])
    }

    func testRestartAfterStopSetsUpAgain() throws {
        let client = FakePostHogClient()
        let backend = makeBackend(client: client)
        try backend.start()
        try backend.stop()
        try backend.start()
        try backend.capture("item_added")

        XCTAssertEqual(client.calls, ["setup", "optIn", "optOut", "close", "setup", "optIn", "capture:item_added"])
        XCTAssertEqual(client.configs.count, 2)
        XCTAssertFalse(client.configs[0] === client.configs[1], "each setup gets a fresh config")
    }

    func testStopBeforeStartAndTwiceIsANoOp() throws {
        let client = FakePostHogClient()
        let backend = makeBackend(client: client)
        try backend.stop()
        try backend.start()
        try backend.stop()
        try backend.stop()

        XCTAssertEqual(client.calls, ["setup", "optIn", "optOut", "close"])
    }

    func testCaptureBeforeStartThrowsAndSendsNothing() {
        let client = FakePostHogClient()
        XCTAssertThrowsError(try makeBackend(client: client).capture("app_opened"))
        XCTAssertEqual(client.calls, [])
    }

    func testBlankKeyThrowsWithoutTouchingTheSDK() {
        for key in ["", "   ", "\n\t"] {
            let client = FakePostHogClient()
            XCTAssertThrowsError(try makeBackend(apiKey: key, client: client).start()) { error in
                XCTAssertEqual(error as? PostHogAnalyticsBackend.StartError, .blankAPIKey)
            }
            XCTAssertEqual(client.calls, [], "key \(key.debugDescription)")
        }
    }

    func testBlankKeyFailsSafelyThroughAnalytics() {
        let client = FakePostHogClient()
        let analytics = Analytics<Event>(backend: makeBackend(apiKey: " ", client: client), buildEnabled: true)
        analytics.consentGranted = true
        analytics.sessionActive = true

        XCTAssertFalse(analytics.track(.appOpened))
        analytics.sessionActive = false
        XCTAssertEqual(client.calls, [])
    }

    func testThroughAnalyticsOnlyEventNamesAreSentAcrossGateCycles() {
        let client = FakePostHogClient()
        let analytics = Analytics<Event>(backend: makeBackend(client: client), buildEnabled: true)
        analytics.consentGranted = true
        XCTAssertFalse(analytics.track(.appOpened), "session not active yet")

        analytics.sessionActive = true
        XCTAssertTrue(analytics.track(.appOpened))
        analytics.consentGranted = false
        XCTAssertFalse(analytics.track(.itemAdded))
        analytics.consentGranted = true
        XCTAssertTrue(analytics.track(.itemAdded))

        // Every call is one of the five allowed ones, and captures carry only the event name.
        XCTAssertEqual(client.calls, [
            "setup", "optIn", "capture:app_opened",
            "optOut", "close",
            "setup", "optIn", "capture:item_added",
        ])
    }
}
#endif
