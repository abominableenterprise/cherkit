import CherKitSunset
import CherKitTestSupport
import Foundation
import XCTest

/// Runs every case in `fixtures/sunset/store-url.json`; Kotlin's `SunsetStoreUrlFixtureTest` runs the same cases.
final class SunsetStoreURLFixtureTests: XCTestCase {
    private struct StoreURLFixture: Decodable {
        let cases: [StoreURLCase]
    }

    private struct StoreURLCase: Decodable {
        let url: String
        let valid: Bool
    }

    func testStoreURLCases() throws {
        let cases = try Fixtures.decode(StoreURLFixture.self, module: "sunset", name: "store-url.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let body = try JSONSerialization.data(withJSONObject: [
                "schemaVersion": 1,
                "ios": ["latestVersion": "2.0", "storeURL": testCase.url],
            ])
            let block = SunsetConfig.parse(body)?.ios
            XCTAssertEqual(block != nil, testCase.valid, testCase.url.debugDescription)
            if let block {
                XCTAssertEqual(block.storeURL.absoluteString, testCase.url, testCase.url.debugDescription)
            }
        }
    }
}
