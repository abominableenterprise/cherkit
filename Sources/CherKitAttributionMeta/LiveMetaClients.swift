#if canImport(FacebookCore) && os(iOS)
import Foundation
import UIKit
import FacebookCore
import RevenueCat
import CherKitAttributionRevenueCat

@MainActor final class LiveMetaClient: MetaClient {
    func disableAutomaticCollection() {
        Settings.shared.isAutoLogAppEventsEnabled = false
        Settings.shared.isAdvertiserIDCollectionEnabled = false
        Settings.shared.isMetaDataCollectionEnabled = false
        Settings.shared.isAutoLogProductImprovementEnabled = false
        Settings.shared.isCodelessDebugLogEnabled = false
        Settings.shared.isSKAdNetworkReportEnabled = false
    }
    func initialize() {
        _ = ApplicationDelegate.shared.application(UIApplication.shared, didFinishLaunchingWithOptions: nil)
    }
    var anonymousID: String { AppEvents.shared.anonymousID }
    func activate() { AppEvents.shared.activateApp() }
}

@MainActor final class LiveMetaRevenueCatClient: MetaRevenueCatClient {
    let setup: RevenueCatSetup
    let audience: Audience
    init(setup: RevenueCatSetup, audience: Audience) {
        self.setup = setup
        self.audience = audience
    }
    var isConfigured: Bool { Purchases.isConfigured }
    func startPurchases() { RevenueCatAttribution.start(revenueCat: setup, audience: audience) }
    func setAnonymousID(_ value: String) { Purchases.shared.attribution.setFBAnonymousID(value) }
}

/// Does not replace the app's Purchases delegate. Owns exactly one foreground token and one stream task.
@MainActor final class LiveMetaObservation: MetaObservation {
    private var foregroundToken: NSObjectProtocol?
    private var customerTask: Task<Void, Never>?

    func start(foreground: @escaping () -> Void, identityChanged: @escaping () -> Void) {
        guard foregroundToken == nil else { return }
        foregroundToken = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
        ) { _ in
            // Delivery is synchronous on the main queue: assign the ID before activation.
            MainActor.assumeIsolated { foreground() }
        }
        // Create the stream synchronously so an immediate login cannot precede subscription.
        let stream = Purchases.shared.customerInfoStream
        customerTask = Task { @MainActor in
            for await _ in stream {
                guard !Task.isCancelled else { break }
                identityChanged()
            }
        }
    }

    func stop() {
        if let token = foregroundToken { NotificationCenter.default.removeObserver(token) }
        foregroundToken = nil
        customerTask?.cancel()
        customerTask = nil
    }
}

extension MetaGate {
    static func current(audience: Audience) -> Self {
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        #if targetEnvironment(simulator)
        let isSimulator = true
        #else
        let isSimulator = false
        #endif
        let environment = ProcessInfo.processInfo.environment
        let keys = ["XCTestConfigurationFilePath", "XCTestBundlePath", "XCTestSessionIdentifier",
                    "SWIFT_TESTING_ENABLED"]
        let isTestHost = keys.contains { environment[$0] != nil }
            || NSClassFromString("XCTestCase") != nil || NSClassFromString("XCTest.XCTestCase") != nil
        return Self(audience: audience, platform: "iOS", isDebug: isDebug,
                    isTestHost: isTestHost, isSimulator: isSimulator,
                    configuration: Bundle.main.infoDictionary ?? [:])
    }
}
#endif
