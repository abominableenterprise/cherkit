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
        let fields = Dictionary(uniqueKeysWithValues: Mirror(reflecting: configuration).children.compactMap {
            child -> (String, Any)? in child.label.map { ($0, child.value) }
        })
        XCTAssertEqual(optionCalls, 1)
        XCTAssertEqual(fields["apiKey"] as? String, "public-key")
        XCTAssertEqual(fields["appUserID"] as? String, "existing-user")
        XCTAssertEqual(fields["observerMode"] as? Bool, true)
        XCTAssertTrue((fields["userDefaults"] as? UserDefaults) === defaults)
        XCTAssertEqual(fields["networkTimeout"] as? TimeInterval, 42)
        XCTAssertEqual(fields["automaticDeviceIdentifierCollectionEnabled"] as? Bool, false)
        XCTAssertEqual(fields["storeKitVersion"] as? StoreKitVersion, .storeKit2)
        XCTAssertNotNil(fields["responseVerificationMode"])
    }
}
#endif
