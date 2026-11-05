import XCTest
@testable import CherKitAttributionMeta

final class AttributionMetaModuleTests: XCTestCase {
    func testModuleIsRegistered() {
        XCTAssertTrue(AttributionMetaModule.self == AttributionMetaModule.self)
    }
}
