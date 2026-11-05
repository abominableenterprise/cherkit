#if canImport(RevenueCat)
import XCTest
import RevenueCat
@testable import CherKitAttributionRevenueCat

final class RevenueCatSetupTests: XCTestCase {
    func testNativeOptionsPassThroughAndPrivacyOverrideWins() {
        let defaults = UserDefaults(suiteName: "cherkit-attribution-tests")!
        var optionCalls = 0
        let setup = RevenueCatSetup(apiKey: "public-key", appUserID: "existing-user") { builder in
            optionCalls += 1
            _ = builder.with(userDefaults: defaults)
                .with(purchasesAreCompletedBy: .myApp, storeKitVersion: .storeKit2)
                .with(entitlementVerificationMode: .informational)
                .with(networkTimeout: 42)
                .with(automaticDeviceIdentifierCollectionEnabled: true)
        }
        // No SDK startup or network: inspect the native configuration that would be passed to configure.
        let configuration = setup.configuration()
        let expected = Configuration.Builder(withAPIKey: "public-key")
            .with(appUserID: "existing-user")
            .with(userDefaults: defaults)
            .with(purchasesAreCompletedBy: .myApp, storeKitVersion: .storeKit2)
            .with(entitlementVerificationMode: .informational)
            .with(networkTimeout: 42)
            .with(automaticDeviceIdentifierCollectionEnabled: false)
        XCTAssertEqual(optionCalls, 1)
        // RevenueCat's configuration equality includes identity, suite, completion mode,
        // verification, timeouts, StoreKit and privacy settings; no private-field reflection.
        XCTAssertEqual(configuration, expected.build())
        XCTAssertNotEqual(configuration, expected.with(automaticDeviceIdentifierCollectionEnabled: true).build())
    }
}
#endif
