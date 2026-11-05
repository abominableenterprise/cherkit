import XCTest
import CherKitTestSupport
@testable import CherKitAttributionRevenueCat

final class RevenueCatStartupTests: XCTestCase {
    struct GateCase: Decodable {
        let name: String
        let platform: String
        let audience: Audience
        let isDebug: Bool
        let isTestHost: Bool
        let isSimulator: Bool
        let enableAppleAttribution: Bool

        var environment: AttributionEnvironment {
            .init(platform: platform, isDebug: isDebug, isTestHost: isTestHost, isSimulator: isSimulator)
        }
    }

    func testSharedGateAndSDKOrdering() throws {
        let cases = try Fixtures.decode([GateCase].self, module: "attribution", name: "gates.json")
        XCTAssertEqual(cases.count, 48)
        for item in cases {
            XCTAssertEqual(shouldEnableAppleAttribution(audience: item.audience, environment: item.environment),
                           item.enableAppleAttribution, item.name)
            let client = RecordingRevenueCatClient()
            RevenueCatStartup.start(client: client, audience: item.audience, environment: item.environment)
            XCTAssertEqual(client.calls, item.enableAppleAttribution ? ["configure", "apple"] : ["configure"],
                           item.name)
            RevenueCatStartup.start(client: client, audience: item.audience, environment: item.environment)
            XCTAssertEqual(client.calls.count, item.enableAppleAttribution ? 2 : 1, item.name)
        }
    }

    func testExistingSDKIsUntouchedEvenForEligibleLaunch() {
        let client = RecordingRevenueCatClient()
        client.isConfigured = true
        RevenueCatStartup.start(client: client, audience: .adultOnly,
                                environment: .init(platform: "iOS", isDebug: false,
                                                   isTestHost: false, isSimulator: false))
        XCTAssertTrue(client.calls.isEmpty)
    }

    func testAudienceCannotBeUpgradedAfterPurchasesOnlyStartup() {
        let client = RecordingRevenueCatClient()
        let environment = AttributionEnvironment(platform: "iOS", isDebug: false,
                                                 isTestHost: false, isSimulator: false)
        RevenueCatStartup.start(client: client, audience: .restrictedOrSwitchable, environment: environment)
        RevenueCatStartup.start(client: client, audience: .adultOnly, environment: environment)
        XCTAssertEqual(client.calls, ["configure"])
    }

    func testTestHostDetection() {
        for key in ["XCTestConfigurationFilePath", "XCTestBundlePath", "XCTestSessionIdentifier",
                    "SWIFT_TESTING_ENABLED"] {
            XCTAssertTrue(AttributionEnvironment.runningTests(environment: [key: ""], classNamed: { _ in nil }))
        }
        XCTAssertTrue(AttributionEnvironment.runningTests(environment: [:], classNamed: {
            $0 == "XCTestCase" ? NSObject.self : nil
        }))
        XCTAssertFalse(AttributionEnvironment.runningTests(environment: [:], classNamed: { _ in nil }))
        XCTAssertTrue(AttributionEnvironment.current.isTestHost)
    }
}

private final class RecordingRevenueCatClient: RevenueCatClient {
    var isConfigured = false
    var calls: [String] = []
    func configure() {
        calls.append("configure")
        isConfigured = true
    }
    func enableAppleAttribution() { calls.append("apple") }
}
