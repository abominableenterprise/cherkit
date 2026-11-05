import XCTest
@testable import CherKitAttributionRevenueCat

final class AttributionRevenueCatModuleTests: XCTestCase {
    func testModuleIsRegistered() {
        XCTAssertTrue(AttributionRevenueCatModule.self == AttributionRevenueCatModule.self)
    }
}
