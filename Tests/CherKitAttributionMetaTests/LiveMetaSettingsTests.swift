#if canImport(FacebookCore) && os(iOS)
import XCTest
import FacebookCore
@testable import CherKitAttributionMeta

final class LiveMetaSettingsTests: XCTestCase {
    @MainActor func testRuntimePrivacyOverridesSavedSettingsWithoutInitializingMeta() {
        // Exercise the real pinned settings setters, not the fake's implementation of the flags.
        let keys = ["FacebookAutoLogAppEventsEnabled", "FacebookAdvertiserIDCollectionEnabled",
                    "FBSDKAutoLogMetaDataEnabled", "FBSDKAutoLogProductImprovementEnabled",
                    "FacebookCodelessDebugLogEnabled", "FacebookSKAdNetworkReportEnabled"]
        for key in keys { UserDefaults.standard.set(true, forKey: key) }
        let client = LiveMetaClient()
        client.disableAutomaticCollection()
        for key in keys { XCTAssertEqual(UserDefaults.standard.object(forKey: key) as? Bool, false) }
        // The auto-log getter returns true until CoreKit installs dependencies, regardless
        // of its pre-init backing value. The store is the startup authority in that window.
        XCTAssertFalse(Settings.shared.isAdvertiserIDCollectionEnabled)
        XCTAssertFalse(Settings.shared.isMetaDataCollectionEnabled)
        XCTAssertFalse(Settings.shared.isAutoLogProductImprovementEnabled)
        XCTAssertFalse(Settings.shared.isCodelessDebugLogEnabled)
        XCTAssertFalse(Settings.shared.isSKAdNetworkReportEnabled)
    }
}
#endif
