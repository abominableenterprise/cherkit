import XCTest
import CherKitTestSupport
import CherKitAttributionRevenueCat
@testable import CherKitAttributionMeta

final class MetaStartupTests: XCTestCase {
    struct EnvironmentCase: Decodable {
        let name: String
        let audience: Audience
        let platform: String
        let isDebug: Bool
        let isTestHost: Bool
        let isSimulator: Bool
        let enableAppleAttribution: Bool
    }
    struct MetaEnvironmentCase: Decodable {
        let name: String
        let audience: Audience
        let platform: String
        let isDebug: Bool
        let isTestHost: Bool
        let isSimulator: Bool
        let hasMetaConfiguration: Bool
        let enableMetaAttribution: Bool
    }

    func testSameMetaGateFixturesAsAndroid() throws {
        let cases = try Fixtures.decode([MetaEnvironmentCase].self, module: "attribution", name: "meta-gates.json")
        XCTAssertEqual(cases.count, 50)
        for item in cases {
            let gate = MetaGate(audience: item.audience, platform: item.platform, isDebug: item.isDebug,
                                isTestHost: item.isTestHost, isSimulator: item.isSimulator,
                                configuration: item.hasMetaConfiguration ? Self.safePlist : [:])
            // Both platforms share the policy; Swift's live SDK is intentionally iOS-only.
            XCTAssertEqual(gate.allowsAttribution, item.enableMetaAttribution && item.platform == "iOS", item.name)
        }
    }

    struct ConfigCase: Decodable {
        let name: String
        let appID: String
        let clientToken: String
        let safeStartupFlags: Bool
        let enableMetaWithEligibleEnvironment: Bool
    }

    @MainActor func testSharedEnvironmentAndConfigurationGatesAndCallOrder() throws {
        let environments = try Fixtures.decode([EnvironmentCase].self, module: "attribution", name: "gates.json")
        let configurations = try Fixtures.decode([ConfigCase].self, module: "attribution", name: "meta-config.json")
        XCTAssertEqual(environments.count, 48)
        for environment in environments {
            for configuration in configurations {
                var plist = Self.safePlist
                plist["FacebookAppID"] = configuration.appID
                plist["FacebookClientToken"] = configuration.clientToken
                if !configuration.safeStartupFlags { plist[Self.flags[0]] = true }
                let gate = MetaGate(audience: environment.audience, platform: environment.platform,
                                    isDebug: environment.isDebug, isTestHost: environment.isTestHost,
                                    isSimulator: environment.isSimulator, configuration: plist)
                let expected = environment.enableAppleAttribution && configuration.enableMetaWithEligibleEnvironment
                let name = environment.name + "/" + configuration.name
                XCTAssertEqual(gate.allowsAttribution, expected, name)
                let fake = RecordingClients()
                let startup = MetaStartup(meta: fake, revenueCat: fake, observation: fake,
                                          eligible: { gate.allowsAttribution })
                startup.start()
                XCTAssertEqual(fake.calls, expected ? Self.startCalls : ["rc.start.deviceIDsOff"], name)
                startup.start()
                XCTAssertEqual(fake.calls, expected ? Self.startCalls : ["rc.start.deviceIDsOff"], name)
                XCTAssertEqual(fake.observerCount, expected ? 1 : 0, name)
            }
        }
    }

    @MainActor func testAllStartupFlagsMustBeExplicitFalseBooleans() {
        for key in Self.flags {
            for value in [nil, true, "false", 0] as [Any?] {
                var plist = Self.safePlist
                plist[key] = value
                XCTAssertFalse(Self.gate(plist).allowsAttribution, "\(key): \(String(describing: value))")
            }
        }
        for key in ["FacebookAppID", "FacebookClientToken"] {
            for value in [nil, 123, "", "\n "] as [Any?] {
                var plist = Self.safePlist
                plist[key] = value
                XCTAssertFalse(Self.gate(plist).allowsAttribution, key)
            }
        }
        XCTAssertTrue(Self.gate(Self.safePlist).allowsAttribution)
    }

    @MainActor func testForegroundAndIdentityRefreshWithoutDuplicateObservers() {
        let fake = RecordingClients()
        let startup = MetaStartup(meta: fake, revenueCat: fake, observation: fake, eligible: { true })
        startup.start()
        fake.calls = []
        fake.id = "actual-next-id"
        fake.foreground?()
        XCTAssertEqual(fake.calls, ["meta.readID", "rc.set:actual-next-id", "meta.activate"])
        fake.calls = []
        fake.identityChanged?()
        XCTAssertEqual(fake.calls, ["meta.readID", "rc.set:actual-next-id"])
        XCTAssertEqual(fake.observerCount, 1)
        startup.start()
        XCTAssertEqual(fake.observerCount, 1)
        let queuedForeground = fake.foreground
        let queuedIdentity = fake.identityChanged
        startup.stop()
        startup.stop()
        XCTAssertEqual(fake.observerCount, 0)
        XCTAssertEqual(fake.calls.last, "observe.stop")
        fake.calls = []
        queuedForeground?()
        queuedIdentity?()
        startup.start()
        XCTAssertTrue(fake.calls.isEmpty)
    }

    @MainActor func testIneligibleRefreshAndAudienceUpgradeStaySilent() {
        let fake = RecordingClients()
        var eligible = false
        let startup = MetaStartup(meta: fake, revenueCat: fake, observation: fake, eligible: { eligible })
        startup.start()
        eligible = true
        startup.start()
        XCTAssertEqual(fake.calls, ["rc.start.deviceIDsOff"])

        let active = RecordingClients()
        let other = MetaStartup(meta: active, revenueCat: active, observation: active, eligible: { eligible })
        other.start()
        eligible = false
        active.calls = []
        active.foreground?()
        active.identityChanged?()
        XCTAssertTrue(active.calls.isEmpty)
    }

    @MainActor func testExistingRevenueCatIsNotRetrofitted() {
        let fake = RecordingClients()
        fake.isConfigured = true
        let startup = MetaStartup(meta: fake, revenueCat: fake, observation: fake, eligible: { true })
        startup.start()
        XCTAssertEqual(fake.calls, ["rc.start.noop"])
    }

    @MainActor func testMissingAnonymousIDNeverInventsAnIDOrBlocksActivation() {
        let fake = RecordingClients()
        fake.id = "  "
        let startup = MetaStartup(meta: fake, revenueCat: fake, observation: fake, eligible: { true })
        startup.start()
        XCTAssertFalse(fake.calls.contains { $0.hasPrefix("rc.set:") })
        XCTAssertTrue(fake.calls.contains("meta.activate"))
    }

    static let flags = ["FacebookAutoLogAppEventsEnabled", "FacebookAdvertiserIDCollectionEnabled",
                        "FBSDKAutoLogMetaDataEnabled", "FBSDKAutoLogProductImprovementEnabled",
                        "FacebookCodelessDebugLogEnabled", "FacebookSKAdNetworkReportEnabled",
                        "FBSDKAemAutoSetupEnabled"]
    static var safePlist: [String: Any] {
        var value: [String: Any] = ["FacebookAppID": "123456789", "FacebookClientToken": "public-client-token"]
        for key in flags { value[key] = false }
        return value
    }
    static func gate(_ plist: [String: Any]) -> MetaGate {
        .init(audience: .adultOnly, platform: "iOS", isDebug: false, isTestHost: false,
              isSimulator: false, configuration: plist)
    }
    static let startCalls = ["rc.start.deviceIDsOff", "meta.autoEventsOff", "meta.advertiserIDsOff",
                             "meta.metadataOff", "meta.productImprovementOff", "meta.codelessOff",
                             "meta.skadOff", "meta.initialize", "meta.readID", "rc.set:actual-sdk-id",
                             "meta.activate", "observe.start"]
}

@MainActor private final class RecordingClients: MetaClient, MetaRevenueCatClient, MetaObservation {
    var calls: [String] = []
    var isConfigured = false
    var id = "actual-sdk-id"
    var observerCount = 0
    var foreground: (() -> Void)?
    var identityChanged: (() -> Void)?
    func startPurchases() {
        calls.append(isConfigured ? "rc.start.noop" : "rc.start.deviceIDsOff")
        isConfigured = true
    }
    func setAnonymousID(_ value: String) { calls.append("rc.set:" + value) }
    func disableAutomaticCollection() {
        calls += ["meta.autoEventsOff", "meta.advertiserIDsOff", "meta.metadataOff",
                  "meta.productImprovementOff", "meta.codelessOff", "meta.skadOff"]
    }
    func initialize() { calls.append("meta.initialize") }
    var anonymousID: String { calls.append("meta.readID"); return id }
    func activate() { calls.append("meta.activate") }
    func start(foreground: @escaping () -> Void, identityChanged: @escaping () -> Void) {
        calls.append("observe.start")
        observerCount += 1
        self.foreground = foreground
        self.identityChanged = identityChanged
    }
    func stop() {
        calls.append("observe.stop")
        observerCount -= 1
        foreground = nil
        identityChanged = nil
    }
}
