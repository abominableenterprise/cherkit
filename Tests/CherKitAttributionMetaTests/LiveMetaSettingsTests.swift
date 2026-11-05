#if canImport(FacebookCore) && os(iOS)
import XCTest
import FacebookCore
@testable import CherKitAttributionMeta

final class LiveMetaSettingsTests: XCTestCase {
    @MainActor func testRuntimePrivacyOverridesSavedSettingsWithoutInitializingMeta() {
        // Exercise the real pinned settings setters, not the fake's implementation of the flags.
        let client = LiveMetaClient()
        client.disableAutomaticCollection()
        XCTAssertFalse(Settings.shared.isAutoLogAppEventsEnabled)
        XCTAssertFalse(Settings.shared.isAdvertiserIDCollectionEnabled)
        XCTAssertFalse(Settings.shared.isMetaDataCollectionEnabled)
        XCTAssertFalse(Settings.shared.isAutoLogProductImprovementEnabled)
        XCTAssertFalse(Settings.shared.isCodelessDebugLogEnabled)
        XCTAssertFalse(Settings.shared.isSKAdNetworkReportEnabled)
    }
}
#endif
